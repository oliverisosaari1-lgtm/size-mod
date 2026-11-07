package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"syscall"

	webview2 "github.com/jchv/go-webview2"
)

func openBrowser(u string) {
	cmd := exec.Command("rundll32", "url.dll,FileProtocolHandler", u)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
	_ = cmd.Start()
}

func openFolder(dir string) { exec.Command("explorer", dir).Start() }

func hideWindow(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
}

// scale fits a size to the person's display zoom (125%, 150%...).
func scale(n uint) uint {
	proc := syscall.NewLazyDLL("user32.dll").NewProc("GetDpiForSystem")
	if proc.Find() != nil {
		return n
	}
	dpi, _, _ := proc.Call()
	if dpi < 96 {
		return n
	}
	return n * uint(dpi) / 96
}

func main() {
	addr, token, err := newServer()
	if err != nil {
		os.Exit(1)
	}
	w := webview2.NewWithOptions(webview2.WebViewOptions{
		Debug:     os.Getenv("OLKKU_DEBUG") != "",
		AutoFocus: true,
		DataPath:  filepath.Join(os.Getenv("LOCALAPPDATA"), "OlkkusClient"),
		WindowOptions: webview2.WindowOptions{
			Title:  "Olkku's Client",
			Width:  scale(1100),
			Height: scale(700),
			IconId: 1,
			Center: true,
		},
	})
	if w == nil {
		openBrowser("https://developer.microsoft.com/microsoft-edge/webview2/")
		os.Exit(1)
	}
	defer w.Destroy()
	w.SetSize(int(scale(900)), int(scale(600)), webview2.HintMin)
	w.Navigate("http://" + addr + "/?t=" + token)
	w.Run()
}
