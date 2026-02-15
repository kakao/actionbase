---
name: codemap-cli
description: "CLI module structure - Go Cobra commands, HTTP client, output formatting."
---

# CLI Module

## Purpose

Command-line client for Actionbase. Written in Go with Cobra.

## Package Structure

```
cli/
├── main.go              # Entry point
├── cmd/
│   ├── root.go          # Root command, global flags
│   ├── mutation.go      # mutation subcommand
│   ├── query.go         # query subcommand
│   ├── schema.go        # schema subcommand
│   └── shell.go         # Interactive REPL
├── client/
│   ├── client.go        # HTTP client
│   ├── mutation.go      # Mutation API calls
│   └── query.go         # Query API calls
├── config/
│   └── config.go        # Configuration loading
└── output/
    ├── formatter.go     # Output formatting
    ├── json.go          # JSON output
    └── table.go         # Table output
```

## Commands

```bash
actionbase mutation --schema likes --user user1 --target post1
actionbase mutation --schema likes --user user1 --target post1 --delete
actionbase query --schema likes --user user1 --limit 20
actionbase schema list
actionbase schema get likes
actionbase shell  # Interactive mode
```

## Key Patterns

### Functional Options
```go
type Option func(*Config)

func WithTimeout(d time.Duration) Option {
    return func(c *Config) { c.Timeout = d }
}

func NewClient(opts ...Option) *Client {
    cfg := defaultConfig()
    for _, opt := range opts {
        opt(cfg)
    }
    return &Client{config: cfg}
}
```

### Error Handling
```go
result, err := client.Mutation(ctx, req)
if err != nil {
    return fmt.Errorf("mutation failed: %w", err)
}
```

### Table-Driven Tests
```go
func TestQuery(t *testing.T) {
    tests := []struct{
        name    string
        input   QueryRequest
        want    QueryResponse
        wantErr bool
    }{
        {"valid query", validReq, validResp, false},
        {"empty user", emptyUserReq, nil, true},
    }
    for _, tt := range tests {
        t.Run(tt.name, func(t *testing.T) {
            // test
        })
    }
}
```

## Build

```bash
make build      # Build CLI
make test       # Run tests
make install    # Install to $GOPATH/bin
```

## Dependencies

- Cobra (CLI framework)
- Viper (configuration)
- Server (HTTP API)
