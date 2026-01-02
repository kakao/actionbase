package command

import (
	"fmt"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/util"
)

type Get struct {
	*BaseCommand
}

func NewGet(runner TableCommandRunner, actionbaseClient *client.ActionbaseClient) *Get {
	return &Get{
		BaseCommand: &BaseCommand{
			client: actionbaseClient,
			runner: runner,
		},
	}
}

func (g *Get) Execute(args []string) *model.Response {
	if len(args) < 1 {
		return model.Fail(fmt.Sprintf("Usage: %s", g.GetType().GetCommand()))
	}

	database, errResp := ValidateDatabase(g.runner)
	if errResp != nil {
		return errResp
	}

	parser := util.ParseArgs(args)
	source, found := parser.Get("source")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", g.GetType().GetCommand()))
	}
	target, found := parser.Get("target")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", g.GetType().GetCommand()))
	}

	table, errResp := ValidateTable(g.runner, args)
	if errResp != nil {
		return errResp
	}

	return g.doExecute(database, table, source, target)
}

func (g *Get) doExecute(database, table, source, target string) *model.Response {
	response := g.client.Get(
		database,
		table,
		source,
		target)

	if response.IsError() {
		return model.Fail(fmt.Sprintf("Failed to get edge: [%s -> %s]", source, target))
	}

	var results []map[string]interface{}
	for _, edge := range response.Body.Edges {
		data := map[string]interface{}{
			"version":    util.ToString(edge.Version),
			"source":     util.ToString(edge.Source),
			"target":     util.ToString(edge.Target),
			"properties": FormatEdgeProperties(edge.Properties),
		}

		results = append(results, data)
	}

	columnOrder := []string{"version", "source", "target", "properties"}

	resultMessage := "\n" + fmt.Sprintf("The edge is found: [%s -> %s]", source, target) + "\n" + util.PrettyPrintRowsWithOrder(results, columnOrder)
	return model.SuccessWithResult(resultMessage)
}

func (g *Get) GetDescription() string {
	return "Query 'get' to table"
}

func (g *Get) GetType() Type {
	return TypeGet
}
