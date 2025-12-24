package command

import (
	"encoding/json"
	"fmt"
	"strconv"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/client/model"
	model2 "github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/util"
)

type Mutate struct {
	context          *Context
	runner           MutateRunner
	actionbaseClient *client.ActionbaseClient
}

type MutateRunner interface {
	GetCurrentDatabase() string
	GetCurrentTable() string
	SetCurrentTable(table string)
}

func NewMutate(runner MutateRunner, actionbaseClient *client.ActionbaseClient) *Mutate {
	return &Mutate{runner: runner, actionbaseClient: actionbaseClient}
}

func (m *Mutate) Execute(args []string) *model2.Result {
	if len(args) < 1 {
		return model2.Fail(fmt.Sprintf("Usage: %s", m.GetType().GetCommand()))
	}

	database := m.runner.GetCurrentDatabase()
	if database == "" {
		return model2.Fail("No database selected. Use 'use database <name>'")
	}

	parser := util.ParseArgs(args)

	eventType, found := parser.Get("type")
	if !found {
		return model2.Fail(fmt.Sprintf("Usage: %s", m.GetType().GetCommand()))
	}

	source, found := parser.Get("source")
	if !found {
		return model2.Fail(fmt.Sprintf("Usage: %s", m.GetType().GetCommand()))
	}

	target, found := parser.Get("target")
	if !found {
		return model2.Fail(fmt.Sprintf("Usage: %s", m.GetType().GetCommand()))
	}

	version, found := parser.Get("version")
	if !found {
		return model2.Fail(fmt.Sprintf("Usage: %s", m.GetType().GetCommand()))
	}
	version = util.ReplaceTimestampInString(version)

	properties, found := parser.Get("properties")
	if !found {
		return model2.Fail(fmt.Sprintf("Usage: %s", m.GetType().GetCommand()))
	}

	versionInt, err := strconv.ParseInt(version, 10, 64)
	if err != nil {
		return model2.Fail(fmt.Sprintf("Usage: %s", m.GetType().GetCommand()))
	}

	properties = strings.Trim(properties, "'")
	properties = util.ReplaceTimestampInString(properties)
	var propertiesMap map[string]interface{}
	if json.Unmarshal([]byte(properties), &propertiesMap) != nil {
		return model2.Fail(fmt.Sprintf("Error parsing properties: %s", err))
	}

	mutationItem := model.MutationItem{
		Type: eventType,
		Edge: model.Edge{Version: versionInt, Source: source, Target: target, Properties: propertiesMap},
	}
	edgeBulkMutation := model.EdgeBulkMutation{
		Mutations: []model.MutationItem{mutationItem},
	}

	if !strings.HasPrefix(args[0], "--") {
		return m.doMutate(database, args[0], edgeBulkMutation, eventType)
	}

	currentTable := m.runner.GetCurrentTable()
	if currentTable == "" {
		return model2.Fail("No table selected. Use 'use <table|alias> <name>'")
	}

	return m.doMutate(database, currentTable, edgeBulkMutation, eventType)
}

func (m *Mutate) doMutate(database, table string, edgeBulkMutation model.EdgeBulkMutation, eventType string) *model2.Result {
	response := m.actionbaseClient.Mutate(database, table, &edgeBulkMutation)
	if response.IsError() {
		return model2.Fail(fmt.Sprintf("Failed to mutate edges: %s", response.Error.Error()))
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

	fmt.Printf("%s is done (updated: %d, failed %d)\n", eventType, updatedCount, failedCount)
	return model2.Success()
}

func (m *Mutate) GetDescription() string {
	return "Query 'mutate' Edge"
}

func (m *Mutate) GetType() Type {
	return TypeMutate
}
