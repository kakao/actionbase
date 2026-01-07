package command

import (
	"encoding/json"
	"fmt"
	"strconv"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	clientModel "github.com/kakao/actionbase/internal/client/model"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/util"
)

type Mutate struct {
	*BaseCommand
}

func NewMutate(runner TableCommandRunner, actionbaseClient *client.ActionbaseClient) *Mutate {
	return &Mutate{
		BaseCommand: &BaseCommand{
			client: actionbaseClient,
			runner: runner,
		},
	}
}

func (m *Mutate) Execute(args []string) *model.Response {
	if len(args) < 1 {
		return model.Fail(fmt.Sprintf("Usage: %s", m.GetType().GetCommand()))
	}

	database, errResp := ValidateDatabase(m.runner)
	if errResp != nil {
		return errResp
	}

	parser := util.ParseArgs(args)

	eventType, found := parser.Get("type")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", m.GetType().GetCommand()))
	}

	source, found := parser.Get("source")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", m.GetType().GetCommand()))
	}

	target, found := parser.Get("target")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", m.GetType().GetCommand()))
	}

	version, found := parser.Get("version")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", m.GetType().GetCommand()))
	}
	version = util.ReplaceTimestampInString(version)

	properties, found := parser.Get("properties")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", m.GetType().GetCommand()))
	}

	versionInt, err := strconv.ParseInt(version, 10, 64)
	if err != nil {
		return model.Fail(fmt.Sprintf("Usage: %s", m.GetType().GetCommand()))
	}

	properties = strings.Trim(properties, "'")
	properties = util.ReplaceTimestampInString(properties)
	var propertiesMap map[string]interface{}
	if err := json.Unmarshal([]byte(properties), &propertiesMap); err != nil {
		return model.Fail(fmt.Sprintf("Error parsing properties: %s", err))
	}

	mutationItem := clientModel.MutationItem{
		Type: eventType,
		Edge: clientModel.Edge{Version: versionInt, Source: source, Target: target, Properties: propertiesMap},
	}
	edgeBulkMutation := clientModel.EdgeBulkMutation{
		Mutations: []clientModel.MutationItem{mutationItem},
	}

	table, errResp := ValidateTable(m.runner, args)
	if errResp != nil {
		return errResp
	}

	return m.doMutate(database, table, edgeBulkMutation, eventType)
}

func (m *Mutate) doMutate(database, table string, edgeBulkMutation clientModel.EdgeBulkMutation, eventType string) *model.Response {
	response := m.client.Mutate(database, table, &edgeBulkMutation)
	if response.IsError() {
		return model.Fail(fmt.Sprintf("Failed to mutate edges: %s", response.Error.Error()))
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

	return model.SuccessWithResult(fmt.Sprintf("%s is done (updated: %d, failed %d)", eventType, updatedCount, failedCount))
}

func (m *Mutate) GetDescription() string {
	return "Query 'mutate' Edge"
}

func (m *Mutate) GetType() Type {
	return TypeMutate
}
