---
name: cli-patterns
description: Go CLI development patterns, command structure, and best practices for the Actionbase CLI.
---

# CLI Development Patterns

Go CLI patterns and best practices for the Actionbase command-line interface.

## CLI Structure

```
cli/
├── main.go                    # Entry point
├── cmd/
│   ├── root.go               # Root command
│   ├── mutation.go           # Mutation subcommand
│   ├── query.go              # Query subcommand
│   ├── schema.go             # Schema subcommand
│   └── shell.go              # Interactive shell
├── internal/
│   ├── client/               # HTTP client
│   ├── config/               # Configuration
│   ├── output/               # Output formatting
│   └── repl/                 # Interactive REPL
└── pkg/
    └── api/                  # Public API types
```

## Command Patterns

### Root Command Setup

```go
package cmd

import (
    "fmt"
    "os"

    "github.com/spf13/cobra"
)

var (
    serverURL string
    verbose   bool
)

var rootCmd = &cobra.Command{
    Use:   "actionbase",
    Short: "Actionbase CLI - Interact with Actionbase server",
    Long: `Actionbase CLI provides commands to interact with your
Actionbase server for managing user interactions at scale.

Examples:
  actionbase mutation --schema likes --user user123 --target post456
  actionbase query --schema likes --user user123
  actionbase shell`,
}

func init() {
    rootCmd.PersistentFlags().StringVar(&serverURL, "server", "http://localhost:8080", "Server URL")
    rootCmd.PersistentFlags().BoolVarP(&verbose, "verbose", "v", false, "Verbose output")
}

func Execute() {
    if err := rootCmd.Execute(); err != nil {
        fmt.Fprintln(os.Stderr, err)
        os.Exit(1)
    }
}
```

### Subcommand Pattern

```go
package cmd

import (
    "fmt"

    "github.com/spf13/cobra"
)

var (
    mutationSchema   string
    mutationUserID   string
    mutationTargetID string
)

var mutationCmd = &cobra.Command{
    Use:   "mutation",
    Short: "Create a new mutation",
    Long:  `Create a new user interaction mutation in Actionbase.`,
    Example: `  actionbase mutation --schema likes --user user123 --target post456
  actionbase mutation -s follows -u user123 -t user456`,
    RunE: runMutation,
}

func init() {
    mutationCmd.Flags().StringVarP(&mutationSchema, "schema", "s", "", "Schema name (required)")
    mutationCmd.Flags().StringVarP(&mutationUserID, "user", "u", "", "User ID (required)")
    mutationCmd.Flags().StringVarP(&mutationTargetID, "target", "t", "", "Target ID (required)")

    mutationCmd.MarkFlagRequired("schema")
    mutationCmd.MarkFlagRequired("user")
    mutationCmd.MarkFlagRequired("target")

    rootCmd.AddCommand(mutationCmd)
}

func runMutation(cmd *cobra.Command, args []string) error {
    client := NewClient(serverURL)

    result, err := client.CreateMutation(&MutationRequest{
        Schema:   mutationSchema,
        UserID:   mutationUserID,
        TargetID: mutationTargetID,
    })
    if err != nil {
        return fmt.Errorf("mutation failed: %w", err)
    }

    fmt.Printf("Mutation created: %s\n", result.ID)
    return nil
}
```

### Query Command

```go
var queryCmd = &cobra.Command{
    Use:   "query",
    Short: "Query interactions",
    Example: `  actionbase query --schema likes --user user123
  actionbase query -s follows -u user123 --limit 50`,
    RunE: runQuery,
}

func runQuery(cmd *cobra.Command, args []string) error {
    client := NewClient(serverURL)

    results, err := client.Query(&QueryRequest{
        Schema: querySchema,
        UserID: queryUserID,
        Limit:  queryLimit,
    })
    if err != nil {
        return fmt.Errorf("query failed: %w", err)
    }

    // Output formatting
    if outputFormat == "json" {
        return outputJSON(results)
    }

    return outputTable(results)
}
```

## HTTP Client Pattern

```go
package client

import (
    "bytes"
    "encoding/json"
    "fmt"
    "net/http"
    "time"
)

type Client struct {
    baseURL    string
    httpClient *http.Client
}

func NewClient(baseURL string) *Client {
    return &Client{
        baseURL: baseURL,
        httpClient: &http.Client{
            Timeout: 30 * time.Second,
        },
    }
}

func (c *Client) CreateMutation(req *MutationRequest) (*MutationResult, error) {
    body, err := json.Marshal(req)
    if err != nil {
        return nil, fmt.Errorf("marshal request: %w", err)
    }

    httpReq, err := http.NewRequest(
        "POST",
        c.baseURL+"/api/v1/mutation",
        bytes.NewReader(body),
    )
    if err != nil {
        return nil, fmt.Errorf("create request: %w", err)
    }

    httpReq.Header.Set("Content-Type", "application/json")

    resp, err := c.httpClient.Do(httpReq)
    if err != nil {
        return nil, fmt.Errorf("send request: %w", err)
    }
    defer resp.Body.Close()

    if resp.StatusCode != http.StatusCreated {
        var errResp ErrorResponse
        json.NewDecoder(resp.Body).Decode(&errResp)
        return nil, fmt.Errorf("server error: %s", errResp.Error)
    }

    var result MutationResult
    if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
        return nil, fmt.Errorf("decode response: %w", err)
    }

    return &result, nil
}

func (c *Client) Query(req *QueryRequest) ([]Interaction, error) {
    url := fmt.Sprintf("%s/api/v1/query?schema=%s&userId=%s&limit=%d",
        c.baseURL, req.Schema, req.UserID, req.Limit)

    resp, err := c.httpClient.Get(url)
    if err != nil {
        return nil, fmt.Errorf("send request: %w", err)
    }
    defer resp.Body.Close()

    if resp.StatusCode != http.StatusOK {
        var errResp ErrorResponse
        json.NewDecoder(resp.Body).Decode(&errResp)
        return nil, fmt.Errorf("server error: %s", errResp.Error)
    }

    var response QueryResponse
    if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
        return nil, fmt.Errorf("decode response: %w", err)
    }

    return response.Data, nil
}
```

## Interactive REPL Pattern

```go
package repl

import (
    "bufio"
    "fmt"
    "os"
    "strings"

    "github.com/chzyer/readline"
)

type REPL struct {
    client *client.Client
    rl     *readline.Instance
}

func New(serverURL string) (*REPL, error) {
    rl, err := readline.New("actionbase> ")
    if err != nil {
        return nil, err
    }

    return &REPL{
        client: client.NewClient(serverURL),
        rl:     rl,
    }, nil
}

func (r *REPL) Run() error {
    defer r.rl.Close()

    fmt.Println("Actionbase Interactive Shell")
    fmt.Println("Type 'help' for commands, 'exit' to quit")
    fmt.Println()

    for {
        line, err := r.rl.Readline()
        if err != nil {
            return nil // EOF or interrupt
        }

        line = strings.TrimSpace(line)
        if line == "" {
            continue
        }

        if line == "exit" || line == "quit" {
            return nil
        }

        if err := r.execute(line); err != nil {
            fmt.Printf("Error: %v\n", err)
        }
    }
}

func (r *REPL) execute(line string) error {
    parts := strings.Fields(line)
    if len(parts) == 0 {
        return nil
    }

    cmd := parts[0]
    args := parts[1:]

    switch cmd {
    case "help":
        r.printHelp()
    case "mutation":
        return r.handleMutation(args)
    case "query":
        return r.handleQuery(args)
    case "schemas":
        return r.handleSchemas()
    default:
        return fmt.Errorf("unknown command: %s", cmd)
    }

    return nil
}

func (r *REPL) printHelp() {
    fmt.Println(`Commands:
  mutation <schema> <userId> <targetId>  - Create mutation
  query <schema> <userId> [limit]        - Query interactions
  schemas                                 - List schemas
  help                                   - Show this help
  exit                                   - Exit shell`)
}
```

## Output Formatting

```go
package output

import (
    "encoding/json"
    "fmt"
    "os"

    "github.com/olekukonko/tablewriter"
)

func PrintJSON(v interface{}) error {
    encoder := json.NewEncoder(os.Stdout)
    encoder.SetIndent("", "  ")
    return encoder.Encode(v)
}

func PrintTable(headers []string, rows [][]string) {
    table := tablewriter.NewWriter(os.Stdout)
    table.SetHeader(headers)
    table.SetBorder(false)
    table.SetHeaderAlignment(tablewriter.ALIGN_LEFT)
    table.SetAlignment(tablewriter.ALIGN_LEFT)

    for _, row := range rows {
        table.Append(row)
    }

    table.Render()
}

func PrintInteractions(interactions []Interaction, format string) error {
    if format == "json" {
        return PrintJSON(interactions)
    }

    headers := []string{"Schema", "User ID", "Target ID", "Timestamp"}
    rows := make([][]string, len(interactions))

    for i, interaction := range interactions {
        rows[i] = []string{
            interaction.Schema,
            interaction.UserID,
            interaction.TargetID,
            interaction.Timestamp.Format("2006-01-02 15:04:05"),
        }
    }

    PrintTable(headers, rows)
    return nil
}
```

## Configuration Pattern

```go
package config

import (
    "encoding/json"
    "os"
    "path/filepath"
)

type Config struct {
    ServerURL string `json:"server_url"`
    Timeout   int    `json:"timeout"`
    Verbose   bool   `json:"verbose"`
}

func DefaultConfig() *Config {
    return &Config{
        ServerURL: "http://localhost:8080",
        Timeout:   30,
        Verbose:   false,
    }
}

func LoadConfig() (*Config, error) {
    configPath := filepath.Join(os.Getenv("HOME"), ".actionbase", "config.json")

    file, err := os.Open(configPath)
    if os.IsNotExist(err) {
        return DefaultConfig(), nil
    }
    if err != nil {
        return nil, err
    }
    defer file.Close()

    var config Config
    if err := json.NewDecoder(file).Decode(&config); err != nil {
        return nil, err
    }

    return &config, nil
}

func SaveConfig(config *Config) error {
    configDir := filepath.Join(os.Getenv("HOME"), ".actionbase")
    if err := os.MkdirAll(configDir, 0755); err != nil {
        return err
    }

    configPath := filepath.Join(configDir, "config.json")
    file, err := os.Create(configPath)
    if err != nil {
        return err
    }
    defer file.Close()

    encoder := json.NewEncoder(file)
    encoder.SetIndent("", "  ")
    return encoder.Encode(config)
}
```

## Testing Patterns

### Table-Driven Tests

```go
func TestParseCommand(t *testing.T) {
    tests := []struct {
        name    string
        input   string
        want    *Command
        wantErr bool
    }{
        {
            name:  "valid mutation",
            input: "mutation likes user123 post456",
            want: &Command{
                Type:     "mutation",
                Schema:   "likes",
                UserID:   "user123",
                TargetID: "post456",
            },
        },
        {
            name:    "missing args",
            input:   "mutation likes",
            wantErr: true,
        },
        {
            name:    "unknown command",
            input:   "unknown",
            wantErr: true,
        },
    }

    for _, tt := range tests {
        t.Run(tt.name, func(t *testing.T) {
            got, err := ParseCommand(tt.input)
            if (err != nil) != tt.wantErr {
                t.Errorf("ParseCommand() error = %v, wantErr %v", err, tt.wantErr)
                return
            }
            if !tt.wantErr && !reflect.DeepEqual(got, tt.want) {
                t.Errorf("ParseCommand() = %v, want %v", got, tt.want)
            }
        })
    }
}
```

### Mock HTTP Server

```go
func TestClient_CreateMutation(t *testing.T) {
    // Mock server
    server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        if r.URL.Path != "/api/v1/mutation" {
            t.Errorf("unexpected path: %s", r.URL.Path)
        }
        if r.Method != "POST" {
            t.Errorf("unexpected method: %s", r.Method)
        }

        w.Header().Set("Content-Type", "application/json")
        w.WriteHeader(http.StatusCreated)
        json.NewEncoder(w).Encode(MutationResult{
            ID:      "mut123",
            Success: true,
        })
    }))
    defer server.Close()

    client := NewClient(server.URL)
    result, err := client.CreateMutation(&MutationRequest{
        Schema:   "likes",
        UserID:   "user123",
        TargetID: "post456",
    })

    if err != nil {
        t.Fatalf("unexpected error: %v", err)
    }
    if result.ID != "mut123" {
        t.Errorf("got ID %s, want mut123", result.ID)
    }
}
```

## Build Commands

```bash
# Build for current platform
go build -o actionbase-cli ./cmd/actionbase

# Build for multiple platforms
GOOS=linux GOARCH=amd64 go build -o actionbase-cli-linux-amd64 ./cmd/actionbase
GOOS=darwin GOARCH=amd64 go build -o actionbase-cli-darwin-amd64 ./cmd/actionbase
GOOS=windows GOARCH=amd64 go build -o actionbase-cli-windows-amd64.exe ./cmd/actionbase

# Run tests
go test ./...

# Run with coverage
go test -cover ./...

# Lint
golangci-lint run
```

**Remember**: CLI should be intuitive, well-documented, and provide clear error messages.
