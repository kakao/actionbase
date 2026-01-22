package httpserver

import (
	"encoding/json"
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
	"sync/atomic"
	"time"
)

type CommandRequest struct {
	Command string `json:"command"`
}

type CommandResponse struct {
	Success bool    `json:"success"`
	Error   *string `json:"error,omitempty"`
	Result  *string `json:"result,omitempty"`
	Elapsed string  `json:"elapsed,omitempty"`
}

var current atomic.Value
var defaultHandler atomic.Value

func Start(port string, ready chan<- error, handlerFunc http.HandlerFunc) error {
	defaultHandler.Store(handlerFunc)
	current.Store(handlerFunc)

	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		handler := current.Load().(http.Handler)
		handler.ServeHTTP(w, r)
	})

	addr := "0.0.0.0:" + port

	listener, err := net.Listen("tcp", addr)
	if err != nil {
		if ready != nil {
			ready <- err
		}
		return err
	}

	fmt.Printf("Started as server mode running on %s\n", addr)

	if ready != nil {
		ready <- nil
	}

	return http.Serve(listener, nil)
}

func StartGuide(cwd, name, apiHost, serverPort string) error {
	if current.Load() == nil {
		fmt.Println("Server mode is required. Run `actionbase --proxy` to continue")
		return nil
	}

	assetsPath := filepath.Join(cwd, name)

	if _, err := os.Stat(assetsPath); os.IsNotExist(err) {
		fmt.Printf("The guide assets are not found in %s\n", assetsPath)
		return fmt.Errorf("guide assets not found at %s: %w", assetsPath, err)
	}

	guideHandler, err := guideHandler(apiHost, assetsPath)
	if err != nil {
		return err
	}

	current.Store(guideHandler)

	address := "http://localhost:" + serverPort
	if err := openBrowser(address); err != nil {
		fmt.Println("Open this URL in your browser:", address)
	}

	fmt.Println("The guide is now being served")
	return nil
}

func guideHandler(apiHost, assetsPath string) (http.HandlerFunc, error) {
	actionbaseURL, err := url.Parse(apiHost)
	if err != nil {
		return nil, fmt.Errorf("invalid API host URL: %w", err)
	}

	indexPath := filepath.Join(assetsPath, "index.html")
	assetsFs := os.DirFS(assetsPath)

	guideHandler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasPrefix(r.URL.Path, "/api/command") {
			defaultHandler.Load().(http.HandlerFunc)(w, r)
			return
		}

		if strings.HasPrefix(r.URL.Path, "/graph") {
			proxy(w, r, actionbaseURL)
			return
		}

		rw := &responseWriter{ResponseWriter: w, status: http.StatusOK, headerSent: false}
		http.FileServer(http.FS(assetsFs)).ServeHTTP(rw, r)

		if rw.status == http.StatusNotFound {
			w.Header().Set("Content-Type", "text/html; charset=utf-8")
			http.ServeFile(w, r, indexPath)
			return
		}
	})
	return guideHandler, nil
}

func BuildResponse(w http.ResponseWriter, httpStatus int, response *CommandResponse) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(httpStatus)
	if err := json.NewEncoder(w).Encode(response); err != nil {
		fmt.Println("Failed to encode response:", err)
	}
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
