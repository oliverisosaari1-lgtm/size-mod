package main

// Mod loaders: Fabric, Quilt, Forge, NeoForge and LiteLoader.

import (
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

var loaderNames = map[string]string{
	"vanilla": "Vanilla", "fabric": "Fabric", "quilt": "Quilt",
	"forge": "Forge", "neoforge": "NeoForge", "liteloader": "LiteLoader",
}

// ---------- which loaders exist for a version ----------

var (
	availMu    sync.Mutex
	availCache = map[string]map[string]string{}
)

// loadersFor returns loader -> newest loader version for one Minecraft version.
func loadersFor(mc string) map[string]string {
	availMu.Lock()
	if c, ok := availCache[mc]; ok {
		availMu.Unlock()
		return c
	}
	availMu.Unlock()
	out := map[string]string{"vanilla": mc}
	var wg sync.WaitGroup
	var mu sync.Mutex
	put := func(k, v string) {
		if v != "" {
			mu.Lock()
			out[k] = v
			mu.Unlock()
		}
	}
	wg.Add(5)
	go func() { defer wg.Done(); put("fabric", fabricLatest(mc)) }()
	go func() { defer wg.Done(); put("quilt", quiltLatest(mc)) }()
	go func() { defer wg.Done(); put("forge", forgeLatest(mc)) }()
	go func() { defer wg.Done(); put("neoforge", neoforgeLatest(mc)) }()
	go func() { defer wg.Done(); put("liteloader", liteLatest(mc)) }()
	wg.Wait()
	availMu.Lock()
	availCache[mc] = out
	availMu.Unlock()
	return out
}

func fabricLatest(mc string) string {
	var list []struct {
		Loader struct {
			Version string `json:"version"`
			Stable  bool   `json:"stable"`
		} `json:"loader"`
	}
	if getJSON("https://meta.fabricmc.net/v2/versions/loader/"+mc, &list) != nil || len(list) == 0 {
		return ""
	}
	for _, l := range list {
		if l.Loader.Stable {
			return l.Loader.Version
		}
	}
	return list[0].Loader.Version
}

func quiltLatest(mc string) string {
	var list []struct {
		Loader struct {
			Version string `json:"version"`
		} `json:"loader"`
	}
	if getJSON("https://meta.quiltmc.org/v3/versions/loader/"+mc, &list) != nil || len(list) == 0 {
		return ""
	}
	for _, l := range list {
		if !strings.Contains(l.Loader.Version, "beta") {
			return l.Loader.Version
		}
	}
	return list[0].Loader.Version
}

func forgeLatest(mc string) string {
	var promos struct {
		Promos map[string]string `json:"promos"`
	}
	if getJSON("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json", &promos) != nil {
		return ""
	}
	if v := promos.Promos[mc+"-recommended"]; v != "" {
		return v
	}
	return promos.Promos[mc+"-latest"]
}

// NeoForge versions look like 21.1.77 for Minecraft 1.21.1, or 26.3.0.12 for 26.3.
func neoforgePrefix(mc string) string {
	p := strings.Split(mc, ".")
	if p[0] == "1" && len(p) >= 2 {
		minor := "0"
		if len(p) >= 3 {
			minor = p[2]
		}
		return p[1] + "." + minor + "."
	}
	if len(p) == 2 {
		return mc + ".0."
	}
	return mc + "."
}

var (
	neoMu    sync.Mutex
	neoList  []string
	neoFetch time.Time
)

func neoforgeLatest(mc string) string {
	neoMu.Lock()
	if neoList == nil || time.Since(neoFetch) > 30*time.Minute {
		var r struct {
			Versions []string `json:"versions"`
		}
		if getJSON("https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge", &r) == nil {
			neoList, neoFetch = r.Versions, time.Now()
		}
	}
	list := neoList
	neoMu.Unlock()
	prefix := neoforgePrefix(mc)
	best, bestBeta := "", ""
	for _, v := range list {
		if !strings.HasPrefix(v, prefix) {
			continue
		}
		if strings.Contains(v, "beta") || strings.Contains(v, "alpha") {
			bestBeta = v
		} else {
			best = v
		}
	}
	if best != "" {
		return best
	}
	return bestBeta
}

type liteVersions struct {
	Versions map[string]struct {
		Repo struct {
			URL string `json:"url"`
		} `json:"repo"`
		Artefacts map[string]map[string]liteBuild `json:"artefacts"`
		Snapshots map[string]map[string]liteBuild `json:"snapshots"`
	} `json:"versions"`
}

type liteBuild struct {
	Version    string `json:"version"`
	File       string `json:"file"`
	TweakClass string `json:"tweakClass"`
	Libraries  []struct {
		Name string `json:"name"`
		URL  string `json:"url"`
	} `json:"libraries"`
}

var (
	liteMu   sync.Mutex
	liteData *liteVersions
)

func liteInfo() *liteVersions {
	liteMu.Lock()
	defer liteMu.Unlock()
	if liteData == nil {
		var l liteVersions
		if getJSON("https://dl.liteloader.com/versions/versions.json", &l) == nil {
			liteData = &l
		}
	}
	return liteData
}

func liteFor(mc string) (*liteBuild, string) {
	l := liteInfo()
	if l == nil {
		return nil, ""
	}
	v, ok := l.Versions[mc]
	if !ok {
		return nil, ""
	}
	repo := v.Repo.URL
	if repo == "" {
		repo = "https://dl.liteloader.com/versions/"
	}
	for _, set := range []map[string]map[string]liteBuild{v.Artefacts, v.Snapshots} {
		if b, ok := set["com.mumfrey:liteloader"]; ok {
			if lb, ok := b["latest"]; ok {
				return &lb, repo
			}
			// otherwise the newest by key
			var keys []string
			for k := range b {
				keys = append(keys, k)
			}
			sort.Strings(keys)
			if len(keys) > 0 {
				lb := b[keys[len(keys)-1]]
				return &lb, repo
			}
		}
	}
	return nil, ""
}

func liteLatest(mc string) string {
	if b, _ := liteFor(mc); b != nil {
		return b.Version
	}
	return ""
}

// ---------- installing ----------

// installLoader makes sure the profile's loader is installed and returns the version id to start.
func installLoader(p *profile, t *task) (string, error) {
	switch p.Loader {
	case "", "vanilla":
		return p.Version, nil
	case "fabric":
		return installMeta(p, "Fabric", "https://meta.fabricmc.net/v2/versions/loader/", fabricLatest, t)
	case "quilt":
		return installMeta(p, "Quilt", "https://meta.quiltmc.org/v3/versions/loader/", quiltLatest, t)
	case "forge":
		return installForge(p, t)
	case "neoforge":
		return installNeoForge(p, t)
	case "liteloader":
		return installLite(p, t)
	}
	return "", fmt.Errorf("unknown loader %q", p.Loader)
}

func installMeta(p *profile, name, base string, latest func(string) string, t *task) (string, error) {
	lv := p.LoaderVersion
	if lv == "" {
		t.set("Finding the newest "+name, 0, 1)
		if lv = latest(p.Version); lv == "" {
			return "", fmt.Errorf("%s doesn't exist for Minecraft %s", name, p.Version)
		}
	}
	var raw map[string]any
	if err := getJSON(base+p.Version+"/"+lv+"/profile/json", &raw); err != nil {
		return "", err
	}
	id, _ := raw["id"].(string)
	if id == "" {
		return "", fmt.Errorf("%s gave a broken profile", name)
	}
	b, _ := json.MarshalIndent(raw, "", "  ")
	os.MkdirAll(filepath.Dir(versionFile(id)), 0o755)
	if err := os.WriteFile(versionFile(id), b, 0o644); err != nil {
		return "", err
	}
	return id, nil
}

func ensureLauncherProfiles() {
	f := filepath.Join(baseDir(), "launcher_profiles.json")
	if _, err := os.Stat(f); err != nil {
		os.WriteFile(f, []byte(`{"profiles":{},"settings":{},"version":3}`), 0o644)
	}
}

// runInstaller runs a Forge/NeoForge installer and returns the new version id.
func runInstaller(p *profile, url, marker string, flags [][]string, t *task) (string, error) {
	if id := findInstalled(p.Version, marker); id != "" {
		return id, nil
	}
	inst := filepath.Join(baseDir(), "installers", filepath.Base(url))
	t.set("Downloading the installer", 0, 1)
	if err := download(url, inst, "", 0); err != nil {
		return "", err
	}
	// the installer needs Java; use the one for this Minecraft version
	v, err := readVersion(p.Version)
	if err != nil {
		return "", err
	}
	java, err := ensureJava(v, t)
	if err != nil {
		return "", err
	}
	if strings.HasSuffix(java, "javaw.exe") {
		java = strings.TrimSuffix(java, "javaw.exe") + "java.exe"
	}
	ensureLauncherProfiles()
	before := installedVersions()
	t.set("Installing (this can take a few minutes)", 0, 1)
	var out []byte
	for _, f := range flags {
		cmd := exec.Command(java, append([]string{"-jar", inst}, append(f, baseDir())...)...)
		cmd.Dir = filepath.Dir(inst)
		hideWindow(cmd)
		out, err = cmd.CombinedOutput()
		if err == nil {
			break
		}
	}
	after := installedVersions()
	for id := range after {
		if !before[id] && strings.Contains(strings.ToLower(id), marker) {
			return id, nil
		}
	}
	if id := findInstalled(p.Version, marker); id != "" {
		return id, nil
	}
	msg := string(out)
	if len(msg) > 600 {
		msg = msg[len(msg)-600:]
	}
	return "", fmt.Errorf("the installer didn't finish: %v %s", err, msg)
}

func findInstalled(mc, marker string) string {
	for _, id := range sortedKeys(installedVersions()) {
		low := strings.ToLower(id)
		if !strings.Contains(low, marker) {
			continue
		}
		v, err := readVersion(id)
		if err == nil && (v.InheritsFrom == mc || strings.HasPrefix(id, mc+"-")) {
			return id
		}
	}
	return ""
}

func installForge(p *profile, t *task) (string, error) {
	fv := p.LoaderVersion
	if fv == "" {
		if fv = forgeLatest(p.Version); fv == "" {
			return "", fmt.Errorf("Forge doesn't exist for Minecraft %s", p.Version)
		}
	}
	full := p.Version + "-" + fv
	url := "https://maven.minecraftforge.net/net/minecraftforge/forge/" + full + "/forge-" + full + "-installer.jar"
	return runInstaller(p, url, "forge", [][]string{{"--installClient"}, {"--install-client"}}, t)
}

func installNeoForge(p *profile, t *task) (string, error) {
	nv := p.LoaderVersion
	if nv == "" {
		if nv = neoforgeLatest(p.Version); nv == "" {
			return "", fmt.Errorf("NeoForge doesn't exist for Minecraft %s", p.Version)
		}
	}
	url := "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + nv + "/neoforge-" + nv + "-installer.jar"
	return runInstaller(p, url, "neoforge", [][]string{{"--install-client"}, {"--installClient"}}, t)
}

func installLite(p *profile, t *task) (string, error) {
	b, repo := liteFor(p.Version)
	if b == nil {
		return "", fmt.Errorf("LiteLoader only exists for old versions (1.5.2 to 1.12.2)")
	}
	id := p.Version + "-LiteLoader"
	if !strings.HasSuffix(repo, "/") {
		repo += "/"
	}
	libs := []map[string]any{{"name": "com.mumfrey:liteloader:" + b.Version, "url": repo}}
	for _, l := range b.Libraries {
		m := map[string]any{"name": l.Name}
		if l.URL != "" {
			m["url"] = l.URL
		}
		libs = append(libs, m)
	}
	vj, err := readVersion(p.Version)
	if err != nil {
		return "", err
	}
	tweak := b.TweakClass
	if tweak == "" {
		tweak = "com.mumfrey.liteloader.launch.LiteLoaderTweaker"
	}
	args := strings.TrimSpace(vj.MinecraftArguments + " --tweakClass " + tweak)
	j := map[string]any{
		"id": id, "inheritsFrom": p.Version, "type": "release",
		"mainClass":          "net.minecraft.launchwrapper.Launch",
		"minecraftArguments": args, "libraries": libs,
	}
	data, _ := json.MarshalIndent(j, "", "  ")
	os.MkdirAll(filepath.Dir(versionFile(id)), 0o755)
	if err := os.WriteFile(versionFile(id), data, 0o644); err != nil {
		return "", err
	}
	return id, nil
}

// versionLess sorts "1.20.10" after "1.20.9".
func versionLess(a, b string) bool {
	pa, pb := strings.Split(a, "."), strings.Split(b, ".")
	for i := 0; i < len(pa) && i < len(pb); i++ {
		x, e1 := strconv.Atoi(pa[i])
		y, e2 := strconv.Atoi(pb[i])
		if e1 != nil || e2 != nil {
			if pa[i] != pb[i] {
				return pa[i] < pb[i]
			}
			continue
		}
		if x != y {
			return x < y
		}
	}
	return len(pa) < len(pb)
}
