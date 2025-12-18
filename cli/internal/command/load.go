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
	"github.com/kakao/actionbase/internal/client/model"
	"github.com/kakao/actionbase/internal/util"
)

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

func (l *Load) Execute(args []string) {
	if len(args) != 1 {
		fmt.Printf("Usage: %s\n", l.GetType().GetCommand())
		return
	}

	file, err := os.Open(args[0])
	if err != nil {
		log.Fatal(err)
	}
	defer func(file *os.File) {
		err := file.Close()
		if err != nil {

		}
	}(file)

	reader := bufio.NewReader(file)

	for {
		chunk, err := reader.ReadString(';')
		chunk = strings.TrimSpace(chunk)
		chunk = strings.TrimSuffix(chunk, ";")
		if err == io.EOF {
			if len(chunk) > 0 {
				clean := strings.ReplaceAll(chunk, "\n", " ")
				clean = strings.ReplaceAll(clean, "\r", " ")
				l.load(clean)
			}
			break
		}
		if err != nil {
			log.Fatal(err)
		}

		clean := strings.ReplaceAll(chunk, "\n", " ")
		clean = strings.ReplaceAll(clean, "\r", " ")
		l.load(clean)
	}
}

func (l *Load) GetDescription() string {
	return "Load resources"
}

func (l *Load) GetType() Type {
	return TypeLoad
}

func (l *Load) load(data string) {
	results := parseArgsWithQuotes(data)
	resourceType := results[1]

	parser := util.ParseArgs(results)

	data, found := parser.Get("data")
	if !found {
		return
	}

	data = runReservedWords(data)

	if resourceType == "database" {
		if !(l.loadDatabase(parser, data)) {
			return
		}
		return
	} else if resourceType == "storage" {
		if l.loadStorage(parser, data) {
			return
		}
		return
	} else if resourceType == "table" {
		if l.loadTable(parser, data) {
			return
		}
		return
	} else if resourceType == "edges" {
		l.loadEdge(parser, data)
		return
	}
}

func (l *Load) loadDatabase(parser *util.Parser, data string) bool {
	name, found := parser.Get("name")
	if !found {
		fmt.Println("Failed to read a command when try to create database. no name found")
		return false
	}

	var databaseCreateRequest model.DatabaseCreateRequest
	err := json.Unmarshal([]byte(data), &databaseCreateRequest)
	if err != nil {
		fmt.Printf("Failed to parse data: %s\n", err.Error())
		return false
	}
	response := l.actionbaseClient.CreateDatabase(name, &databaseCreateRequest)
	if response == nil {
		return false
	}

	fmt.Printf("Database '%s' is created\n", name)
	return true
}

func (l *Load) loadStorage(parser *util.Parser, data string) bool {
	name, found := parser.Get("name")
	if !found {
		fmt.Println("Failed to read a command when try to create storage. no name found")
		return true
	}

	var storageCreateRequest model.StorageCreateRequest
	err := json.Unmarshal([]byte(data), &storageCreateRequest)
	if err != nil {
		fmt.Printf("Failed to parse data: %s\n", err.Error())
		return false
	}

	response := l.actionbaseClient.CreateStorage(name, &storageCreateRequest)
	if response == nil {
		return true
	}

	fmt.Printf("Storage '%s' is created\n", name)
	return false
}

func (l *Load) loadTable(parser *util.Parser, data string) bool {
	name, found := parser.Get("name")
	if !found {
		fmt.Println("Failed to read a command when try to create table. no name found")
		return true
	}

	database, found := parser.Get("database")
	if !found {
		fmt.Println("Failed to read a command when try to create table. no database found")
		return true
	}

	var tableCreateRequest model.TableCreateRequest
	err := json.Unmarshal([]byte(data), &tableCreateRequest)
	if err != nil {
		fmt.Printf("Failed to parse data: %s\n", err.Error())
		return false
	}

	response := l.actionbaseClient.CreateTable(database, name, &tableCreateRequest)
	if response == nil {
		return true
	}

	fmt.Printf("Table '%s' is created\n", name)
	return false
}

func (l *Load) loadEdge(parser *util.Parser, data string) bool {
	database, found := parser.Get("database")
	if !found {
		fmt.Println("Failed to read a command when try to mutate edges. no --database found")
		return false
	}

	table, found := parser.Get("table")
	if !found {
		fmt.Println("Failed to read a command when try to mutate edges. no --table found")
		return false
	}

	var edgeBulkMutations model.EdgeBulkMutation
	err := json.Unmarshal([]byte(data), &edgeBulkMutations)
	if err != nil {
		fmt.Printf("Failed to parse data: %s\n", err.Error())
		return false
	}

	response := l.actionbaseClient.Mutate(database, table, &edgeBulkMutations)
	if response == nil {
		return false
	}

	var updatedCount int32 = 0
	var failedCount int32 = 0
	for _, result := range response.Results {
		if result.Status != "ERROR" {
			updatedCount += result.Count
		} else {
			failedCount += result.Count
		}
	}

	fmt.Printf("%d edges are mutated (total: %d, failed: %d)\n", len(edgeBulkMutations.Mutations), updatedCount, failedCount)
	return true
}

func runReservedWords(dataStr string) string {
	dataStr = strings.TrimSpace(dataStr)
	if len(dataStr) >= 2 && dataStr[0] == '\'' && dataStr[len(dataStr)-1] == '\'' {
		dataStr = dataStr[1 : len(dataStr)-1]
	}

	dataStr = util.ReplaceTimestampInString(dataStr)
	return dataStr
}

func parseArgsWithQuotes(line string) []string {
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

	// Add the last argument if any
	if current.Len() > 0 {
		args = append(args, current.String())
	}

	return args
}
