package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"time"

	"github.com/kakao/actionbase/internal/util"
)

type Context struct {
	IsProxyModeEnabled bool
	IsDebugEnabled     bool
}

type Response[T any] struct {
	StatusCode int
	Body       *T
	Error      error
}

func NewResponse[T any](statusCode int, body *T, error error) *Response[T] {
	return &Response[T]{StatusCode: statusCode, Body: body, Error: error}
}

func (r *Response[T]) IsSuccess() bool {
	return r.StatusCode >= http.StatusOK && r.StatusCode < http.StatusMultipleChoices
}

func (r *Response[T]) IsError() bool {
	return r.Error != nil || !r.IsSuccess()
}

type HTTPClient struct {
	baseUrl string
	authKey *string
	client  *http.Client
	context *Context
}

func NewHTTPClient(baseUrl string, authKey *string, context *Context) *HTTPClient {
	return &HTTPClient{
		baseUrl: baseUrl,
		authKey: authKey,
		client: &http.Client{
			Timeout: 5 * time.Second,
		},
		context: context,
	}
}

func Get[T any](c *HTTPClient, uri string) *Response[T] {
	url := fmt.Sprintf("%s%s", c.baseUrl, uri)
	request, err := http.NewRequest("GET", url, nil)
	if err != nil {
		var nil T
		return NewResponse[T](-1, &nil, fmt.Errorf("failed to create request: %w", err))
	}

	request.Header.Set("Content-Type", "application/json")
	if c.authKey != nil {
		request.Header.Set("Authorization", *c.authKey)
	}

	return call[T](c, request, nil)
}

// GetStream is Get with two differences that a paged, version-dependent endpoint needs.
//
// It keeps the real status code when the body does not decode. Get replaces it with -1, and an
// unauthorized response carries no body at all, so a caller cannot otherwise tell "this server does
// not serve that path" from "this token may not". And it decodes straight off the connection rather
// than reading the whole body first, so a page is not held twice.
func GetStream[T any](c *HTTPClient, uri string) *Response[T] {
	url := fmt.Sprintf("%s%s", c.baseUrl, uri)
	request, err := http.NewRequest("GET", url, nil)
	if err != nil {
		var empty T
		return NewResponse[T](-1, &empty, fmt.Errorf("failed to create request: %w", err))
	}

	request.Header.Set("Content-Type", "application/json")
	if c.authKey != nil {
		request.Header.Set("Authorization", *c.authKey)
	}

	if c.context.IsDebugEnabled {
		slog.Debug(fmt.Sprintf("→ %s %s", request.Method, request.URL.RequestURI()))
	}

	response, err := c.client.Do(request)
	if err != nil {
		var empty T
		return NewResponse[T](-1, &empty, fmt.Errorf("failed to execute request: %w", err))
	}
	defer func(body io.ReadCloser) {
		_, _ = io.Copy(io.Discard, body)
		_ = body.Close()
	}(response.Body)

	if c.context.IsDebugEnabled {
		slog.Debug(fmt.Sprintf("← %s", response.Status))
	}

	var responseBody T
	if err := json.NewDecoder(response.Body).Decode(&responseBody); err != nil {
		var empty T
		return NewResponse[T](response.StatusCode, &empty, fmt.Errorf("failed to read response Body: %w", err))
	}

	return NewResponse(response.StatusCode, &responseBody, nil)
}

func Post[T any, R any](c *HTTPClient, uri string, requestBody T) *Response[R] {
	url := fmt.Sprintf("%s%s", c.baseUrl, uri)
	requestBodyJson, err := json.Marshal(requestBody)
	if err != nil {
		var nil R
		return NewResponse[R](-1, &nil, fmt.Errorf("failed to marshal request Body: %w", err))
	}

	request, err := http.NewRequest("POST", url, bytes.NewBuffer(requestBodyJson))
	if err != nil {
		var nil R
		return NewResponse[R](-1, &nil, fmt.Errorf("failed to create request: %w", err))
	}

	request.Header.Set("Content-Type", "application/json")
	if c.authKey != nil {
		request.Header.Set("Authorization", *c.authKey)
	}

	return call[R](c, request, requestBodyJson)
}

// PostForResult POSTs a JSON body and returns the HTTP status code and the
// raw response body without parsing it. Returns -1 on transport failure. Used
// by replay-style flows (e.g. migrate apply) that key on the status plus the
// server's error message.
func PostForResult(c *HTTPClient, uri string, requestBody any) (int, string) {
	url := fmt.Sprintf("%s%s", c.baseUrl, uri)
	requestBodyJson, err := json.Marshal(requestBody)
	if err != nil {
		return -1, ""
	}

	request, err := http.NewRequest("POST", url, bytes.NewBuffer(requestBodyJson))
	if err != nil {
		return -1, ""
	}

	request.Header.Set("Content-Type", "application/json")
	if c.authKey != nil {
		request.Header.Set("Authorization", *c.authKey)
	}

	if c.context.IsDebugEnabled {
		slog.Debug(fmt.Sprintf("→ %s %s %s", request.Method, request.URL.RequestURI(), util.Truncate(string(requestBodyJson), 60)))
	}

	response, err := c.client.Do(request)
	if err != nil {
		return -1, ""
	}
	defer func() { _ = response.Body.Close() }()

	responseBody, _ := io.ReadAll(response.Body)

	if c.context.IsDebugEnabled {
		slog.Debug(fmt.Sprintf("← %s %s", response.Status, util.Truncate(string(responseBody), 60)))
	}
	return response.StatusCode, string(responseBody)
}

func call[T any](c *HTTPClient, request *http.Request, requestBody []byte) *Response[T] {
	if c.context.IsDebugEnabled {
		if requestBody == nil {
			slog.Debug(fmt.Sprintf("\u2192 %s %s", request.Method, request.URL.RequestURI()))
		} else {
			slog.Debug(fmt.Sprintf("\u2192 %s %s %s", request.Method, request.URL.RequestURI(), util.Truncate(string(requestBody), 60)))
		}
	}

	response, err := c.client.Do(request)
	if err != nil {
		var nil T
		return NewResponse[T](-1, &nil, fmt.Errorf("failed to execute request: %w", err))
	}

	statusCode := response.StatusCode
	defer func(Body io.ReadCloser) {
		err := Body.Close()
		if err != nil {

		}
	}(response.Body)

	body, err := io.ReadAll(response.Body)

	if c.context.IsDebugEnabled {
		slog.Debug(fmt.Sprintf("\u2190 %s %s", response.Status, util.Truncate(string(body), 60)))
	}

	if err != nil {
		var nil T
		return NewResponse[T](-1, &nil, fmt.Errorf("failed to read response Body: %w", err))
	}

	var responseBody T
	if err := json.Unmarshal(body, &responseBody); err != nil {
		if c.context.IsDebugEnabled {
			slog.Debug(fmt.Sprintf("Failed to parse response: %s", err.Error()))
		}
		var nil T
		return NewResponse[T](-1, &nil, fmt.Errorf("failed to read response Body: %w", err))
	}

	return NewResponse(statusCode, &responseBody, nil)
}
