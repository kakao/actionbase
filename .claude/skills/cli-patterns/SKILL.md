---
name: cli-patterns
description: Go CLI development patterns for Actionbase CLI.
---

# CLI Development Patterns

See `CLAUDE.md` for Go code patterns and testing guidelines.

## Structure

```
cli/
├── main.go              # Entry point
├── cmd/                 # Cobra commands
│   ├── root.go
│   ├── mutation.go
│   ├── query.go
│   └── shell.go
├── internal/
│   ├── client/          # HTTP client
│   ├── config/          # Configuration
│   └── output/          # Formatting
└── pkg/api/             # Public types
```

## Cobra Command Pattern

```go
var mutationCmd = &cobra.Command{
    Use:     "mutation",
    Short:   "Create a new mutation",
    Example: `actionbase mutation --schema likes --user user123 --target post456`,
    RunE:    runMutation,
}

func init() {
    mutationCmd.Flags().StringVarP(&schema, "schema", "s", "", "Schema (required)")
    mutationCmd.MarkFlagRequired("schema")
    rootCmd.AddCommand(mutationCmd)
}
```

## HTTP Client Pattern

```go
type Client struct {
    baseURL    string
    httpClient *http.Client
}

func (c *Client) CreateMutation(req *MutationRequest) (*Result, error) {
    resp, err := c.httpClient.Post(c.baseURL+"/api/v1/mutation", "application/json", body)
    if err != nil {
        return nil, fmt.Errorf("request failed: %w", err)
    }
    defer resp.Body.Close()
    // decode response...
}
```

## Output Formatting

```go
// JSON output
func PrintJSON(v interface{}) error {
    encoder := json.NewEncoder(os.Stdout)
    encoder.SetIndent("", "  ")
    return encoder.Encode(v)
}

// Table output (using tablewriter)
func PrintTable(headers []string, rows [][]string) {
    table := tablewriter.NewWriter(os.Stdout)
    table.SetHeader(headers)
    table.AppendBulk(rows)
    table.Render()
}
```

## Testing

```go
// Table-driven tests
func TestCommand(t *testing.T) {
    tests := []struct {
        name    string
        input   string
        want    string
        wantErr bool
    }{
        {"valid", "mutation likes user1 post1", "mut123", false},
        {"missing args", "mutation likes", "", true},
    }
    for _, tt := range tests {
        t.Run(tt.name, func(t *testing.T) {
            // test...
        })
    }
}

// Mock HTTP server
server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
    w.WriteHeader(http.StatusOK)
    json.NewEncoder(w).Encode(result)
}))
defer server.Close()
```

## Build

```bash
go build -o actionbase ./cmd/actionbase
go test ./...
go test -cover ./...
```
