package command

import (
	"encoding/json"
	"fmt"
	"strconv"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/client/model"
	"github.com/kakao/actionbase/internal/util"
)

type Mutate struct {
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

func (m *Mutate) Execute(args []string) {
	if len(args) < 1 {
		fmt.Printf("Usage: %s\n", m.GetType().GetCommand())
		return
	}

	if m.runner.GetCurrentDatabase() == "" {
		fmt.Println("No database selected. Use 'use database <name>'")
		return
	}

	if m.runner.GetCurrentTable() == "" {
		fmt.Println("No table selected. Use 'use <table|alias> <name>'")
		return
	}

	parser := util.ParseArgs(args)

	eventType, found := parser.Get("type")
	if !found {
		fmt.Printf("Usage: %s\n", m.GetType().GetCommand())
		return
	}

	table, found := parser.Get("table")
	if !found {
		fmt.Printf("Usage: %s\n", m.GetType().GetCommand())
		return
	}

	source, found := parser.Get("source")
	if !found {
		fmt.Printf("Usage: %s\n", m.GetType().GetCommand())
		return
	}

	target, found := parser.Get("target")
	if !found {
		fmt.Printf("Usage: %s\n", m.GetType().GetCommand())
		return
	}

	version, found := parser.Get("version")
	if !found {
		fmt.Printf("Usage: %s\n", m.GetType().GetCommand())
		return
	}
	version = util.ReplaceTimestampInString(version)

	properties, found := parser.Get("properties")
	if !found {
		fmt.Printf("Usage: %s\n", m.GetType().GetCommand())
		return
	}

	versionInt, err := strconv.ParseInt(version, 10, 64)
	if err != nil {
		fmt.Println("Error parsing version: ", err)
		return
	}

	properties = strings.Trim(properties, "'")
	properties = util.ReplaceTimestampInString(properties)
	var propertiesMap map[string]interface{}
	if json.Unmarshal([]byte(properties), &propertiesMap) != nil {
		fmt.Println("Error parsing properties: ", err)
		return
	}

	mutationItem := model.MutationItem{
		Type: eventType,
		Edge: model.Edge{Version: versionInt, Source: source, Target: target, Properties: propertiesMap},
	}
	edgeBulkMutation := model.EdgeBulkMutation{
		Mutations: []model.MutationItem{mutationItem},
	}

	response := m.actionbaseClient.Mutate(m.runner.GetCurrentDatabase(), table, &edgeBulkMutation)
	if response == nil {
		return
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

	fmt.Printf("%s is done (updated: %d, failed %d)\n", eventType, updatedCount, failedCount)
	return
}

func (m *Mutate) GetDescription() string {
	return "Query 'mutate' Edge"
}

func (m *Mutate) GetType() Type {
	return TypeMutate
}
