package guides

import (
	"archive/zip"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
)

func Download(name string) bool {
	filename := fmt.Sprintf("%s-latest.zip", name)
	url := fmt.Sprintf("https://github.com/kakao/actionbase/releases/download/guides/%s/%s", name, filename)
	fmt.Println("Downloading guide assets from", url)

	req, err := http.NewRequest("GET", url, nil)
	req.Header.Set("Accept", "application/octet-stream")

	if err != nil {
		fmt.Println("Failed to create request")
		return false
	}

	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		fmt.Println("Failed to download guide assets")
		return false
	}
	defer func(Body io.ReadCloser) {
		err := Body.Close()
		if err != nil {
			fmt.Println("Failed to close response body")
		}
	}(resp.Body)

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		fmt.Println("Failed to read response body")
		return false
	}

	if resp.StatusCode != http.StatusOK {
		fmt.Println("Failed to download guide assets")
		return false
	}

	if err := os.WriteFile(filename, body, 0644); err != nil {
		fmt.Println("Failed to write file")
		return false
	}

	fmt.Println("Successfully downloaded guide assets")
	return true
}

func Unzip(src, dest string) error {
	r, err := zip.OpenReader(src)
	if err != nil {
		return err
	}
	defer func(r *zip.ReadCloser) {
		err := r.Close()
		if err != nil {

		}
	}(r)

	if err := os.MkdirAll(dest, 0755); err != nil {
		return err
	}

	for _, f := range r.File {
		err := extractFile(f, dest)
		if err != nil {
			return err
		}
	}

	return nil
}

func extractFile(f *zip.File, dest string) error {
	filePath := filepath.Join(dest, f.Name)
	if !strings.HasPrefix(filePath, filepath.Clean(dest)+string(os.PathSeparator)) {
		return fmt.Errorf("illegal file path: %s", f.Name)
	}

	if f.FileInfo().IsDir() {
		return os.MkdirAll(filePath, f.Mode())
	}

	if err := os.MkdirAll(filepath.Dir(filePath), 0755); err != nil {
		fmt.Println("Failed to create directory:", err)
		return err
	}

	rc, err := f.Open()
	if err != nil {
		fmt.Println("Failed to open file:", err)
		return err
	}
	defer func(rc io.ReadCloser) {
		err := rc.Close()
		if err != nil {
		}
	}(rc)

	outFile, err := os.OpenFile(filePath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, f.Mode())
	if err != nil {
		fmt.Println("Failed to create file:", err)
		return err
	}
	defer func(outFile *os.File) {
		err := outFile.Close()
		if err != nil {
		}
	}(outFile)

	_, err = io.Copy(outFile, rc)
	return err
}
