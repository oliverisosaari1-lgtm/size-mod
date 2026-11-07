package main

// Mods: search and install from Modrinth, FPS Boost, and the Olkku's Client in-game mod.

import (
	"encoding/json"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

const modrinth = "https://api.modrinth.com/v2"

// FPS Boost: well known performance mods, installed if they exist for the version.
var boostMods = map[string][]string{
	"fabric":   {"sodium", "lithium", "ferrite-core", "entityculling", "immediatelyfast", "modernfix", "dynamic-fps"},
	"quilt":    {"sodium", "lithium", "ferrite-core", "entityculling", "immediatelyfast", "modernfix", "dynamic-fps"},
	"neoforge": {"sodium", "ferrite-core", "entityculling", "immediatelyfast", "modernfix"},
	"forge":    {"embeddium", "ferrite-core", "entityculling", "immediatelyfast", "modernfix"},
}

// The Olkku's Client in-game mod (Right Shift menu, HUD, capes), per Minecraft version.
var olkkuMod = map[string]string{
	"26.3": "https://github.com/oliverisosaari1-lgtm/size-mod/releases/download/olkkuclient/olkkus-client-26.3.jar",
}

func modsDir(p *profile) string { return filepath.Join(profileDir(p), "mods") }

type modHit struct {
	ID          string `json:"project_id"`
	Slug        string `json:"slug"`
	Title       string `json:"title"`
	Description string `json:"description"`
	Icon        string `json:"icon_url"`
	Downloads   int    `json:"downloads"`
	Author      string `json:"author"`
}

func loaderForModrinth(p *profile) string {
	if p.Loader == "" || p.Loader == "vanilla" || p.Loader == "liteloader" {
		return ""
	}
	return p.Loader
}

func searchMods(p *profile, q string) ([]modHit, error) {
	l := loaderForModrinth(p)
	if l == "" {
		return nil, fmt.Errorf("mods need a mod loader. Pick Fabric, Quilt, Forge or NeoForge for this profile")
	}
	facets := fmt.Sprintf(`[["versions:%s"],["categories:%s"],["project_type:mod"]]`, p.Version, l)
	u := modrinth + "/search?limit=30&index=relevance&query=" + url.QueryEscape(q) + "&facets=" + url.QueryEscape(facets)
	var r struct {
		Hits []modHit `json:"hits"`
	}
	if err := getJSON(u, &r); err != nil {
		return nil, err
	}
	return r.Hits, nil
}

type mrVersion struct {
	ID    string `json:"id"`
	Files []struct {
		URL      string            `json:"url"`
		Filename string            `json:"filename"`
		Primary  bool              `json:"primary"`
		Hashes   map[string]string `json:"hashes"`
		Size     int64             `json:"size"`
	} `json:"files"`
	Dependencies []struct {
		ProjectID string `json:"project_id"`
		Type      string `json:"dependency_type"`
	} `json:"dependencies"`
}

// installMod installs a Modrinth project (and the mods it needs) into the profile.
func installMod(p *profile, project string, depth int) (string, error) {
	l := loaderForModrinth(p)
	if l == "" {
		return "", fmt.Errorf("this profile has no mod loader")
	}
	loaders := []string{l}
	if l == "quilt" {
		loaders = append(loaders, "fabric")
	}
	lj, _ := json.Marshal(loaders)
	gj, _ := json.Marshal([]string{p.Version})
	u := modrinth + "/project/" + url.PathEscape(project) + "/version?loaders=" + url.QueryEscape(string(lj)) + "&game_versions=" + url.QueryEscape(string(gj))
	var vs []mrVersion
	if err := getJSON(u, &vs); err != nil {
		return "", err
	}
	if len(vs) == 0 || len(vs[0].Files) == 0 {
		return "", fmt.Errorf("%s has no version for %s %s", project, loaderNames[l], p.Version)
	}
	v := vs[0]
	f := v.Files[0]
	for _, x := range v.Files {
		if x.Primary {
			f = x
		}
	}
	os.MkdirAll(modsDir(p), 0o755)
	if err := download(f.URL, filepath.Join(modsDir(p), f.Filename), f.Hashes["sha1"], f.Size); err != nil {
		return "", err
	}
	if depth < 3 {
		for _, d := range v.Dependencies {
			if d.Type == "required" && d.ProjectID != "" && !hasModProject(p, d.ProjectID) {
				installMod(p, d.ProjectID, depth+1)
			}
		}
	}
	markProject(p, project, f.Filename)
	return f.Filename, nil
}

// a tiny record of which file came from which project, so we don't install twice
func projFile(p *profile) string { return filepath.Join(modsDir(p), ".olkku-mods.json") }

func readProj(p *profile) map[string]string {
	m := map[string]string{}
	b, err := os.ReadFile(projFile(p))
	if err == nil {
		json.Unmarshal(b, &m)
	}
	return m
}

func markProject(p *profile, project, file string) {
	m := readProj(p)
	m[project] = file
	b, _ := json.Marshal(m)
	os.WriteFile(projFile(p), b, 0o644)
}

func hasModProject(p *profile, project string) bool {
	f, ok := readProj(p)[project]
	if !ok {
		return false
	}
	_, err := os.Stat(filepath.Join(modsDir(p), f))
	return err == nil
}

type modFile struct {
	File    string `json:"file"`
	Name    string `json:"name"`
	Enabled bool   `json:"enabled"`
}

func listMods(p *profile) []modFile {
	var out []modFile
	es, _ := os.ReadDir(modsDir(p))
	for _, e := range es {
		n := e.Name()
		if e.IsDir() || !(strings.HasSuffix(n, ".jar") || strings.HasSuffix(n, ".jar.disabled")) {
			continue
		}
		on := strings.HasSuffix(n, ".jar")
		out = append(out, modFile{n, strings.TrimSuffix(strings.TrimSuffix(n, ".disabled"), ".jar"), on})
	}
	sort.Slice(out, func(i, j int) bool { return strings.ToLower(out[i].Name) < strings.ToLower(out[j].Name) })
	return out
}

func safeModPath(p *profile, file string) (string, error) {
	if file == "" || strings.ContainsAny(file, `/\`) || strings.Contains(file, "..") {
		return "", fmt.Errorf("bad file name")
	}
	return filepath.Join(modsDir(p), file), nil
}

// setupMods runs before every start: FPS Boost and the in-game client mod.
func setupMods(p *profile, t *task) {
	l := loaderForModrinth(p)
	if l == "" {
		return
	}
	if p.Boost {
		list := boostMods[l]
		for i, slug := range list {
			t.set("FPS Boost: "+slug, i, len(list))
			if !hasModProject(p, slug) {
				if _, err := installMod(p, slug, 0); err != nil {
					t.logf("FPS Boost: %s isn't available for this version", slug)
				}
			}
		}
	}
	if p.OlkkuMod && (l == "fabric" || l == "quilt") {
		if u, ok := olkkuMod[p.Version]; ok {
			t.set("Installing Olkku's Client menu", 0, 1)
			os.MkdirAll(modsDir(p), 0o755)
			if err := download(u, filepath.Join(modsDir(p), "olkkus-client-"+p.Version+".jar"), "", 0); err != nil {
				t.logf("Couldn't get the Olkku's Client mod: %v", err)
			}
			if !hasModProject(p, "fabric-api") {
				installMod(p, "fabric-api", 0)
			}
		}
	}
}
