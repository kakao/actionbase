package command

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"os"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	clientModel "github.com/kakao/actionbase/internal/client/model"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/util"
)

const (
	singleQuote = '\''
	doubleQuote = '"'

	multilineOutCommentStart = "# @out"
	multilineOutCommentEnd   = "#"
	singleLineOutComment     = "# @out:"
)

type Load struct {
	context          *Context
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
	if len(args) != 1 {
		return model.Fail(fmt.Sprintf("Invalid arguments: %s", args))
	}

	path := args[0]
	file, err := os.Open(path)
	if err != nil {
		return model.Fail(err.Error())
	}

	defer func(file *os.File) {
		err := file.Close()
		if err != nil {

		}
	}(file)

	reader := bufio.NewReader(file)

	return l.load(reader, path)
}

func (l *Load) load(reader *bufio.Reader, path string) *model.Response {
	var resultBuffer strings.Builder
	var command strings.Builder
	var multilineOutComment strings.Builder
	isInOutCommentBlock := false

	for {
		line, err := reader.ReadString('\n')
		if err == io.EOF {
			if isInOutCommentBlock {
				decoratedOutComment := fmt.Sprintf("/**\n%s\n*/\n", strings.TrimSpace(multilineOutComment.String()))
				resultBuffer.WriteString(decoratedOutComment)
				fmt.Printf("\033[90m%s\033[0m\n", decoratedOutComment)

				multilineOutComment.Reset()
				isInOutCommentBlock = false
				break
			}
			if command.Len() > 0 {
				chunk := l.normalize(command.String())
				if len(chunk) == 0 {
					break
				}

				result := l.doLoad(chunk)
				if !result.IsSuccess {
					resultMessage := fmt.Sprintf("Failed to doLoad '%s'. Please check your command syntax or system log", path)
					fmt.Printf(resultMessage)
					resultBuffer.WriteString(resultMessage)
					return model.FailWithNoOut(resultBuffer.String())
				}
				resultBuffer.WriteString(*result.Result)
			}
			break
		}
		if err != nil {
			log.Fatal(err)
		}

		trimmedLine := strings.TrimSpace(line)

		if strings.HasPrefix(trimmedLine, singleLineOutComment) {
			outContent := strings.TrimPrefix(trimmedLine, singleLineOutComment)

			decoratedOutComment := fmt.Sprintf("/* %s */\n", strings.TrimSpace(outContent))
			resultBuffer.WriteString(decoratedOutComment)
			fmt.Printf("\033[90m%s\033[0m\n", decoratedOutComment)

			command.Reset()
			continue
		}

		if trimmedLine == multilineOutCommentStart {
			isInOutCommentBlock = true
			multilineOutComment.Reset()
			continue
		}

		if isInOutCommentBlock && trimmedLine == multilineOutCommentEnd {
			decoratedOutComment := fmt.Sprintf("/**\n%s\n*/\n", strings.TrimSpace(multilineOutComment.String()))
			resultBuffer.WriteString(decoratedOutComment)
			fmt.Printf("\033[90m%s\033[0m\n", decoratedOutComment)

			multilineOutComment.Reset()
			isInOutCommentBlock = false
			command.Reset()
			continue
		}

		if isInOutCommentBlock {
			multilineOutComment.WriteString(strings.TrimSuffix(line, "\n"))
			multilineOutComment.WriteString("\n")
			continue
		}

		command.WriteString(line)

		if strings.HasSuffix(strings.TrimSpace(line), ";") {
			chunk := l.normalize(command.String())

			if len(chunk) > 0 {
				result := l.doLoad(chunk)
				if !result.IsSuccess {
					resultMessage := fmt.Sprintf("Failed to doLoad '%s'. Please check your command syntax or system log", path)
					fmt.Println(resultMessage)
					resultBuffer.WriteString(resultMessage)
					return model.FailWithNoOut(resultBuffer.String())
				}
				resultBuffer.WriteString(*result.Result)
			}
			command.Reset()
		}
	}

	return model.SuccessWithResultNoOut(resultBuffer.String())
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

	return model.SuccessWithResult(fmt.Sprintf("%d edges are mutated (total: %d, failed: %d)\n", len(edgeBulkMutations.Mutations), updatedCount, failedCount))
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

func (l *Load) GetDescription() string {
	return "Load resources"
}

func (l *Load) GetType() Type {
	return TypeLoad
}
