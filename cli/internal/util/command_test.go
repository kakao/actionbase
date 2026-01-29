package util

import (
	"archive/zip"
	"bytes"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func TestDownload(t *testing.T) {
	content := "test file content"
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(content))
	}))
	defer server.Close()

	tempFile := filepath.Join(os.TempDir(), "test_download.txt")
	defer os.Remove(tempFile)

	success := Download(tempFile, server.URL+"/test")
	if !success {
		t.Error("Download should succeed")
	}

	data, err := os.ReadFile(tempFile)
	if err != nil {
		t.Errorf("Failed to read downloaded file: %v", err)
	}
	if string(data) != content {
		t.Errorf("Downloaded content = %q, want %q", string(data), content)
	}
}

func TestUnzip(t *testing.T) {
	// Create a test zip in memory
	var buf bytes.Buffer
	zw := zip.NewWriter(&buf)
	w, err := zw.Create("test.txt")
	if err != nil {
		t.Fatal(err)
	}
	w.Write([]byte("test content"))
	zw.Close()

	tempDir := os.TempDir()
	zipFile := filepath.Join(tempDir, "test.zip")
	defer os.Remove(zipFile)
	defer os.RemoveAll(filepath.Join(tempDir, "test_unzip"))

	err = os.WriteFile(zipFile, buf.Bytes(), 0644)
	if err != nil {
		t.Fatal(err)
	}

	destDir := filepath.Join(tempDir, "test_unzip")
	err = Unzip(zipFile, destDir)
	if err != nil {
		t.Errorf("Unzip failed: %v", err)
	}

	extractedFile := filepath.Join(destDir, "test.txt")
	data, err := os.ReadFile(extractedFile)
	if err != nil {
		t.Errorf("Failed to read extracted file: %v", err)
	}
	if string(data) != "test content" {
		t.Errorf("Extracted content = %q, want %q", string(data), "test content")
	}
}

func TestDownload_Error(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	defer server.Close()

	tempFile := filepath.Join(os.TempDir(), "test_download_error.txt")
	defer os.Remove(tempFile)

	success := Download(tempFile, server.URL+"/notfound")
	if success {
		t.Error("Download should fail for 404")
	}

	// Test invalid URL
	success = Download(tempFile, "invalid-url")
	if success {
		t.Error("Download should fail for invalid URL")
	}
}

func TestUnzip_Error(t *testing.T) {
	tempDir := os.TempDir()
	invalidZip := filepath.Join(tempDir, "invalid.zip")
	defer os.Remove(invalidZip)
	defer os.RemoveAll(filepath.Join(tempDir, "test_unzip_error"))

	err := os.WriteFile(invalidZip, []byte("not a zip"), 0644)
	if err != nil {
		t.Fatal(err)
	}

	destDir := filepath.Join(tempDir, "test_unzip_error")
	err = Unzip(invalidZip, destDir)
	if err == nil {
		t.Error("Unzip should fail for invalid zip")
	}
}

