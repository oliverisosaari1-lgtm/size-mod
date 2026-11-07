package main

import (
	"crypto/rand"
	"embed"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io/fs"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"sync"
	"time"
)

const appVersion = "1.0.0"

//go:embed ui
var uiFiles embed.FS

func randHex(n int) string {
	b := make([]byte, n)
	rand.Read(b)
	return hex.EncodeToString(b)
}

// ---------- folders ----------

func appData() string {
	if d := os.Getenv("OLKKU_CLIENT_DIR"); d != "" {
		return filepath.Dir(d)
	}
	if runtime.GOOS == "windows" {
		if d := os.Getenv("APPDATA"); d != "" {
			return d
		}
	}
	d, err := os.UserConfigDir()
	if err != nil {
		d = os.TempDir()
	}
	return d
}

func baseDir() string {
	if d := os.Getenv("OLKKU_CLIENT_DIR"); d != "" {
		return d
	}
	return filepath.Join(appData(), ".olkkuclient")
}

func minecraftDir() string {
	if runtime.GOOS == "windows" {
		return filepath.Join(appData(), ".minecraft")
	}
	h, _ := os.UserHomeDir()
	return filepath.Join(h, ".minecraft")
}

func profileDir(p *profile) string {
	if p.UseMinecraft {
		return minecraftDir()
	}
	return filepath.Join(baseDir(), "instances", safeName(p.Name))
}

func safeName(s string) string {
	var b strings.Builder
	for _, r := range s {
		if strings.ContainsRune(`<>:"/\|?*`, r) || r < 32 {
			b.WriteRune('_')
		} else {
			b.WriteRune(r)
		}
	}
	out := strings.TrimSpace(strings.Trim(b.String(), ". "))
	if out == "" {
		out = "profile"
	}
	return out
}

// ---------- saved settings ----------

type profile struct {
	Name          string `json:"name"`
	Version       string `json:"version"`
	Loader        string `json:"loader"`
	LoaderVersion string `json:"loaderVersion"`
	RAM           int    `json:"ram"`
	Boost         bool   `json:"boost"`
	OlkkuMod      bool   `json:"olkkuMod"`
	UseMinecraft  bool   `json:"useMinecraft"`
	LastPlayed    int64  `json:"lastPlayed"`
}

type config struct {
	Profiles      []profile `json:"profiles"`
	Accounts      []account `json:"accounts"`
	Account       string    `json:"account"`
	Selected      string    `json:"selected"`
	ClientID      string    `json:"clientId"`
	JavaPath      string    `json:"javaPath"`
	JVMArgs       string    `json:"jvmArgs"`
	CloseOnLaunch bool      `json:"closeOnLaunch"`
	Snapshots     bool      `json:"snapshots"`
}

var cfgMu sync.Mutex

func cfgFile() string { return filepath.Join(baseDir(), "olkkus-client.json") }

func loadConfig() config {
	cfgMu.Lock()
	defer cfgMu.Unlock()
	var c config
	if b, err := os.ReadFile(cfgFile()); err == nil {
		json.Unmarshal(b, &c)
	}
	if len(c.Profiles) == 0 {
		c.Profiles = []profile{{Name: "Olkku's Client 26.3", Version: "26.3", Loader: "fabric", RAM: 4096, Boost: true, OlkkuMod: true}}
		c.Selected = c.Profiles[0].Name
	}
	return c
}

func saveConfig(c config) error {
	cfgMu.Lock()
	defer cfgMu.Unlock()
	os.MkdirAll(baseDir(), 0o755)
	b, _ := json.MarshalIndent(c, "", "  ")
	return os.WriteFile(cfgFile(), b, 0o600)
}

func findProfile(c *config, name string) *profile {
	for i := range c.Profiles {
		if c.Profiles[i].Name == name {
			return &c.Profiles[i]
		}
	}
	return nil
}

func addAccount(a account) {
	c := loadConfig()
	for i := range c.Accounts {
		if c.Accounts[i].ID == a.ID {
			c.Accounts[i] = a
			c.Account = a.ID
			saveConfig(c)
			return
		}
	}
	c.Accounts = append(c.Accounts, a)
	c.Account = a.ID
	saveConfig(c)
}

func currentAccount(c config) (account, bool) {
	for _, a := range c.Accounts {
		if a.ID == c.Account {
			return a, true
		}
	}
	if len(c.Accounts) > 0 {
		return c.Accounts[0], true
	}
	return account{}, false
}

// ---------- the one task running (installing / starting a game) ----------

type task struct {
	mu       sync.Mutex
	Running  bool     `json:"running"`
	Step     string   `json:"step"`
	Done     int      `json:"done"`
	Total    int      `json:"total"`
	Log      []string `json:"log"`
	Error    string   `json:"error"`
	crashLog string
}

func (t *task) set(step string, done, total int) {
	t.mu.Lock()
	t.Step, t.Done, t.Total = step, done, total
	t.mu.Unlock()
}

func (t *task) logf(f string, a ...any) {
	t.mu.Lock()
	t.Log = append(t.Log, time.Now().Format("15:04:05")+"  "+fmt.Sprintf(f, a...))
	if len(t.Log) > 200 {
		t.Log = t.Log[len(t.Log)-200:]
	}
	t.mu.Unlock()
}

var cur = &task{}

func play(name string) error {
	cur.mu.Lock()
	if cur.Running {
		cur.mu.Unlock()
		return fmt.Errorf("already busy, wait a moment")
	}
	cur.Running, cur.Error, cur.Step, cur.Done, cur.Total = true, "", "Starting", 0, 0
	cur.mu.Unlock()
	go func() {
		err := func() error {
			c := loadConfig()
			p := findProfile(&c, name)
			if p == nil {
				return fmt.Errorf("profile not found")
			}
			acc, ok := currentAccount(c)
			if !ok {
				return fmt.Errorf("add an account first (Accounts)")
			}
			acc, err := refreshAccount(acc)
			if err != nil {
				return err
			}
			cur.logf("Getting %s %s ready...", loaderNames[orVanilla(p.Loader)], p.Version)
			setupMods(p, cur)
			li, err := prepare(p, acc, cur)
			if err != nil {
				return err
			}
			cur.set("Starting Minecraft", 1, 1)
			if err := startGame(p, li, cur); err != nil {
				return err
			}
			p.LastPlayed = time.Now().Unix()
			c.Selected = p.Name
			saveConfig(c)
			cur.logf("Minecraft is starting as %s. Have fun!", acc.Name)
			if c.CloseOnLaunch {
				go func() { time.Sleep(3 * time.Second); os.Exit(0) }()
			}
			return nil
		}()
		cur.mu.Lock()
		cur.Running = false
		cur.Step = ""
		if err != nil {
			cur.Error = err.Error()
			cur.Log = append(cur.Log, time.Now().Format("15:04:05")+"  Problem: "+err.Error())
		}
		cur.mu.Unlock()
	}()
	return nil
}

func orVanilla(l string) string {
	if l == "" {
		return "vanilla"
	}
	return l
}

// ---------- HTTP API for the window ----------

func writeJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v)
}

func writeErr(w http.ResponseWriter, err error) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(400)
	json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
}

func readBody(r *http.Request, v any) error {
	return json.NewDecoder(http.MaxBytesReader(nil, r.Body, 1<<20)).Decode(v)
}

func publicState() map[string]any {
	c := loadConfig()
	accs := []map[string]any{}
	for _, a := range c.Accounts {
		accs = append(accs, map[string]any{"id": a.ID, "type": a.Type, "name": a.Name, "uuid": a.UUID})
	}
	sel, _ := currentAccount(c)
	ps := c.Profiles
	sort.SliceStable(ps, func(i, j int) bool { return ps[i].LastPlayed > ps[j].LastPlayed })
	running := []string{}
	gamesMu.Lock()
	for n := range games {
		running = append(running, n)
	}
	gamesMu.Unlock()
	return map[string]any{
		"profiles": ps, "selected": c.Selected, "accounts": accs, "account": sel.ID,
		"settings": map[string]any{"clientId": c.ClientID, "javaPath": c.JavaPath, "jvmArgs": c.JVMArgs,
			"closeOnLaunch": c.CloseOnLaunch, "snapshots": c.Snapshots},
		"running": running, "folder": baseDir(), "version": appVersion,
	}
}

func newServer() (addr, token string, err error) {
	token = randHex(16)
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return "", "", err
	}
	mux := http.NewServeMux()
	sub, _ := fs.Sub(uiFiles, "ui")
	files := http.FileServer(http.FS(sub))
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/" && r.URL.Query().Get("t") != token {
			http.Error(w, "no", 403)
			return
		}
		w.Header().Set("Cache-Control", "no-store")
		files.ServeHTTP(w, r)
	})
	api := func(path string, h func(w http.ResponseWriter, r *http.Request)) {
		mux.HandleFunc(path, func(w http.ResponseWriter, r *http.Request) {
			if r.Header.Get("X-Token") != token {
				http.Error(w, "no", 403)
				return
			}
			h(w, r)
		})
	}

	api("/api/state", func(w http.ResponseWriter, r *http.Request) { writeJSON(w, publicState()) })

	api("/api/versions", func(w http.ResponseWriter, r *http.Request) {
		m, err := getManifest()
		if err != nil {
			writeErr(w, err)
			return
		}
		writeJSON(w, m)
	})

	api("/api/loaders", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, loadersFor(r.URL.Query().Get("mc")))
	})

	api("/api/profile/save", func(w http.ResponseWriter, r *http.Request) {
		var in struct {
			Old     string  `json:"old"`
			Profile profile `json:"profile"`
		}
		if err := readBody(r, &in); err != nil {
			writeErr(w, err)
			return
		}
		p := in.Profile
		p.Name = strings.TrimSpace(p.Name)
		if p.Name == "" || p.Version == "" {
			writeErr(w, fmt.Errorf("give the profile a name and a version"))
			return
		}
		c := loadConfig()
		if in.Old != in.Profile.Name && findProfile(&c, p.Name) != nil {
			writeErr(w, fmt.Errorf("a profile called %s already exists", p.Name))
			return
		}
		if old := findProfile(&c, in.Old); old != nil && in.Old != "" {
			if old.Version != p.Version || old.Loader != p.Loader {
				p.LoaderVersion = ""
			}
			p.LastPlayed = old.LastPlayed
			*old = p
		} else {
			c.Profiles = append(c.Profiles, p)
		}
		c.Selected = p.Name
		saveConfig(c)
		writeJSON(w, publicState())
	})

	api("/api/profile/delete", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ Name string }
		readBody(r, &in)
		c := loadConfig()
		var keep []profile
		for _, p := range c.Profiles {
			if p.Name != in.Name {
				keep = append(keep, p)
			}
		}
		c.Profiles = keep
		saveConfig(c)
		writeJSON(w, publicState())
	})

	api("/api/select", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ Profile, Account string }
		readBody(r, &in)
		c := loadConfig()
		if in.Profile != "" {
			c.Selected = in.Profile
		}
		if in.Account != "" {
			c.Account = in.Account
		}
		saveConfig(c)
		writeJSON(w, publicState())
	})

	api("/api/play", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ Name string }
		readBody(r, &in)
		if err := play(in.Name); err != nil {
			writeErr(w, err)
			return
		}
		writeJSON(w, map[string]bool{"ok": true})
	})

	api("/api/task", func(w http.ResponseWriter, r *http.Request) {
		cur.mu.Lock()
		defer cur.mu.Unlock()
		writeJSON(w, map[string]any{"running": cur.Running, "step": cur.Step, "done": cur.Done, "total": cur.Total,
			"log": cur.Log, "error": cur.Error, "crash": cur.crashLog})
	})

	modProfile := func(name string) (*profile, error) {
		c := loadConfig()
		p := findProfile(&c, name)
		if p == nil {
			return nil, fmt.Errorf("pick a profile")
		}
		cp := *p
		return &cp, nil
	}

	api("/api/mods/search", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ Profile, Query string }
		readBody(r, &in)
		p, err := modProfile(in.Profile)
		if err == nil {
			var hits []modHit
			if hits, err = searchMods(p, in.Query); err == nil {
				writeJSON(w, hits)
				return
			}
		}
		writeErr(w, err)
	})

	api("/api/mods/install", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ Profile, Project string }
		readBody(r, &in)
		p, err := modProfile(in.Profile)
		if err == nil {
			var f string
			if f, err = installMod(p, in.Project, 0); err == nil {
				writeJSON(w, map[string]string{"file": f})
				return
			}
		}
		writeErr(w, err)
	})

	api("/api/mods/list", func(w http.ResponseWriter, r *http.Request) {
		p, err := modProfile(r.URL.Query().Get("profile"))
		if err != nil {
			writeErr(w, err)
			return
		}
		writeJSON(w, listMods(p))
	})

	api("/api/mods/change", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ Profile, File, Action string }
		readBody(r, &in)
		p, err := modProfile(in.Profile)
		if err != nil {
			writeErr(w, err)
			return
		}
		path, err := safeModPath(p, in.File)
		if err != nil {
			writeErr(w, err)
			return
		}
		switch in.Action {
		case "remove":
			err = os.Remove(path)
		case "toggle":
			if strings.HasSuffix(path, ".disabled") {
				err = os.Rename(path, strings.TrimSuffix(path, ".disabled"))
			} else {
				err = os.Rename(path, path+".disabled")
			}
		}
		if err != nil {
			writeErr(w, err)
			return
		}
		writeJSON(w, listMods(p))
	})

	api("/api/account/offline", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ Name string }
		readBody(r, &in)
		if !validName(in.Name) {
			writeErr(w, fmt.Errorf("names are 3-16 letters, numbers or _"))
			return
		}
		addAccount(account{ID: "off-" + strings.ToLower(in.Name), Type: "offline", Name: in.Name, UUID: offlineUUID(in.Name)})
		writeJSON(w, publicState())
	})

	api("/api/account/remove", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ ID string }
		readBody(r, &in)
		c := loadConfig()
		var keep []account
		for _, a := range c.Accounts {
			if a.ID != in.ID {
				keep = append(keep, a)
			}
		}
		c.Accounts = keep
		saveConfig(c)
		writeJSON(w, publicState())
	})

	api("/api/account/ms/start", func(w http.ResponseWriter, r *http.Request) {
		l, err := msStart(loadConfig().ClientID)
		if err != nil {
			writeErr(w, err)
			return
		}
		openBrowser(l.Link)
		writeJSON(w, l)
	})

	api("/api/account/ms/poll", func(w http.ResponseWriter, r *http.Request) {
		msMu.Lock()
		defer msMu.Unlock()
		if msNow == nil {
			writeJSON(w, map[string]string{"status": "none"})
			return
		}
		writeJSON(w, msNow)
	})

	api("/api/settings", func(w http.ResponseWriter, r *http.Request) {
		var in struct {
			ClientID      string `json:"clientId"`
			JavaPath      string `json:"javaPath"`
			JVMArgs       string `json:"jvmArgs"`
			CloseOnLaunch bool   `json:"closeOnLaunch"`
			Snapshots     bool   `json:"snapshots"`
		}
		if err := readBody(r, &in); err != nil {
			writeErr(w, err)
			return
		}
		c := loadConfig()
		c.ClientID, c.JavaPath, c.JVMArgs = strings.TrimSpace(in.ClientID), strings.TrimSpace(in.JavaPath), in.JVMArgs
		c.CloseOnLaunch, c.Snapshots = in.CloseOnLaunch, in.Snapshots
		saveConfig(c)
		writeJSON(w, publicState())
	})

	api("/api/open", func(w http.ResponseWriter, r *http.Request) {
		var in struct{ Profile, What string }
		readBody(r, &in)
		dir := baseDir()
		if p, err := modProfile(in.Profile); err == nil {
			dir = profileDir(p)
			if in.What == "mods" {
				dir = modsDir(p)
			}
		}
		if strings.HasPrefix(in.What, "https://") {
			openBrowser(in.What)
		} else {
			os.MkdirAll(dir, 0o755)
			openFolder(dir)
		}
		writeJSON(w, map[string]bool{"ok": true})
	})

	go http.Serve(ln, mux)
	return ln.Addr().String(), token, nil
}
