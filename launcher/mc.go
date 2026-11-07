package main

// Downloading and starting Minecraft: versions, libraries, assets, Java.

import (
	"archive/zip"
	"crypto/md5"
	"crypto/sha1"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"sync"
	"time"
)

const manifestURL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
const javaManifestURL = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"

var httpClient = &http.Client{Timeout: 60 * time.Second}

func osName() string {
	switch runtime.GOOS {
	case "windows":
		return "windows"
	case "darwin":
		return "osx"
	}
	return "linux"
}

func cpSep() string {
	if runtime.GOOS == "windows" {
		return ";"
	}
	return ":"
}

// ---------- small download helpers ----------

func getJSON(url string, v any) error {
	req, _ := http.NewRequest("GET", url, nil)
	req.Header.Set("User-Agent", "OlkkusClient/1.0")
	resp, err := httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return fmt.Errorf("%s: %s", url, resp.Status)
	}
	return json.NewDecoder(resp.Body).Decode(v)
}

func fileSHA1(path string) string {
	f, err := os.Open(path)
	if err != nil {
		return ""
	}
	defer f.Close()
	h := sha1.New()
	io.Copy(h, f)
	return hex.EncodeToString(h.Sum(nil))
}

// download saves url to path unless a good copy is already there.
func download(url, path, sha string, size int64) error {
	if st, err := os.Stat(path); err == nil {
		if sha == "" && (size == 0 || st.Size() == size) {
			return nil
		}
		if sha != "" && (size == 0 || st.Size() == size) && fileSHA1(path) == sha {
			return nil
		}
	}
	if url == "" {
		return fmt.Errorf("no download link for %s", filepath.Base(path))
	}
	os.MkdirAll(filepath.Dir(path), 0o755)
	var last error
	for try := 0; try < 3; try++ {
		last = func() error {
			req, _ := http.NewRequest("GET", url, nil)
			req.Header.Set("User-Agent", "OlkkusClient/1.0")
			c := &http.Client{Timeout: 10 * time.Minute}
			resp, err := c.Do(req)
			if err != nil {
				return err
			}
			defer resp.Body.Close()
			if resp.StatusCode != 200 {
				return fmt.Errorf("%s: %s", url, resp.Status)
			}
			tmp := path + ".part"
			f, err := os.Create(tmp)
			if err != nil {
				return err
			}
			h := sha1.New()
			_, err = io.Copy(io.MultiWriter(f, h), resp.Body)
			f.Close()
			if err != nil {
				os.Remove(tmp)
				return err
			}
			if sha != "" && hex.EncodeToString(h.Sum(nil)) != sha {
				os.Remove(tmp)
				return fmt.Errorf("broken download: %s", filepath.Base(path))
			}
			return os.Rename(tmp, path)
		}()
		if last == nil {
			return nil
		}
		time.Sleep(time.Second)
	}
	return last
}

type dlItem struct {
	URL, Path, SHA string
	Size           int64
}

// downloadAll fetches many files at once and reports progress.
func downloadAll(items []dlItem, label string, t *task) error {
	if len(items) == 0 {
		return nil
	}
	var mu sync.Mutex
	done := 0
	var firstErr error
	ch := make(chan dlItem)
	var wg sync.WaitGroup
	for w := 0; w < 12; w++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for it := range ch {
				err := download(it.URL, it.Path, it.SHA, it.Size)
				mu.Lock()
				done++
				if err != nil && firstErr == nil {
					firstErr = err
				}
				t.set(label, done, len(items))
				mu.Unlock()
			}
		}()
	}
	for _, it := range items {
		ch <- it
	}
	close(ch)
	wg.Wait()
	return firstErr
}

// ---------- version files ----------

type manifest struct {
	Latest   struct{ Release, Snapshot string } `json:"latest"`
	Versions []struct {
		ID   string `json:"id"`
		Type string `json:"type"`
		URL  string `json:"url"`
		SHA1 string `json:"sha1"`
	} `json:"versions"`
}

var (
	manMu     sync.Mutex
	manCache  *manifest
	manCached time.Time
)

func getManifest() (*manifest, error) {
	manMu.Lock()
	defer manMu.Unlock()
	if manCache != nil && time.Since(manCached) < 10*time.Minute {
		return manCache, nil
	}
	var m manifest
	if err := getJSON(manifestURL, &m); err != nil {
		// offline: use the last copy we saved
		b, e2 := os.ReadFile(filepath.Join(baseDir(), "versions", "version_manifest_v2.json"))
		if e2 != nil || json.Unmarshal(b, &m) != nil {
			return nil, err
		}
		return &m, nil
	}
	b, _ := json.Marshal(m)
	os.MkdirAll(filepath.Join(baseDir(), "versions"), 0o755)
	os.WriteFile(filepath.Join(baseDir(), "versions", "version_manifest_v2.json"), b, 0o644)
	manCache, manCached = &m, time.Now()
	return &m, nil
}

type artifact struct {
	Path string `json:"path"`
	SHA1 string `json:"sha1"`
	Size int64  `json:"size"`
	URL  string `json:"url"`
}

type rule struct {
	Action string `json:"action"`
	OS     struct {
		Name string `json:"name"`
		Arch string `json:"arch"`
	} `json:"os"`
	Features map[string]bool `json:"features"`
}

type library struct {
	Name      string `json:"name"`
	URL       string `json:"url,omitempty"`
	Downloads struct {
		Artifact    *artifact           `json:"artifact,omitempty"`
		Classifiers map[string]artifact `json:"classifiers,omitempty"`
	} `json:"downloads"`
	Rules   []rule            `json:"rules,omitempty"`
	Natives map[string]string `json:"natives,omitempty"`
	SHA1    string            `json:"sha1,omitempty"`
	Size    int64             `json:"size,omitempty"`
}

type versionJSON struct {
	ID                 string `json:"id"`
	InheritsFrom       string `json:"inheritsFrom,omitempty"`
	Type               string `json:"type"`
	MainClass          string `json:"mainClass"`
	MinecraftArguments string `json:"minecraftArguments,omitempty"`
	Arguments          *struct {
		Game []json.RawMessage `json:"game"`
		JVM  []json.RawMessage `json:"jvm"`
	} `json:"arguments,omitempty"`
	Libraries  []library `json:"libraries"`
	AssetIndex *struct {
		ID   string `json:"id"`
		SHA1 string `json:"sha1"`
		Size int64  `json:"size"`
		URL  string `json:"url"`
	} `json:"assetIndex,omitempty"`
	Assets      string              `json:"assets,omitempty"`
	Downloads   map[string]artifact `json:"downloads,omitempty"`
	JavaVersion *struct {
		Component    string `json:"component"`
		MajorVersion int    `json:"majorVersion"`
	} `json:"javaVersion,omitempty"`
	Jar string `json:"jar,omitempty"`
}

func versionsDir() string { return filepath.Join(baseDir(), "versions") }

func versionFile(id string) string { return filepath.Join(versionsDir(), id, id+".json") }

// ensureVanilla downloads the vanilla version json if we don't have it.
func ensureVanilla(id string) error {
	if _, err := os.Stat(versionFile(id)); err == nil {
		return nil
	}
	m, err := getManifest()
	if err != nil {
		return err
	}
	for _, v := range m.Versions {
		if v.ID == id {
			return download(v.URL, versionFile(id), v.SHA1, 0)
		}
	}
	return fmt.Errorf("Minecraft %s was not found", id)
}

func readVersion(id string) (*versionJSON, error) {
	b, err := os.ReadFile(versionFile(id))
	if err != nil {
		if e := ensureVanilla(id); e != nil {
			return nil, e
		}
		if b, err = os.ReadFile(versionFile(id)); err != nil {
			return nil, err
		}
	}
	var v versionJSON
	if err := json.Unmarshal(b, &v); err != nil {
		return nil, fmt.Errorf("version %s: %v", id, err)
	}
	return &v, nil
}

func libKey(name string) string {
	p := strings.Split(strings.SplitN(name, "@", 2)[0], ":")
	if len(p) >= 4 {
		return p[0] + ":" + p[1] + ":" + p[3]
	}
	if len(p) >= 2 {
		return p[0] + ":" + p[1]
	}
	return name
}

// resolveVersion loads a version and everything it inherits from, merged.
// It also returns the id of the vanilla game it is built on.
func resolveVersion(id string) (*versionJSON, string, error) {
	v, err := readVersion(id)
	if err != nil {
		return nil, "", err
	}
	if v.InheritsFrom == "" {
		return v, v.ID, nil
	}
	p, base, err := resolveVersion(v.InheritsFrom)
	if err != nil {
		return nil, "", err
	}
	seen := map[string]bool{}
	var libs []library
	for _, l := range v.Libraries {
		if !seen[libKey(l.Name)] {
			seen[libKey(l.Name)] = true
			libs = append(libs, l)
		}
	}
	for _, l := range p.Libraries {
		if !seen[libKey(l.Name)] {
			seen[libKey(l.Name)] = true
			libs = append(libs, l)
		}
	}
	out := *p
	out.ID = v.ID
	out.Libraries = libs
	if v.MainClass != "" {
		out.MainClass = v.MainClass
	}
	if v.MinecraftArguments != "" {
		out.MinecraftArguments = v.MinecraftArguments
	}
	if v.Arguments != nil {
		if out.Arguments == nil {
			out.Arguments = v.Arguments
		} else {
			a := *out.Arguments
			a.Game = append(append([]json.RawMessage{}, p.Arguments.Game...), v.Arguments.Game...)
			a.JVM = append(append([]json.RawMessage{}, p.Arguments.JVM...), v.Arguments.JVM...)
			out.Arguments = &a
		}
	}
	if v.JavaVersion != nil {
		out.JavaVersion = v.JavaVersion
	}
	if v.Type != "" {
		out.Type = v.Type
	}
	return &out, base, nil
}

func rulesAllow(rules []rule) bool {
	if len(rules) == 0 {
		return true
	}
	ok := false
	for _, r := range rules {
		match := true
		if r.OS.Name != "" && r.OS.Name != osName() {
			match = false
		}
		if r.OS.Arch != "" && r.OS.Arch == "x86" && runtime.GOARCH == "amd64" {
			match = false
		}
		for _, on := range r.Features {
			if on { // we don't use demo mode, custom size, quick play...
				match = false
			}
		}
		if match {
			ok = r.Action == "allow"
		}
	}
	return ok
}

func mavenPath(name string) string {
	ext := "jar"
	if i := strings.Index(name, "@"); i >= 0 {
		ext = name[i+1:]
		name = name[:i]
	}
	p := strings.Split(name, ":")
	if len(p) < 3 {
		return name
	}
	file := p[1] + "-" + p[2]
	if len(p) >= 4 {
		file += "-" + p[3]
	}
	return filepath.Join(append(strings.Split(p[0], "."), p[1], p[2], file+"."+ext)...)
}

func libsDir() string { return filepath.Join(baseDir(), "libraries") }

type libFile struct {
	dl     dlItem
	native bool
}

func libraryFiles(v *versionJSON) []libFile {
	var out []libFile
	for _, l := range v.Libraries {
		if !rulesAllow(l.Rules) {
			continue
		}
		isNative := strings.Contains(l.Name, ":natives-")
		if a := l.Downloads.Artifact; a != nil && (a.URL != "" || a.Path != "") {
			p := a.Path
			if p == "" {
				p = mavenPath(l.Name)
			}
			out = append(out, libFile{dlItem{a.URL, filepath.Join(libsDir(), filepath.FromSlash(p)), a.SHA1, a.Size}, isNative})
		} else if l.Downloads.Artifact == nil && l.Natives == nil {
			base := l.URL
			if base == "" {
				base = "https://libraries.minecraft.net/"
			}
			if !strings.HasSuffix(base, "/") {
				base += "/"
			}
			p := filepath.ToSlash(mavenPath(l.Name))
			out = append(out, libFile{dlItem{base + p, filepath.Join(libsDir(), filepath.FromSlash(p)), l.SHA1, l.Size}, isNative})
		}
		if cls, ok := l.Natives[osName()]; ok {
			cls = strings.ReplaceAll(cls, "${arch}", "64")
			if a, ok := l.Downloads.Classifiers[cls]; ok {
				p := a.Path
				if p == "" {
					p = mavenPath(l.Name + ":" + cls)
				}
				out = append(out, libFile{dlItem{a.URL, filepath.Join(libsDir(), filepath.FromSlash(p)), a.SHA1, a.Size}, true})
			}
		}
	}
	return out
}

func extractNatives(jar, dest string) error {
	r, err := zip.OpenReader(jar)
	if err != nil {
		return err
	}
	defer r.Close()
	for _, f := range r.File {
		if f.FileInfo().IsDir() || strings.HasPrefix(f.Name, "META-INF") {
			continue
		}
		low := strings.ToLower(f.Name)
		if !(strings.HasSuffix(low, ".dll") || strings.HasSuffix(low, ".so") || strings.HasSuffix(low, ".dylib") || strings.HasSuffix(low, ".jnilib")) {
			continue
		}
		out := filepath.Join(dest, filepath.Base(f.Name))
		rc, err := f.Open()
		if err != nil {
			continue
		}
		w, err := os.Create(out)
		if err == nil {
			io.Copy(w, rc)
			w.Close()
		}
		rc.Close()
	}
	return nil
}

// ---------- assets ----------

func assetsDir() string { return filepath.Join(baseDir(), "assets") }

func ensureAssets(v *versionJSON, gameDir string, t *task) (string, error) {
	if v.AssetIndex == nil {
		return assetsDir(), nil
	}
	idx := filepath.Join(assetsDir(), "indexes", v.AssetIndex.ID+".json")
	if err := download(v.AssetIndex.URL, idx, v.AssetIndex.SHA1, v.AssetIndex.Size); err != nil {
		return "", err
	}
	var ai struct {
		Objects map[string]struct {
			Hash string `json:"hash"`
			Size int64  `json:"size"`
		} `json:"objects"`
		Virtual        bool `json:"virtual"`
		MapToResources bool `json:"map_to_resources"`
	}
	b, _ := os.ReadFile(idx)
	if err := json.Unmarshal(b, &ai); err != nil {
		return "", err
	}
	var items []dlItem
	for _, o := range ai.Objects {
		h := o.Hash
		items = append(items, dlItem{"https://resources.download.minecraft.net/" + h[:2] + "/" + h,
			filepath.Join(assetsDir(), "objects", h[:2], h), h, o.Size})
	}
	if err := downloadAll(items, "Downloading sounds and textures", t); err != nil {
		return "", err
	}
	if ai.Virtual || ai.MapToResources {
		dest := filepath.Join(assetsDir(), "virtual", v.AssetIndex.ID)
		if ai.MapToResources {
			dest = filepath.Join(gameDir, "resources")
		}
		for name, o := range ai.Objects {
			to := filepath.Join(dest, filepath.FromSlash(name))
			if _, err := os.Stat(to); err == nil {
				continue
			}
			os.MkdirAll(filepath.Dir(to), 0o755)
			copyFile(filepath.Join(assetsDir(), "objects", o.Hash[:2], o.Hash), to)
		}
		return dest, nil
	}
	return assetsDir(), nil
}

func copyFile(from, to string) error {
	in, err := os.Open(from)
	if err != nil {
		return err
	}
	defer in.Close()
	os.MkdirAll(filepath.Dir(to), 0o755)
	out, err := os.Create(to)
	if err != nil {
		return err
	}
	defer out.Close()
	_, err = io.Copy(out, in)
	return err
}

// ---------- Java ----------

func javaExe(dir string) string {
	if runtime.GOOS == "windows" {
		return filepath.Join(dir, "bin", "javaw.exe")
	}
	return filepath.Join(dir, "bin", "java")
}

func javaPlatform() string {
	switch runtime.GOOS {
	case "windows":
		return "windows-x64"
	case "darwin":
		if runtime.GOARCH == "arm64" {
			return "mac-os-arm64"
		}
		return "mac-os"
	}
	return "linux"
}

// ensureJava downloads Mojang's Java for this Minecraft version and returns java's path.
func ensureJava(v *versionJSON, t *task) (string, error) {
	if p := loadConfig().JavaPath; p != "" {
		return p, nil
	}
	comp := "jre-legacy"
	if v.JavaVersion != nil && v.JavaVersion.Component != "" {
		comp = v.JavaVersion.Component
	}
	dir := filepath.Join(baseDir(), "runtime", comp)
	if _, err := os.Stat(filepath.Join(dir, ".done")); err == nil {
		return javaExe(dir), nil
	}
	t.set("Getting Java ("+comp+")", 0, 1)
	var all map[string]map[string][]struct {
		Manifest struct{ URL string } `json:"manifest"`
	}
	if err := getJSON(javaManifestURL, &all); err != nil {
		return "", err
	}
	list := all[javaPlatform()][comp]
	if len(list) == 0 {
		return "", fmt.Errorf("no Java %s for this computer", comp)
	}
	var man struct {
		Files map[string]struct {
			Type       string `json:"type"`
			Executable bool   `json:"executable"`
			Target     string `json:"target"`
			Downloads  struct {
				Raw artifact `json:"raw"`
			} `json:"downloads"`
		} `json:"files"`
	}
	if err := getJSON(list[0].Manifest.URL, &man); err != nil {
		return "", err
	}
	var items []dlItem
	var execs []string
	for p, f := range man.Files {
		to := filepath.Join(dir, filepath.FromSlash(p))
		switch f.Type {
		case "directory":
			os.MkdirAll(to, 0o755)
		case "file":
			items = append(items, dlItem{f.Downloads.Raw.URL, to, f.Downloads.Raw.SHA1, f.Downloads.Raw.Size})
			if f.Executable {
				execs = append(execs, to)
			}
		case "link":
			if runtime.GOOS != "windows" {
				os.MkdirAll(filepath.Dir(to), 0o755)
				os.Symlink(f.Target, to)
			}
		}
	}
	if err := downloadAll(items, "Downloading Java", t); err != nil {
		return "", err
	}
	for _, e := range execs {
		os.Chmod(e, 0o755)
	}
	os.WriteFile(filepath.Join(dir, ".done"), []byte("ok"), 0o644)
	return javaExe(dir), nil
}

// ---------- launching ----------

func offlineUUID(name string) string {
	h := md5.Sum([]byte("OfflinePlayer:" + name))
	h[6] = h[6]&0x0f | 0x30
	h[8] = h[8]&0x3f | 0x80
	return hex.EncodeToString(h[:])
}

type launchInfo struct {
	Java    string
	Args    []string
	GameDir string
}

func expandArgs(raw []json.RawMessage, vars map[string]string) []string {
	var out []string
	sub := func(s string) string {
		for k, v := range vars {
			s = strings.ReplaceAll(s, "${"+k+"}", v)
		}
		return s
	}
	for _, r := range raw {
		var s string
		if json.Unmarshal(r, &s) == nil {
			out = append(out, sub(s))
			continue
		}
		var obj struct {
			Rules []rule          `json:"rules"`
			Value json.RawMessage `json:"value"`
		}
		if json.Unmarshal(r, &obj) != nil || !rulesAllow(obj.Rules) {
			continue
		}
		var one string
		var many []string
		if json.Unmarshal(obj.Value, &one) == nil {
			out = append(out, sub(one))
		} else if json.Unmarshal(obj.Value, &many) == nil {
			for _, m := range many {
				out = append(out, sub(m))
			}
		}
	}
	return out
}

// prepare downloads everything a profile needs and builds the java command.
func prepare(p *profile, acc account, t *task) (*launchInfo, error) {
	t.set("Getting Minecraft "+p.Version, 0, 1)
	if err := ensureVanilla(p.Version); err != nil {
		return nil, err
	}
	id, err := installLoader(p, t)
	if err != nil {
		return nil, err
	}
	v, base, err := resolveVersion(id)
	if err != nil {
		return nil, err
	}
	gameDir := profileDir(p)
	os.MkdirAll(gameDir, 0o755)

	// the game itself
	baseV, err := readVersion(base)
	if err != nil {
		return nil, err
	}
	client, ok := baseV.Downloads["client"]
	if !ok {
		return nil, errors.New("this version has no game download")
	}
	baseJar := filepath.Join(versionsDir(), base, base+".jar")
	t.set("Downloading Minecraft "+base, 0, 1)
	if err := download(client.URL, baseJar, client.SHA1, client.Size); err != nil {
		return nil, err
	}
	jar := baseJar
	if id != base {
		jar = filepath.Join(versionsDir(), id, id+".jar")
		if st, err := os.Stat(jar); err != nil || st.Size() == 0 {
			copyFile(baseJar, jar)
		}
	}

	// libraries
	libs := libraryFiles(v)
	var items []dlItem
	for _, l := range libs {
		items = append(items, l.dl)
	}
	if err := downloadAll(items, "Downloading libraries", t); err != nil {
		return nil, err
	}
	natives := filepath.Join(baseDir(), "natives", id)
	os.RemoveAll(natives)
	os.MkdirAll(natives, 0o755)
	var cp []string
	seen := map[string]bool{}
	for _, l := range libs {
		if l.native {
			extractNatives(l.dl.Path, natives)
		}
		if !seen[l.dl.Path] {
			seen[l.dl.Path] = true
			cp = append(cp, l.dl.Path)
		}
	}
	cp = append(cp, jar)

	assetsRoot, err := ensureAssets(v, gameDir, t)
	if err != nil {
		return nil, err
	}
	java, err := ensureJava(v, t)
	if err != nil {
		return nil, err
	}

	assetID := v.Assets
	if v.AssetIndex != nil {
		assetID = v.AssetIndex.ID
	}
	userType := "legacy"
	if acc.Type == "microsoft" {
		userType = "msa"
	}
	token := acc.Token
	if token == "" {
		token = "0"
	}
	vars := map[string]string{
		"auth_player_name": acc.Name, "version_name": id, "game_directory": gameDir,
		"assets_root": assetsRoot, "game_assets": assetsRoot, "assets_index_name": assetID,
		"auth_uuid": acc.UUID, "auth_access_token": token, "auth_session": "token:" + token + ":" + acc.UUID,
		"clientid": "", "auth_xuid": "", "user_type": userType, "version_type": "Olkku's Client",
		"user_properties": "{}", "natives_directory": natives, "launcher_name": "olkkus-client",
		"launcher_version": appVersion, "classpath": strings.Join(cp, cpSep()), "classpath_separator": cpSep(),
		"library_directory": libsDir(),
	}

	var args []string
	ram := p.RAM
	if ram < 1024 {
		ram = 4096
	}
	args = append(args, fmt.Sprintf("-Xmx%dM", ram), fmt.Sprintf("-Xms%dM", min(ram, 1024)))
	if p.Boost {
		args = append(args, boostJVMFlags...)
	}
	if extra := strings.TrimSpace(loadConfig().JVMArgs); extra != "" {
		args = append(args, strings.Fields(extra)...)
	}
	if v.Arguments != nil && len(v.Arguments.JVM) > 0 {
		args = append(args, expandArgs(v.Arguments.JVM, vars)...)
	} else {
		args = append(args, "-Djava.library.path="+natives, "-cp", vars["classpath"])
	}
	args = append(args, v.MainClass)
	if v.Arguments != nil && len(v.Arguments.Game) > 0 {
		args = append(args, expandArgs(v.Arguments.Game, vars)...)
	} else {
		for _, a := range strings.Fields(v.MinecraftArguments) {
			for k, val := range vars {
				a = strings.ReplaceAll(a, "${"+k+"}", val)
			}
			args = append(args, a)
		}
	}
	return &launchInfo{Java: java, Args: args, GameDir: gameDir}, nil
}

var boostJVMFlags = []string{
	"-XX:+UnlockExperimentalVMOptions", "-XX:+UseG1GC", "-XX:G1NewSizePercent=20",
	"-XX:G1ReservePercent=20", "-XX:MaxGCPauseMillis=50", "-XX:G1HeapRegionSize=32M",
	"-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch", "-XX:+ParallelRefProcEnabled",
}

// running game processes, by profile name
var (
	gamesMu sync.Mutex
	games   = map[string]*exec.Cmd{}
)

func startGame(p *profile, li *launchInfo, t *task) error {
	cmd := exec.Command(li.Java, li.Args...)
	cmd.Dir = li.GameDir
	hideWindow(cmd)
	logPath := filepath.Join(li.GameDir, "olkkus-client-latest.log")
	lf, err := os.Create(logPath)
	if err == nil {
		cmd.Stdout = lf
		cmd.Stderr = lf
	}
	if err := cmd.Start(); err != nil {
		return err
	}
	gamesMu.Lock()
	games[p.Name] = cmd
	gamesMu.Unlock()
	go func() {
		cmd.Wait()
		if lf != nil {
			lf.Close()
		}
		gamesMu.Lock()
		delete(games, p.Name)
		gamesMu.Unlock()
		if cmd.ProcessState != nil && cmd.ProcessState.ExitCode() != 0 {
			t.logf("Minecraft closed with an error (code %d). Log: %s", cmd.ProcessState.ExitCode(), logPath)
			t.crashLog = tailFile(logPath, 40)
		} else {
			t.logf("Minecraft closed.")
		}
	}()
	return nil
}

func tailFile(path string, n int) string {
	b, err := os.ReadFile(path)
	if err != nil {
		return ""
	}
	lines := strings.Split(string(b), "\n")
	if len(lines) > n {
		lines = lines[len(lines)-n:]
	}
	return strings.Join(lines, "\n")
}

// installedVersions lists version folders (used to spot what an installer made).
func installedVersions() map[string]bool {
	out := map[string]bool{}
	es, _ := os.ReadDir(versionsDir())
	for _, e := range es {
		if e.IsDir() {
			if _, err := os.Stat(versionFile(e.Name())); err == nil {
				out[e.Name()] = true
			}
		}
	}
	return out
}

func sortedKeys(m map[string]bool) []string {
	var k []string
	for s := range m {
		k = append(k, s)
	}
	sort.Strings(k)
	return k
}
