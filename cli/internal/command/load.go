package command

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"gopkg.in/yaml.v3"

	"github.com/kakao/actionbase/internal/client"
	clientModel "github.com/kakao/actionbase/internal/client/model"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/util"
)

type YAMLCommand struct {
	Name        string `yaml:"name"`
	Description string `yaml:"description"`
	Command     string `yaml:"command"`
}

const (
	singleQuote = '\''
	doubleQuote = '"'
)

type Load struct {
	runner           LoadRunner
	actionbaseClient *client.ActionbaseClient
}

type LoadRunner interface {
	GetCurrentDatabase() string
	GetCurrentTable() string
	GetCurrentAlias() string
	SetCurrentDatabase(database string)
	SetCurrentTable(table string)
	SetCurrentAlias(alias string)
}

func NewLoad(runner LoadRunner, actionbaseClient *client.ActionbaseClient) *Load {
	return &Load{
		runner:           runner,
		actionbaseClient: actionbaseClient,
	}
}

func (l *Load) Execute(args []string) *model.Response {
	if len(args) < 2 {
		return model.Fail(fmt.Sprintf("Usage: %s", l.GetType().GetCommand()))
	}

	switch args[0] {
	case "file":
		if len(args) != 2 {
			return model.Fail(fmt.Sprintf("Usage: %s", l.GetType().GetCommand()))
		}

		return l.loadFile(args[1])
	case "preset":
		parser := util.ParseArgs(args)
		refs, _ := parser.GetLenient("ref")
		return l.loadPreset(args[1], refs)
	default:
		return model.Fail(fmt.Sprintf("Usage: %s", l.GetType().GetCommand()))
	}
}

func (l *Load) loadFile(path string) *model.Response {
	if !strings.HasSuffix(path, ".yaml") && !strings.HasSuffix(path, ".yml") {
		return model.Fail(fmt.Sprintf("Unsupported file extension: %s", filepath.Ext(path)))
	}

	return l.loadYAMLFile(path)
}

func (l *Load) loadYAMLFile(path string) *model.Response {
	cwd, err := os.Getwd()
	if err != nil {
		return model.Fail(fmt.Sprintf("failed to get current working directory: %s", err.Error()))
	}

	safeDirAbs, err := filepath.Abs(cwd)
	if err != nil {
		return model.Fail(fmt.Sprintf("failed to resolve safe directory: %s", err.Error()))
	}

	absPath, err := filepath.Abs(filepath.Join(safeDirAbs, path))
	if err != nil {
		return model.Fail(fmt.Sprintf("failed to resolve absolute path: %s", err.Error()))
	}

	rel, err := filepath.Rel(safeDirAbs, absPath)
	if err != nil || rel == ".." || rel == "." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
		return model.Fail("invalid file path")
	}

	data, err := os.ReadFile(absPath)
	if err != nil {
		return model.Fail(err.Error())
	}

	var commands []YAMLCommand
	err = yaml.Unmarshal(data, &commands)
	if err != nil {
		return model.Fail(fmt.Sprintf("Failed to parse YAML: %s", err.Error()))
	}

	var results []string
	for _, cmd := range commands {
		if cmd.Command == "" {
			continue
		}

		if cmd.Description != "" {
			decoratedDescription := fmt.Sprintf("/* %s */\n", cmd.Description)
			results = append(results, decoratedDescription)
			fmt.Printf("\033[90m%s\033[0m", decoratedDescription)
		}

		chunk := l.normalize(cmd.Command)
		if len(chunk) == 0 {
			continue
		}

		result := l.doLoad(chunk)
		if !result.IsSuccess {
			resultMessage := fmt.Sprintf("Failed to execute command. Please check your command syntax or system log")
			fmt.Println(resultMessage)
			results = append(results, resultMessage)
			return model.FailWithNoOut(strings.Join(results, "\n"))
		}
		results = append(results, *result.Result)
	}

	return model.SuccessWithResultNoOut(strings.Join(results, "\n"))
}

func (l *Load) doLoad(data string) *model.Response {
	results := l.parseArgsWithQuotes(data)
	resourceType := results[1]

	parser := util.ParseArgs(results)

	data, found := parser.Get("data")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", l.GetType().GetCommand()))
	}

	data = l.runReservedWords(data)

	switch resourceType {
	case "database":
		return l.loadDatabase(parser, data)
	case "storage":
		return l.loadStorage(parser, data)
	case "table":
		return l.loadTable(parser, data)
	case "edges":
		return l.loadEdge(parser, data)
	default:
		return model.Fail(fmt.Sprintf("Unknown resource type: %s", resourceType))
	}
}

func (l *Load) loadDatabase(parser *util.Parser, data string) *model.Response {
	name, found := parser.Get("name")
	if !found {
		return model.Fail("Failed to read a command when try to create database. no name found")
	}

	var databaseCreateRequest clientModel.DatabaseCreateRequest
	err := json.Unmarshal([]byte(data), &databaseCreateRequest)
	if err != nil {
		return model.Fail(fmt.Sprintf("Failed to parse data: %s", err.Error()))
	}
	response := l.actionbaseClient.CreateDatabase(name, &databaseCreateRequest)

	if response.IsError() || response.Body.Status == "ERROR" {
		return model.Fail(fmt.Sprintf("Failed to create database '%s'", name))
	}

	return model.SuccessWithResult(fmt.Sprintf("Database '%s' is created", name))
}

func (l *Load) loadStorage(parser *util.Parser, data string) *model.Response {
	name, found := parser.Get("name")
	if !found {
		return model.Fail("Failed to read a command when try to create storage. no name found")
	}

	var storageCreateRequest clientModel.StorageCreateRequest
	err := json.Unmarshal([]byte(data), &storageCreateRequest)
	if err != nil {
		return model.Fail(fmt.Sprintf("Failed to parse data: %s", err.Error()))
	}

	response := l.actionbaseClient.CreateStorage(name, &storageCreateRequest)
	if response.IsError() || response.Body.Status == "ERROR" {
		return model.Fail(fmt.Sprintf("Failed to create storage '%s'", name))
	}

	return model.SuccessWithResult(fmt.Sprintf("Storage '%s' is created", name))
}

func (l *Load) loadTable(parser *util.Parser, data string) *model.Response {
	name, found := parser.Get("name")
	if !found {
		return model.Fail("Failed to read a command when try to create table. no name found")
	}

	database, found := parser.Get("database")
	if !found {
		return model.Fail(fmt.Sprintf("Failed to read a command when try to create table. no database found"))
	}

	var tableCreateRequest clientModel.TableCreateRequest
	err := json.Unmarshal([]byte(data), &tableCreateRequest)
	if err != nil {
		return model.Fail(fmt.Sprintf("Failed to parse data: %s", err.Error()))
	}

	response := l.actionbaseClient.CreateTable(database, name, &tableCreateRequest)
	if response.IsError() || response.Body.Status == "ERROR" {
		return model.Fail(fmt.Sprintf("Failed to create table '%s'", name))
	}

	return model.SuccessWithResult(fmt.Sprintf("Table '%s' is created", name))
}

func (l *Load) loadEdge(parser *util.Parser, data string) *model.Response {
	database, found := parser.Get("database")
	if !found {
		return model.Fail("Failed to read a command when try to mutate edges. no --database found")
	}

	table, found := parser.Get("table")
	if !found {
		return model.Fail("Failed to read a command when try to mutate edges. no --table found")
	}

	var edgeBulkMutations clientModel.EdgeBulkMutation
	err := json.Unmarshal([]byte(data), &edgeBulkMutations)
	if err != nil {
		return model.Fail(fmt.Sprintf("Failed to parse data: %s", err.Error()))
	}

	response := l.actionbaseClient.Mutate(database, table, &edgeBulkMutations)
	if response.IsError() {
		return model.Fail(fmt.Sprintf("Failed to mutate edges: %s", err.Error()))
	}

	var updatedCount int32 = 0
	var failedCount int32 = 0
	for _, result := range response.Body.Results {
		if result.Status != "ERROR" {
			updatedCount += result.Count
		} else {
			failedCount += result.Count
		}
	}

	return model.SuccessWithResult(fmt.Sprintf("%d edges of '%s' are mutated (total: %d, failed: %d)", len(edgeBulkMutations.Mutations), table, updatedCount, failedCount))
}

func (l *Load) normalize(chunk string) string {
	chunk = strings.TrimSpace(chunk)
	chunk = strings.TrimSuffix(chunk, ";")
	chunk = strings.ReplaceAll(chunk, "\n", " ")
	return strings.ReplaceAll(chunk, "\r", " ")
}

func (l *Load) runReservedWords(dataStr string) string {
	dataStr = strings.TrimSpace(dataStr)
	if len(dataStr) >= 2 && dataStr[0] == '\'' && dataStr[len(dataStr)-1] == '\'' {
		dataStr = dataStr[1 : len(dataStr)-1]
	}

	return util.ReplaceTimestampInString(dataStr)
}

func (l *Load) parseArgsWithQuotes(line string) []string {
	var args []string
	var current strings.Builder
	inSingleQuote := false
	inDoubleQuote := false
	escaped := false

	for _, r := range line {
		if r == '\\' && !escaped {
			escaped = true
			current.WriteRune(r)
			continue
		}

		if r == singleQuote && !escaped && !inDoubleQuote {
			inSingleQuote = !inSingleQuote
			current.WriteRune(r)
			escaped = false
			continue
		}

		if r == doubleQuote && !escaped && !inSingleQuote {
			inDoubleQuote = !inDoubleQuote
			current.WriteRune(r)
			escaped = false
			continue
		}

		if (r == ' ' || r == '\t') && !inSingleQuote && !inDoubleQuote {
			if current.Len() > 0 {
				args = append(args, current.String())
				current.Reset()
			}
			escaped = false
			continue
		}

		current.WriteRune(r)
		escaped = false
	}

	if current.Len() > 0 {
		args = append(args, current.String())
	}

	return args
}

func (l *Load) loadPreset(filename, refs string) *model.Response {
	if strings.Contains(filename, "/") || strings.Contains(filename, "\\") || strings.Contains(filename, "..") {
		return model.Fail("invalid preset filename")
	}

	cleanedFilename := filepath.Clean(filename) + ".preset.yaml"

	var url string
	if refs != "" {
		url = "https://raw.githubusercontent.com/kakao/actionbase/" + refs + "/examples/presets/" + cleanedFilename
	} else {
		url = "https://github.com/kakao/actionbase/releases/download/examples/" + cleanedFilename
	}

	ok := util.Download(cleanedFilename, url)
	if !ok {
		return model.Fail("Failed to download preset file")
	}

	return l.loadFile(cleanedFilename)
}

func (l *Load) GetDescription() string {
	return "Load resources"
}

func (l *Load) GetType() Type {
	return TypeLoad
}
