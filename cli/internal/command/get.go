package command

import (
	"fmt"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/util"
)

type Get struct {
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

func (g *Get) Execute(args []string) {
	if len(args) < 1 {
		fmt.Printf("Usage: %s\n", g.GetType().GetCommand())
		return
	}

	database := g.runner.GetCurrentDatabase()
	if database == "" {
		fmt.Println("No database selected. Use 'use database <name>'")
		return
	}

	parser := util.ParseArgs(args)
	source, found := parser.Get("source")
	if !found {
		fmt.Printf("Usage: %s\n", g.GetType().GetCommand())
		return
	}
	target, found := parser.Get("target")
	if !found {
		fmt.Printf("Usage: %s\n", g.GetType().GetCommand())
	}

	table, found := parser.Get("table")
	if found {
		response := g.actionbaseClient.GetTable(database, table)
		if response.IsError() {
			fmt.Printf("No Table '%s' found in %s\n", table, database)
			return
		}
		g.doExecute(database, table, source, target)
		return
	}

	alias, found := parser.Get("alias")
	if found {
		response := g.actionbaseClient.GetAlias(database, alias)
		if response.IsError() {
			fmt.Printf("No Alias '%s' found in %s\n", alias, database)
			return
		}
		g.doExecute(database, alias, source, target)
		return
	}

	currentTable := g.runner.GetCurrentTable()
	if currentTable == "" {
		fmt.Println("No table selected. Use 'use <table|alias> <name>'")
		return
	}

	g.doExecute(database, currentTable, source, target)
}

func (g *Get) doExecute(database, table, source, target string) {
	response := g.actionbaseClient.Get(
		database,
		table,
		source,
		target)

	if response.IsError() {
		fmt.Printf("Failed to get edge: [%s -> %s]\n", source, target)
		return
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
	fmt.Printf("The edge is found: [%s -> %s]\n", source, target)
	fmt.Println(util.PrettyPrintRowsWithOrder(results, columnOrder))
}

func (g *Get) GetDescription() string {
	return "Query 'get' to table"
}

func (g *Get) GetType() Type {
	return TypeGet
}
