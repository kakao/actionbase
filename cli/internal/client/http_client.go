package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"time"
)

type Context struct {
	IsServerModeEnabled bool
	IsDebugEnabled      bool
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

	return call[T](c, request)
}

func Post[T any, R any](c *HTTPClient, uri string, requestBody T) *Response[R] {
	url := fmt.Sprintf("%s%s", c.baseUrl, uri)
	jsonData, err := json.Marshal(requestBody)
	if err != nil {
		var nil R
		return NewResponse[R](-1, &nil, fmt.Errorf("failed to marshal request Body: %w", err))
	}

	request, err := http.NewRequest("POST", url, bytes.NewBuffer(jsonData))
	if err != nil {
		var nil R
		return NewResponse[R](-1, &nil, fmt.Errorf("failed to create request: %w", err))
	}

	request.Header.Set("Content-Type", "application/json")
	if c.authKey != nil {
		request.Header.Set("Authorization", *c.authKey)
	}

	return call[R](c, request)
}

func call[T any](c *HTTPClient, request *http.Request) *Response[T] {
	if c.context.IsDebugEnabled {
		if request.Body != nil {
			slog.Debug(fmt.Sprintf("Trying to call '%s %s'\n> request: %s", request.Method, request.URL, request.Body))
		} else {
			slog.Debug(fmt.Sprintf("Trying to call '%s %s'", request.Method, request.URL))
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
		slog.Debug(fmt.Sprintf("%s\n> %s", response.Status, body))
	}

	if err != nil {
		var nil T
		return NewResponse[T](-1, &nil, fmt.Errorf("failed to read response Body: %w", err))
	}

	var responseBody T
	if err := json.Unmarshal(body, &responseBody); err != nil {
		if c.context.IsDebugEnabled {
			slog.Debug(fmt.Sprintf("Failed to parse response: %s\n", err.Error()))
		}
		var nil T
		return NewResponse[T](-1, &nil, fmt.Errorf("failed to read response Body: %w", err))
	}

	return NewResponse(statusCode, &responseBody, nil)
}
