package command

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/util"
)

type Scan struct {
	runner           ScanRunner
	actionbaseClient *client.ActionbaseClient
}

type ScanRunner interface {
	GetCurrentDatabase() string
	GetCurrentTable() string
	SetCurrentTable(table string)
}

func NewScan(runner ScanRunner, actionbaseClient *client.ActionbaseClient) *Scan {
	return &Scan{runner: runner, actionbaseClient: actionbaseClient}
}

func (s *Scan) Execute(args []string) {
	if len(args) < 1 {
		fmt.Printf("Usage: %s\n", s.GetType().GetCommand())
		return
	}

	database := s.runner.GetCurrentDatabase()
	if s.runner.GetCurrentDatabase() == "" {
		fmt.Println("No database selected. Use 'use database <name>'")
		return
	}

	parser := util.ParseArgs(args)

	index, found := parser.Get("index")
	if !found {
		fmt.Printf("Usage: %s\n", s.GetType().GetCommand())
		return
	}

	start, found := parser.Get("start")
	if !found {
		fmt.Printf("Usage: %s\n", s.GetType().GetCommand())
		return
	}

	direction, found := parser.Get("direction")
	if !found {
		fmt.Printf("Usage: %s\n", s.GetType().GetCommand())
		return
	}

	limit, found := parser.Get("limit")
	if !found {
		limit = "25"
	}
	ranges, found := parser.Get("ranges")

	table, found := parser.Get("table")
	if found {
		response := s.actionbaseClient.GetTable(database, table)
		if response == nil {
			fmt.Printf("No Table '%s' found in %s\n", table, database)
			return
		}
		s.doScan(database, table, index, start, direction, limit, ranges)
		return
	}

	alias, found := parser.Get("alias")
	if found {
		response := s.actionbaseClient.GetAlias(database, alias)
		if response.IsError() {
			fmt.Printf("No Alias '%s' found in %s\n", alias, database)
			return
		}
		s.doScan(database, alias, index, start, direction, limit, ranges)
		return
	}

	currentTable := s.runner.GetCurrentTable()
	if currentTable == "" {
		fmt.Println("No table selected. Use 'use <table|alias> <name>'")
		return
	}

	s.doScan(database, currentTable, index, start, direction, limit, ranges)
}

func (s *Scan) doScan(database, table, index, start, direction, limit, ranges string) {
	response := s.actionbaseClient.Scan(
		database,
		table,
		index,
		start,
		direction,
		limit,
		&ranges,
	)

	if response.IsError() {
		fmt.Printf("Failed to scan table '%s in %s\n", table, database)
		return
	}

	responseBody := response.Body

	var results []map[string]interface{}
	for idx, edge := range responseBody.Edges {
		property := edge.Properties

		var properties []string
		for key, value := range property {
			keyString := util.ToString(key)
			valueString := util.ToString(value)
			propertyString := keyString + ": " + valueString
			properties = append(properties, propertyString)
		}

		data := map[string]interface{}{
			"#":          "[" + strconv.Itoa(idx+1) + "]",
			"version":    util.ToString(edge.Version),
			"source":     util.ToString(edge.Source),
			"target":     util.ToString(edge.Target),
			"properties": strings.Join(properties, "\n"),
		}

		results = append(results, data)
	}

	if len(results) == 0 {
		emptyEdge := map[string]interface{}{
			"#":          "",
			"version":    "",
			"source":     "",
			"target":     "",
			"properties": "",
		}
		results = append(results, emptyEdge)
	}

	fmt.Println()
	fmt.Printf("The %d edges found (offset: %s, hasNext: %t)\n", responseBody.Count, responseBody.Offset, responseBody.HasNext)

	columnOrder := []string{"#", "version", "source", "target", "properties"}
	fmt.Println(util.PrettyPrintRowsWithOrder(results, columnOrder))
}

func (s *Scan) GetDescription() string {
	return "Query 'scan' table"
}

func (s *Scan) GetType() Type {
	return TypeScan
}
