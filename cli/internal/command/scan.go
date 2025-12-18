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

	if s.runner.GetCurrentDatabase() == "" {
		fmt.Println("No database selected. Use 'use database <name>'")
		return
	}

	if s.runner.GetCurrentTable() == "" {
		fmt.Println("No table selected. Use 'use <table|alias> <name>'")
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

	response := s.actionbaseClient.Scan(
		s.runner.GetCurrentDatabase(),
		s.runner.GetCurrentTable(),
		index,
		start,
		direction,
		limit,
		&ranges,
	)

	if response == nil {
		return
	}

	var results []map[string]interface{}
	for idx, edge := range response.Edges {
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

	columnOrder := []string{"#", "version", "source", "target", "properties"}

	if len(results) > 0 {
		fmt.Printf("The %d edges found (offset: %s, hasNext: %t)\n", response.Count, response.Offset, response.HasNext)
		fmt.Println(util.PrettyPrintRowsWithOrder(results, columnOrder))
		fmt.Println()
		return
	}

	emptyEdge := map[string]interface{}{
		"#":          "",
		"version":    "",
		"source":     "",
		"target":     "",
		"properties": "",
	}

	fmt.Println("No results found")
	fmt.Println(util.PrettyPrintWithOrder(emptyEdge, columnOrder))
	fmt.Println()
}

func (s *Scan) GetDescription() string {
	return "Query 'scan' table"
}

func (s *Scan) GetType() Type {
	return TypeScan
}
