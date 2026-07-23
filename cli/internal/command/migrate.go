package command

import (
	"encoding/json"
	"fmt"
	"os"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	clientModel "github.com/kakao/actionbase/internal/client/model"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/util"
)

const (
	defaultPlanFile    = "migration-run.json"
	datastoreURIPrefix = "datastore://"
	jdbcNamespace      = "__jdbc__"
	localNamespace     = "__local__"
)

// migrationEntry is one POST the apply phase will replay. It mirrors the JSON
// shape of the previous dev/migration-run.py plan: a path, a request body, and
// a human-readable label for progress output.
type migrationEntry struct {
	Path  string         `json:"path"`
	Body  map[string]any `json:"body"`
	Label string         `json:"label"`
}

type Migrate struct {
	client *client.ActionbaseClient
}

func NewMigrate(c *client.ActionbaseClient) *Migrate {
	return &Migrate{client: c}
}

func (m *Migrate) Execute(args []string) *model.Response {
	if len(args) < 1 {
		return model.Fail(fmt.Sprintf("Usage: %s", m.GetType().GetCommand()))
	}
	parser := util.ParseArgs(args[1:])
	switch args[0] {
	case "plan":
		out := defaultPlanFile
		if v, ok := parser.Get("o"); ok {
			out = v
		}
		return m.plan(out)
	case "apply":
		in := defaultPlanFile
		if v, ok := parser.Get("i"); ok {
			in = v
		}
		return m.apply(in)
	default:
		return model.Fail(fmt.Sprintf("Usage: %s", m.GetType().GetCommand()))
	}
}

// plan reads all operational metadata and writes a migration plan to disk.
// Storage entities are not re-created (the v2 Storage model is being removed);
// instead each label's storage reference is rewritten to a datastore:// URI so
// the plan stays valid against the new metadata API.
func (m *Migrate) plan(outputPath string) *model.Response {
	dbs := m.client.GetDatabases()
	if dbs.IsError() {
		return model.Fail("Failed to fetch databases")
	}

	// Read storages first: label storage references are stored as storage names,
	// and we need each storage's conf to resolve the datastore:// URI.
	storageURIByName := map[string]string{}
	if resp := m.client.GetStorages(); !resp.IsError() {
		for _, s := range resp.Body.Content {
			storageURIByName[s.Name] = storageToDatastoreURI(s)
		}
	}

	var plan []migrationEntry
	add := func(path string, body map[string]any, label string) {
		plan = append(plan, migrationEntry{Path: path, Body: body, Label: label})
	}

	databases, tables, aliases := 0, 0, 0
	for _, db := range dbs.Body.Content {
		if isSystem(db.Name) {
			continue
		}
		databases++
		add(fmt.Sprintf("/graph/v2/service/%s", db.Name),
			map[string]any{"desc": db.Desc},
			fmt.Sprintf("service/%s", db.Name))

		if resp := m.client.GetTables(db.Name); !resp.IsError() {
			for _, t := range resp.Body.Content {
				if isSystem(t.Name) {
					continue
				}
				short := shortName(t.Name)
				detail := m.client.GetTable(db.Name, short)
				if detail.IsError() {
					return model.Fail(fmt.Sprintf("Failed to fetch table %s.%s", db.Name, short))
				}
				tables++
				add(fmt.Sprintf("/graph/v2/service/%s/label/%s", db.Name, short),
					tableBody(*detail.Body, storageURIByName),
					fmt.Sprintf("label/%s/%s", db.Name, short))
			}
		}

		if resp := m.client.GetAliases(db.Name); !resp.IsError() {
			for _, a := range resp.Body.Content {
				if isSystem(a.Name) {
					continue
				}
				short := shortName(a.Name)
				aliases++
				add(fmt.Sprintf("/graph/v2/service/%s/alias/%s", db.Name, short),
					map[string]any{"desc": a.Desc, "target": a.Target},
					fmt.Sprintf("alias/%s/%s", db.Name, short))
			}
		}
	}

	data, err := json.MarshalIndent(plan, "", "  ")
	if err != nil {
		return model.Fail(fmt.Sprintf("Failed to encode plan: %v", err))
	}
	if err := os.WriteFile(outputPath, data, 0o644); err != nil {
		return model.Fail(fmt.Sprintf("Failed to write %s: %v", outputPath, err))
	}

	return model.SuccessWithResult(fmt.Sprintf(
		"Wrote %s (%d entries: %d database(s), %d table(s), %d alias(es)). Review it, then run: migrate apply -i %s",
		outputPath, len(plan), databases, tables, aliases, outputPath))
}

// apply replays each entry in the plan. Existing entries (409) are skipped so
// the run is idempotent.
func (m *Migrate) apply(inputPath string) *model.Response {
	data, err := os.ReadFile(inputPath)
	if err != nil {
		return model.Fail(fmt.Sprintf("Failed to read %s: %v", inputPath, err))
	}
	var plan []migrationEntry
	if err := json.Unmarshal(data, &plan); err != nil {
		return model.Fail(fmt.Sprintf("Failed to parse %s: %v", inputPath, err))
	}

	ok, skip, fail := 0, 0, 0
	for _, e := range plan {
		status := m.client.PostRaw(e.Path, e.Body)
		switch {
		case status == 409:
			util.Print("[SKIP] %s (already exists)\n", e.Label)
			skip++
		case status >= 200 && status < 300:
			util.Print("[OK]   %s\n", e.Label)
			ok++
		default:
			util.Print("[FAIL] %s (HTTP %d)\n", e.Label, status)
			fail++
		}
	}

	summary := fmt.Sprintf("done: ok=%d skip=%d fail=%d", ok, skip, fail)
	if fail > 0 {
		return model.Fail(summary)
	}
	return model.SuccessWithResult(summary)
}

// tableBody builds the label create body, rewriting the storage reference to a
// datastore:// URI resolved from the storage-name→URI map.
func tableBody(t clientModel.TableEntity, storageURIByName map[string]string) map[string]any {
	storage := t.Storage
	if resolved, ok := storageURIByName[storage]; ok {
		storage = resolved
	}
	return map[string]any{
		"desc":     t.Desc,
		"type":     t.Type,
		"schema":   t.Schema,
		"dirType":  t.DirType,
		"storage":  storage,
		"indices":  t.Indices,
		"groups":   t.Groups,
		"event":    t.Event,
		"readOnly": t.ReadOnly,
		"mode":     t.Mode,
	}
}

// storageToDatastoreURI maps a legacy storage entity to its datastore:// URI.
func storageToDatastoreURI(s clientModel.StorageEntity) string {
	switch s.Type {
	case "HBASE":
		return fmt.Sprintf("%s%s/%s", datastoreURIPrefix, confString(s.Conf, "namespace"), confString(s.Conf, "tableName"))
	case "JDBC":
		return fmt.Sprintf("%s%s/%s", datastoreURIPrefix, jdbcNamespace, s.Name)
	case "LOCAL":
		return fmt.Sprintf("%s%s/%s", datastoreURIPrefix, localNamespace, s.Name)
	default:
		return s.Name
	}
}

func confString(conf map[string]any, key string) string {
	if v, ok := conf[key]; ok {
		if s, ok := v.(string); ok {
			return s
		}
	}
	return ""
}

// isSystem reports bootstrap seeds that must not be migrated: the sys service
// and any origin-prefixed entity. Mirrors the original migration script rule.
func isSystem(name string) bool {
	return name == "sys" || strings.HasPrefix(name, "origin")
}

func (m *Migrate) GetDescription() string {
	return "Migrate metadata: plan reads all metadata to a file, apply replays it"
}

func (m *Migrate) GetType() Type {
	return TypeMigrate
}
