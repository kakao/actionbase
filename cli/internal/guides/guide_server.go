package guides

import (
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

func Start(cwd, name, apiHost, serverPort string) error {
	assetsPath := filepath.Join(cwd, name)

	if _, err := os.Stat(assetsPath); os.IsNotExist(err) {
		fmt.Printf("The guide assets are not found in %s\n", assetsPath)
		return fmt.Errorf("guide assets not found at %s: %w", assetsPath, err)
	}

	apiURL, err := url.Parse(apiHost)
	if err != nil {
		return fmt.Errorf("invalid API host URL: %w", err)
	}

	address := "http://localhost:" + serverPort

	cliURL, err := url.Parse(address + "/api/command")
	if err != nil {
		return fmt.Errorf("invalid CLI host URL: %w", err)
	}

	indexPath := filepath.Join(assetsPath, "index.html")
	assetsFs := os.DirFS(assetsPath)
	guideHandler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasPrefix(r.URL.Path, "/graph") {
			proxy(w, r, apiURL)
			return
		}

		if strings.HasPrefix(r.URL.Path, "/api/command") {
			proxy(w, r, cliURL)
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

	http.Handle("/", guideHandler)

	if err := openBrowser(address); err != nil {
		fmt.Println("failed to open browser automatically; open this URL manually:", address)
	}

	fmt.Println("The guide is running on:", address)
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
