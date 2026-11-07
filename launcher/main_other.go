//go:build !windows

package main

import (
	"fmt"
	"os"
	"os/exec"
	"os/signal"
	"strings"
	"time"
)

// On Linux/macOS this runs the server for testing in a browser,
// or "test <version> <loader> [seconds]" to install and start a game for real.
func openBrowser(u string)     { fmt.Println("open:", u) }
func openFolder(dir string)    { fmt.Println("folder:", dir) }
func hideWindow(cmd *exec.Cmd) {}

func main() {
	if len(os.Args) >= 4 && os.Args[1] == "test" {
		secs := 90
		if len(os.Args) >= 5 {
			fmt.Sscan(os.Args[4], &secs)
		}
		p := &profile{Name: "test-" + os.Args[2] + "-" + os.Args[3], Version: os.Args[2], Loader: os.Args[3], RAM: 2048,
			Boost: os.Getenv("BOOST") != "", OlkkuMod: os.Getenv("OLKKU") != ""}
		t := &task{}
		go func() {
			last := ""
			for {
				time.Sleep(3 * time.Second)
				t.mu.Lock()
				s := fmt.Sprintf("%s %d/%d", t.Step, t.Done, t.Total)
				t.mu.Unlock()
				if s != last {
					fmt.Println("  ..", s)
					last = s
				}
			}
		}()
		setupMods(p, t)
		li, err := prepare(p, account{Name: "Olkku", UUID: offlineUUID("Olkku"), Type: "offline"}, t)
		if err != nil {
			fmt.Println("PREPARE FAILED:", err)
			os.Exit(1)
		}
		fmt.Println("java:", li.Java)
		fmt.Println("main class:", li.Args[indexOfMain(li.Args)])
		if err := startGame(p, li, t); err != nil {
			fmt.Println("START FAILED:", err)
			os.Exit(1)
		}
		time.Sleep(time.Duration(secs) * time.Second)
		gamesMu.Lock()
		cmd := games[p.Name]
		gamesMu.Unlock()
		alive := cmd != nil
		if cmd != nil {
			cmd.Process.Kill()
		}
		time.Sleep(time.Second)
		fmt.Println("---- game log tail ----")
		fmt.Println(tailFile(li.GameDir+"/olkkus-client-latest.log", 60))
		fmt.Println("STILL RUNNING AFTER", secs, "s:", alive)
		for _, l := range t.Log {
			fmt.Println("task:", l)
		}
		if !alive {
			os.Exit(2)
		}
		return
	}
	addr, token, err := newServer()
	if err != nil {
		fmt.Println(err)
		os.Exit(1)
	}
	fmt.Printf("http://%s/?t=%s\n", addr, token)
	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt)
	<-c
}

func indexOfMain(args []string) int {
	for i, a := range args {
		if !strings.HasPrefix(a, "-") && i > 0 && !strings.HasPrefix(args[i-1], "-cp") && !strings.HasPrefix(args[i-1], "-p") && strings.Contains(a, ".") && !strings.Contains(a, "/") {
			return i
		}
	}
	return 0
}
