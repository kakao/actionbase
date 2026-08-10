package command

import (
	"encoding/json"
	"fmt"
	"os"
	"strconv"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/util"
)

const (
	defaultPurgeFile = "purge.json"
	candidatesPath   = "/control/metastore/purge/candidates"
	executePath      = "/control/metastore/purge/execute"
	restorePath      = "/control/metastore/purge/restore"
	defaultOlderThan = 30
	defaultMaxRows   = 500
	purgeFilePerm    = 0o600
	httpOK           = 200
)

// purgeSet is only enough of the document to summarise it. The file is posted
// back byte for byte, so nothing here reshapes it - a field the server adds
// later still round-trips even though this struct has never heard of it.
type purgeSet struct {
	Metastore   string `json:"metastore"`
	Table       string `json:"table"`
	Service     string `json:"service"`
	GeneratedAt string `json:"generatedAt"`
	Scanned     int    `json:"scanned"`
	NextCursor  *int64 `json:"nextCursor"`
	Rows        []struct {
		K string `json:"k"`
	} `json:"rows"`
	Undecodable []struct {
		K      string `json:"k"`
		Reason string `json:"reason"`
	} `json:"undecodable"`
}

type purgeResult struct {
	Requested int `json:"requested"`
	Applied   int `json:"applied"`
	Skipped   []struct {
		K      string `json:"k"`
		Reason string `json:"reason"`
	} `json:"skipped"`
}

// Purge drives the three metastore purge endpoints.
//
// The file written by plan is the response as it arrived, and apply and restore
// post it back unchanged. That is what makes it a backup: the rows are on disk
// before anything is deleted, so a lost response can never leave rows gone with
// no copy of them. Committing a purge is keeping the file; reverting one is
// handing the same file to restore.
type Purge struct {
	client *client.ActionbaseClient
}

func NewPurge(c *client.ActionbaseClient) *Purge {
	return &Purge{client: c}
}

func (p *Purge) Execute(args []string) *model.Response {
	if len(args) < 1 {
		return model.Fail(fmt.Sprintf("Usage: %s", p.GetType().GetCommand()))
	}
	parser := util.ParseArgs(args[1:])

	switch args[0] {
	case "plan":
		return p.plan(parser)
	case "show":
		return p.show(fileArg(parser))
	case "apply":
		return p.send(executePath, "delete", parser)
	case "restore":
		return p.send(restorePath, "restore", parser)
	default:
		return model.Fail(fmt.Sprintf("Usage: %s", p.GetType().GetCommand()))
	}
}

func (p *Purge) plan(parser *util.Parser) *model.Response {
	metastore, ok := parser.Get("metastore")
	if !ok {
		return model.Fail("--metastore is required: it names a metastore configured on the control plane")
	}
	service, ok := parser.Get("service")
	if !ok {
		return model.Fail("--service is required: a purge is always scoped to one service")
	}

	status, body := p.client.PostRaw(candidatesPath, map[string]any{
		"metastore":     metastore,
		"service":       service,
		"olderThanDays": intArg(parser, "older-than", defaultOlderThan),
		"maxRows":       intArg(parser, "max", defaultMaxRows),
	})
	if status != httpOK {
		return model.Fail(fmt.Sprintf("candidates failed (%d): %s", status, body))
	}

	set, err := parseSet([]byte(body))
	if err != nil {
		return model.Fail(err.Error())
	}

	out := fileArg(parser)
	if err := os.WriteFile(out, []byte(body), purgeFilePerm); err != nil {
		return model.Fail(fmt.Sprintf("failed to write %s: %v", out, err))
	}
	return model.SuccessWithResult(summarise(set, out))
}

func (p *Purge) show(path string) *model.Response {
	set, _, err := read(path)
	if err != nil {
		return model.Fail(err.Error())
	}
	return model.SuccessWithResult(summarise(set, path))
}

// send posts a saved document back, unchanged.
//
// --yes is required rather than prompting: the console runs inside a readline
// loop, and a confirmation competing with it for stdin is worse than one the
// operator types deliberately. Without it this prints what would happen.
func (p *Purge) send(path, verb string, parser *util.Parser) *model.Response {
	file := fileArg(parser)
	set, body, err := read(file)
	if err != nil {
		return model.Fail(err.Error())
	}

	if _, confirmed := parser.GetLenient("yes"); !confirmed {
		return model.Fail(fmt.Sprintf(
			"%s\nThis would %s %d rows. Re-run with --yes to go ahead.",
			summarise(set, file), verb, len(set.Rows),
		))
	}

	status, response := p.client.PostRaw(path, json.RawMessage(body))
	if status != httpOK {
		return model.Fail(fmt.Sprintf("%s failed (%d): %s", verb, status, response))
	}

	var result purgeResult
	if err := json.Unmarshal([]byte(response), &result); err != nil {
		return model.Fail(fmt.Sprintf("could not read the response: %v", err))
	}

	out := fmt.Sprintf("%s: %d of %d rows", verb, result.Applied, result.Requested)
	for _, skipped := range result.Skipped {
		out += fmt.Sprintf("\n  skipped %s (%s)", skipped.K, skipped.Reason)
	}
	if verb == "delete" && result.Applied > 0 {
		out += fmt.Sprintf("\nKeep %s: it is the only copy of what was deleted.", file)
	}
	return model.SuccessWithResult(out)
}

func read(path string) (*purgeSet, []byte, error) {
	body, err := os.ReadFile(path)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to read %s: %w", path, err)
	}
	set, err := parseSet(body)
	if err != nil {
		return nil, nil, err
	}
	return set, body, nil
}

func summarise(set *purgeSet, path string) string {
	out := fmt.Sprintf(
		"%s\n  metastore  %s\n  table      %s\n  service    %s\n  generated  %s\n  scanned    %d\n  rows       %d",
		path, set.Metastore, set.Table, set.Service, set.GeneratedAt, set.Scanned, len(set.Rows),
	)
	if len(set.Undecodable) > 0 {
		out += fmt.Sprintf("\n  unreadable %d (reported, never deleted)", len(set.Undecodable))
	}
	if set.NextCursor != nil {
		out += fmt.Sprintf("\n  more       yes, resume from cursor %d", *set.NextCursor)
	}
	return out
}

func parseSet(body []byte) (*purgeSet, error) {
	var set purgeSet
	if err := json.Unmarshal(body, &set); err != nil {
		return nil, fmt.Errorf("not a purge document: %w", err)
	}
	if set.Metastore == "" {
		return nil, fmt.Errorf("not a purge document: no metastore in it")
	}
	return &set, nil
}

func fileArg(parser *util.Parser) string {
	if v, ok := parser.Get("file"); ok {
		return v
	}
	return defaultPurgeFile
}

func intArg(parser *util.Parser, name string, fallback int) int {
	v, ok := parser.Get(name)
	if !ok {
		return fallback
	}
	parsed, err := strconv.Atoi(v)
	if err != nil {
		return fallback
	}
	return parsed
}

func (p *Purge) GetDescription() string {
	return "Purge tombstoned rows from a metastore table"
}

func (p *Purge) GetType() Type {
	return TypePurge
}
