package main

// Accounts: offline names and Microsoft sign-in (device code, so we never see a password).

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

type account struct {
	ID        string `json:"id"`
	Type      string `json:"type"` // "offline" or "microsoft"
	Name      string `json:"name"`
	UUID      string `json:"uuid"`
	Token     string `json:"token,omitempty"`
	Refresh   string `json:"refresh,omitempty"`
	ExpiresAt int64  `json:"expiresAt,omitempty"`
}

func postForm(u string, form url.Values, out any) (int, error) {
	resp, err := httpClient.PostForm(u, form)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(resp.Body)
	json.Unmarshal(b, out)
	return resp.StatusCode, nil
}

func postJSON(u string, in any, out any, bearer string) (int, error) {
	b, _ := json.Marshal(in)
	req, _ := http.NewRequest("POST", u, bytes.NewReader(b))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	if bearer != "" {
		req.Header.Set("Authorization", "Bearer "+bearer)
	}
	resp, err := httpClient.Do(req)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()
	rb, _ := io.ReadAll(resp.Body)
	json.Unmarshal(rb, out)
	return resp.StatusCode, nil
}

// ---- sign-in state shown in the app ----

type msLogin struct {
	Code    string `json:"code"`
	Link    string `json:"link"`
	Status  string `json:"status"` // waiting, done, error
	Message string `json:"message"`
}

var (
	msMu  sync.Mutex
	msNow *msLogin
)

func msStart(clientID string) (*msLogin, error) {
	if clientID == "" {
		return nil, fmt.Errorf("add your Microsoft app ID in Settings first")
	}
	var dc struct {
		UserCode        string `json:"user_code"`
		DeviceCode      string `json:"device_code"`
		VerificationURI string `json:"verification_uri"`
		Interval        int    `json:"interval"`
		ExpiresIn       int    `json:"expires_in"`
		Error           string `json:"error_description"`
	}
	_, err := postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
		url.Values{"client_id": {clientID}, "scope": {"XboxLive.signin offline_access"}}, &dc)
	if err != nil {
		return nil, err
	}
	if dc.DeviceCode == "" {
		return nil, fmt.Errorf("Microsoft said: %s", dc.Error)
	}
	l := &msLogin{Code: dc.UserCode, Link: dc.VerificationURI, Status: "waiting", Message: "Type the code on the Microsoft page"}
	msMu.Lock()
	msNow = l
	msMu.Unlock()
	go func() {
		interval := time.Duration(max(dc.Interval, 3)) * time.Second
		deadline := time.Now().Add(time.Duration(max(dc.ExpiresIn, 300)) * time.Second)
		for time.Now().Before(deadline) {
			time.Sleep(interval)
			var tok struct {
				AccessToken  string `json:"access_token"`
				RefreshToken string `json:"refresh_token"`
				Error        string `json:"error"`
			}
			postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", url.Values{
				"grant_type": {"urn:ietf:params:oauth:grant-type:device_code"}, "client_id": {clientID}, "device_code": {dc.DeviceCode},
			}, &tok)
			if tok.Error == "authorization_pending" || tok.Error == "slow_down" {
				continue
			}
			msMu.Lock()
			if tok.AccessToken == "" {
				l.Status, l.Message = "error", "Sign-in was cancelled or timed out"
				msMu.Unlock()
				return
			}
			l.Message = "Signing in to Minecraft..."
			msMu.Unlock()
			acc, err := minecraftLogin(tok.AccessToken)
			msMu.Lock()
			if err != nil {
				l.Status, l.Message = "error", err.Error()
			} else {
				acc.Refresh = tok.RefreshToken
				addAccount(acc)
				l.Status, l.Message = "done", "Signed in as "+acc.Name
			}
			msMu.Unlock()
			return
		}
		msMu.Lock()
		l.Status, l.Message = "error", "The code ran out of time. Try again."
		msMu.Unlock()
	}()
	return l, nil
}

// minecraftLogin turns a Microsoft token into a Minecraft account.
func minecraftLogin(msToken string) (account, error) {
	var xbl struct {
		Token         string `json:"Token"`
		DisplayClaims struct {
			Xui []struct {
				Uhs string `json:"uhs"`
			} `json:"xui"`
		} `json:"DisplayClaims"`
	}
	postJSON("https://user.auth.xboxlive.com/user/authenticate", map[string]any{
		"Properties":   map[string]any{"AuthMethod": "RPS", "SiteName": "user.auth.xboxlive.com", "RpsTicket": "d=" + msToken},
		"RelyingParty": "http://auth.xboxlive.com", "TokenType": "JWT",
	}, &xbl, "")
	if xbl.Token == "" || len(xbl.DisplayClaims.Xui) == 0 {
		return account{}, fmt.Errorf("Xbox sign-in failed")
	}
	var xsts struct {
		Token string `json:"Token"`
		XErr  int64  `json:"XErr"`
	}
	postJSON("https://xsts.auth.xboxlive.com/xsts/authorize", map[string]any{
		"Properties":   map[string]any{"SandboxId": "RETAIL", "UserTokens": []string{xbl.Token}},
		"RelyingParty": "rp://api.minecraftservices.com/", "TokenType": "JWT",
	}, &xsts, "")
	if xsts.Token == "" {
		if xsts.XErr == 2148916233 {
			return account{}, fmt.Errorf("this Microsoft account has no Xbox profile yet")
		}
		if xsts.XErr == 2148916238 {
			return account{}, fmt.Errorf("this is a child account; an adult needs to add it to a Microsoft family")
		}
		return account{}, fmt.Errorf("Xbox security check failed")
	}
	var mc struct {
		AccessToken string `json:"access_token"`
		ExpiresIn   int64  `json:"expires_in"`
	}
	code, _ := postJSON("https://api.minecraftservices.com/authentication/login_with_xbox",
		map[string]any{"identityToken": "XBL3.0 x=" + xbl.DisplayClaims.Xui[0].Uhs + ";" + xsts.Token}, &mc, "")
	if mc.AccessToken == "" {
		if code == 403 {
			return account{}, fmt.Errorf("Minecraft blocked this app ID. Mojang must approve the app ID first (see Settings)")
		}
		return account{}, fmt.Errorf("Minecraft sign-in failed (%d)", code)
	}
	req, _ := http.NewRequest("GET", "https://api.minecraftservices.com/minecraft/profile", nil)
	req.Header.Set("Authorization", "Bearer "+mc.AccessToken)
	resp, err := httpClient.Do(req)
	if err != nil {
		return account{}, err
	}
	defer resp.Body.Close()
	var prof struct {
		ID   string `json:"id"`
		Name string `json:"name"`
	}
	json.NewDecoder(resp.Body).Decode(&prof)
	if prof.ID == "" {
		return account{}, fmt.Errorf("this Microsoft account doesn't own Minecraft")
	}
	return account{ID: "ms-" + prof.ID, Type: "microsoft", Name: prof.Name, UUID: prof.ID, Token: mc.AccessToken,
		ExpiresAt: time.Now().Unix() + mc.ExpiresIn}, nil
}

// refreshAccount gets a fresh token before playing if the old one ran out.
func refreshAccount(a account) (account, error) {
	if a.Type != "microsoft" || time.Now().Unix() < a.ExpiresAt-300 {
		return a, nil
	}
	cid := loadConfig().ClientID
	var tok struct {
		AccessToken  string `json:"access_token"`
		RefreshToken string `json:"refresh_token"`
	}
	postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", url.Values{
		"grant_type": {"refresh_token"}, "client_id": {cid}, "refresh_token": {a.Refresh},
		"scope": {"XboxLive.signin offline_access"},
	}, &tok)
	if tok.AccessToken == "" {
		return a, fmt.Errorf("your Microsoft sign-in ran out. Sign in again in Accounts")
	}
	n, err := minecraftLogin(tok.AccessToken)
	if err != nil {
		return a, err
	}
	n.Refresh = tok.RefreshToken
	addAccount(n)
	return n, nil
}

func validName(n string) bool {
	if len(n) < 3 || len(n) > 16 {
		return false
	}
	return strings.Trim(n, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_") == ""
}
