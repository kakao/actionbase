package guides

import (
	"context"
	"embed"
	"errors"
	"fmt"
	"io/fs"
	"net"
	"net/http"
	"os/exec"
	"runtime"
	"sync"
	"time"
)

var guideFs embed.FS

var (
	mutex    sync.Mutex
	listener net.Listener
	server   *http.Server
)

func Start(assetPath string) error {
	mutex.Lock()
	defer mutex.Unlock()

	if server != nil {
		fmt.Println("guide server is already running")
		return nil
	}

	sub, err := fs.Sub(guideFs, assetPath)
	if err != nil {
		return fmt.Errorf("failed to load guide server assets: %w", err)
	}

	fileServer := http.FileServer(http.FS(sub))

	handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fileServer.ServeHTTP(w, r)
	})

	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return fmt.Errorf("failed to listen on local port: %w", err)
	}
	listener = ln

	server = &http.Server{
		Handler: handler,
	}

	address := "http://" + ln.Addr().String()

	go func() {
		fmt.Printf("Starting guide server at %s\n", address)
		if serveErr := server.Serve(ln); serveErr != nil &&
			!errors.Is(serveErr, http.ErrServerClosed) &&
			!errors.Is(serveErr, net.ErrClosed) {
			fmt.Println("HTTP server error:", serveErr)
		}

		mutex.Lock()
		listener = nil
		server = nil
		mutex.Unlock()
	}()

	if err := openBrowser(address); err != nil {
		fmt.Println("failed to open browser automatically; open this URL manually:", address)
	}

	return nil
}

func Stop() error {
	mutex.Lock()
	defer mutex.Unlock()

	if server == nil {
		return fmt.Errorf("guide server is not running")
	}

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	if err := server.Shutdown(ctx); err != nil && !errors.Is(err, context.Canceled) {
		return fmt.Errorf("failed to stop guide server: %w", err)
	}

	if listener != nil {
		_ = listener.Close()
	}

	listener = nil
	server = nil

	fmt.Println("guide server is stopped")

	return nil
}

func openBrowser(url string) error {
	switch runtime.GOOS {
	case "darwin":
		return exec.Command("open", url).Start()
	case "windows":
		return exec.Command("rundll32", "url.dll,FileProtocolHandler", url).Start()
	default: // linux, *bsd, etc.
		return exec.Command("xdg-open", url).Start()
	}
}
