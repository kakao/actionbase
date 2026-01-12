package guides

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"
)

var (
	mutex    sync.Mutex
	listener net.Listener
	server   *http.Server
)

func Start(cwd, name, apiHost, serverPort string) error {
	mutex.Lock()
	defer mutex.Unlock()

	if server != nil {
		fmt.Println("guide server is already running")
		return nil
	}

	assetsPath := filepath.Join(cwd, name)

	if _, err := os.Stat(assetsPath); os.IsNotExist(err) {
		fmt.Printf("The guide assets are not found in %s\n", assetsPath)
		return fmt.Errorf("guide assets not found at %s: %w", assetsPath, err)
	}

	assetsFs := os.DirFS(assetsPath)
	fileServer := http.FileServer(http.FS(assetsFs))

	apiURL, err := url.Parse(apiHost)
	if err != nil {
		return fmt.Errorf("invalid API host URL: %w", err)
	}

	cliURL, err := url.Parse("http://localhost:" + serverPort)
	if err != nil {
		return fmt.Errorf("invalid CLI host URL: %w", err)
	}

	ln, err := net.Listen("tcp", "127.0.0.1:"+serverPort)
	if err != nil {
		return fmt.Errorf("failed to listen on local port: %w", err)
	}
	listener = ln

	indexPath := filepath.Join(assetsPath, "index.html")
	server = &http.Server{
		Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if strings.HasPrefix(r.URL.Path, "/graph") {
				proxy(w, r, apiURL)
				return
			}

			if strings.HasPrefix(r.URL.Path, "/api/command") {
				proxy(w, r, cliURL)
				return
			}

			rw := &responseWriter{ResponseWriter: w, status: http.StatusOK, headerSent: false}
			fileServer.ServeHTTP(rw, r)

			if rw.status == http.StatusNotFound {
				w.Header().Set("Content-Type", "text/html; charset=utf-8")
				http.ServeFile(w, r, indexPath)
				return
			}
		}),
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

func proxy(w http.ResponseWriter, r *http.Request, targetURL *url.URL) {
	proxyURL := *targetURL
	proxyURL.Path = r.URL.Path
	proxyURL.RawQuery = r.URL.RawQuery

	proxyReq, err := http.NewRequest(r.Method, proxyURL.String(), r.Body)
	if err != nil {
		http.Error(w, fmt.Sprintf("Error creating proxy request: %v", err), http.StatusInternalServerError)
		return
	}

	for key, values := range r.Header {
		for _, value := range values {
			proxyReq.Header.Add(key, value)
		}
	}

	client := &http.Client{
		Timeout: 30 * time.Second,
	}
	resp, err := client.Do(proxyReq)
	if err != nil {
		http.Error(w, fmt.Sprintf("Error proxying request: %v", err), http.StatusBadGateway)
		return
	}
	defer func(Body io.ReadCloser) {
		err := Body.Close()
		if err != nil {

		}
	}(resp.Body)

	for key, values := range resp.Header {
		for _, value := range values {
			w.Header().Add(key, value)
		}
	}

	w.WriteHeader(resp.StatusCode)

	_, err = io.Copy(w, resp.Body)
	if err != nil {
		return
	}
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
