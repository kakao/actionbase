package command

import (
	"fmt"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/util"
)

type Get struct {
	context          *Context
	runner           GetRunner
	actionbaseClient *client.ActionbaseClient
}

type GetRunner interface {
	GetCurrentDatabase() string
	GetCurrentTable() string
	SetCurrentTable(table string)
}

func NewGet(runner GetRunner, actionbaseClient *client.ActionbaseClient) *Get {
	return &Get{runner: runner, actionbaseClient: actionbaseClient}
}

func (g *Get) Execute(args []string) *model.Response {
	if len(args) < 1 {
		return model.Fail(fmt.Sprintf("Usage: %s", g.GetType().GetCommand()))
	}

	database := g.runner.GetCurrentDatabase()
	if database == "" {
		return model.Fail("No database selected. Use 'use database <name>'")
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

	if !strings.HasPrefix(args[0], "--") {
		return g.doExecute(database, args[0], source, target)
	}

	currentTable := g.runner.GetCurrentTable()
	if currentTable == "" {
		return model.Fail("No table selected. Use 'use <table|alias> <name>'")
	}

	return g.doExecute(database, currentTable, source, target)
}

func (g *Get) doExecute(database, table, source, target string) *model.Response {
	response := g.actionbaseClient.Get(
		database,
		table,
		source,
		target)

	if response.IsError() {
		return model.Fail(fmt.Sprintf("Failed to get edge: [%s -> %s]", source, target))
	}

	var results []map[string]interface{}
	for _, edge := range response.Body.Edges {
		property := edge.Properties
		var properties []string
		for key, value := range property {
			keyString := util.ToString(key)
			valueString := util.ToString(value)
			propertyString := keyString + ": " + valueString
			properties = append(properties, propertyString)
		}

		data := map[string]interface{}{
			"version":    util.ToString(edge.Version),
			"source":     util.ToString(edge.Source),
			"target":     util.ToString(edge.Target),
			"properties": strings.Join(properties, "\n"),
		}

		results = append(results, data)
	}

	columnOrder := []string{"version", "source", "target", "properties"}

	if len(results) == 0 {
		emptyEdge := map[string]interface{}{
			"version":    "",
			"source":     "",
			"target":     "",
			"properties": "",
		}

		results = append(results, emptyEdge)
	}

	fmt.Println()
	resultMessage := fmt.Sprintf("The edge is found: [%s -> %s]", source, target) + "\n" + util.PrettyPrintRowsWithOrder(results, columnOrder)
	return model.SuccessWithResult(resultMessage)
}

func (g *Get) GetDescription() string {
	return "Query 'get' to table"
}

func (g *Get) GetType() Type {
	return TypeGet
}
