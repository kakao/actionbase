package command

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/util"
)

type Scan struct {
	context          *Context
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

func (s *Scan) Execute(args []string) *model.Response {
	if len(args) < 1 {
		return model.Fail(fmt.Sprintf("Usage: %s", s.GetType().GetCommand()))
	}

	database := s.runner.GetCurrentDatabase()
	if s.runner.GetCurrentDatabase() == "" {
		return model.Fail("No database selected. Use 'use database <name>'")
	}

	parser := util.ParseArgs(args)

	index, found := parser.Get("index")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", s.GetType().GetCommand()))
	}

	start, found := parser.Get("start")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", s.GetType().GetCommand()))
	}

	direction, found := parser.Get("direction")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", s.GetType().GetCommand()))
	}

	limit, found := parser.Get("limit")
	if !found {
		limit = "25"
	}
	ranges, found := parser.Get("ranges")

	if !strings.HasPrefix(args[0], "--") {
		return s.doScan(database, args[0], index, start, direction, limit, ranges)
	}

	currentTable := s.runner.GetCurrentTable()
	if currentTable == "" {
		return model.Fail("No table selected. Use 'use <table|alias> <name>'")
	}

	return s.doScan(database, currentTable, index, start, direction, limit, ranges)
}

func (s *Scan) doScan(database, table, index, start, direction, limit, ranges string) *model.Response {
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
		return model.Fail(fmt.Sprintf("Failed to scan table '%s in %s'", table, database))
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
			"#":          strconv.Itoa(idx + 1),
			"version":    util.ToString(edge.Version),
			"source":     util.ToString(edge.Source),
			"target":     util.ToString(edge.Target),
			"properties": strings.Join(properties, "\n"),
		}

		results = append(results, data)
	}

	offset := responseBody.Offset
	if offset == "" {
		offset = "-"
	}

	columnOrder := []string{"#", "version", "source", "target", "properties"}
	resultMessage := fmt.Sprintf("The %d edges found (offset: %s, hasNext: %t)", responseBody.Count, offset, responseBody.HasNext) +
		"\n" +
		util.PrettyPrintRowsWithOrder(results, columnOrder)

	return model.SuccessWithResult(resultMessage)
}

func (s *Scan) GetDescription() string {
	return "Query 'scan' table"
}

func (s *Scan) GetType() Type {
	return TypeScan
}
