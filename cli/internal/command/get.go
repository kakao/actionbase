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

	if g.runner.GetCurrentDatabase() == "" {
		fmt.Println("No database selected. Use 'use database <name>'")
		return
	}

	if g.runner.GetCurrentTable() == "" {
		fmt.Println("No table selected. Use 'use <table|alias> <name>'")
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

	response := g.actionbaseClient.Get(
		g.runner.GetCurrentDatabase(),
		g.runner.GetCurrentTable(),
		source,
		target)

	if response == nil {
		return
	}

	var results []map[string]interface{}
	for _, edge := range response.Edges {
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

	if len(results) > 0 {
		fmt.Printf("The edge is found: [%s -> %s]\n", source, target)
		fmt.Println(util.PrettyPrintRowsWithOrder(results, columnOrder))
		fmt.Println()
		return
	}

	emptyEdge := map[string]interface{}{
		"version":    "",
		"source":     "",
		"target":     "",
		"properties": "",
	}
	fmt.Printf("No results found: [%s -> %s]\n", source, target)
	fmt.Println(util.PrettyPrintWithOrder(emptyEdge, columnOrder))
	fmt.Println()
}

func (g *Get) GetDescription() string {
	return "Query 'get' to table"
}

func (g *Get) GetType() Type {
	return TypeGet
}
