package guides

import "net/http"

type responseWriter struct {
	http.ResponseWriter
	status     int
	headerSent bool
	body       []byte
}

func (rw *responseWriter) WriteHeader(code int) {
	if !rw.headerSent {
		rw.status = code
		if code != http.StatusNotFound {
			rw.headerSent = true
			rw.ResponseWriter.WriteHeader(code)
		}
	}
}

func (rw *responseWriter) Write(b []byte) (int, error) {
	if rw.status == http.StatusNotFound {
		return len(b), nil
	}
	if !rw.headerSent {
		rw.WriteHeader(http.StatusOK)
	}
	return rw.ResponseWriter.Write(b)
}
