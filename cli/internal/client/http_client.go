package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

type Context struct {
	IsDebuggingEnabled bool
}

type Response struct {
	StatusCode int
	Body       string
	Error      error
}

// HTTPClient represents an HTTP client for Actionbase API
type HTTPClient struct {
	baseUrl string
	authKey *string
	client  *http.Client
	context *Context
}

// NewHTTPClient creates a new HTTP client instance
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

func (c *HTTPClient) Get(uri string) *Response {
	url := fmt.Sprintf("%s%s", c.baseUrl, uri)
	request, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return &Response{StatusCode: -1, Body: "", Error: fmt.Errorf("failed to create request: %w", err)}
	}

	request.Header.Set("Content-Type", "application/json")
	if c.authKey != nil {
		request.Header.Set("Authorization", *c.authKey)
	}

	return c.call(request)
}

func Post[T any](uri string, requestBody T, c *HTTPClient) *Response {
	url := fmt.Sprintf("%s%s", c.baseUrl, uri)
	jsonData, err := json.Marshal(requestBody)
	if err != nil {
		return &Response{StatusCode: -1, Body: "", Error: fmt.Errorf("failed to marshal request Body: %w", err)}
	}

	request, err := http.NewRequest("POST", url, bytes.NewBuffer(jsonData))
	if err != nil {
		return &Response{StatusCode: -1, Body: "", Error: fmt.Errorf("failed to create request: %w", err)}
	}

	request.Header.Set("Content-Type", "application/json")
	if c.authKey != nil {
		request.Header.Set("Authorization", *c.authKey)
	}

	return c.call(request)
}

func (c *HTTPClient) call(request *http.Request) *Response {
	response, err := c.client.Do(request)
	if err != nil {
		return &Response{StatusCode: -1, Body: "", Error: fmt.Errorf("failed to execute request: %w", err)}
	}

	statusCode := response.StatusCode
	defer func(Body io.ReadCloser) {
		err := Body.Close()
		if err != nil {

		}
	}(response.Body)

	body, err := io.ReadAll(response.Body)
	if err != nil {
		return &Response{StatusCode: statusCode, Body: "", Error: fmt.Errorf("failed to read model Body: %w", err)}
	}

	bodyStr := string(body)
	if statusCode != http.StatusOK && statusCode != http.StatusCreated {
		return &Response{StatusCode: statusCode, Body: bodyStr, Error: fmt.Errorf("HTTP error code: %d", statusCode)}
	}

	if c.context.IsDebuggingEnabled {
		fmt.Printf("\n[DEBUG] GET %s (%d)\n > model: %s\n\n", request.RequestURI, statusCode, bodyStr)
	}

	return &Response{StatusCode: statusCode, Body: bodyStr, Error: nil}
}
