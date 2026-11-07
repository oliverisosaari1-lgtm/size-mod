//go:build !windows

package main

import (
	"fmt"
	"os"
	"os/exec"
	"os/signal"
)

// On Linux/macOS this only runs the server, for testing in a normal browser.
func openBrowser(u string)     { fmt.Println("open:", u) }
func openFolder(dir string)    { fmt.Println("folder:", dir) }
func hideWindow(cmd *exec.Cmd) {}

func main() {
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
