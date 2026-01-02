package command

import (
	"fmt"
	"strconv"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/util"
)

type Scan struct {
	*BaseCommand
}

func NewScan(runner TableCommandRunner, actionbaseClient *client.ActionbaseClient) *Scan {
	return &Scan{
		BaseCommand: &BaseCommand{
			client: actionbaseClient,
			runner: runner,
		},
	}
}

func (s *Scan) Execute(args []string) *model.Response {
	if len(args) < 1 {
		return model.Fail(fmt.Sprintf("Usage: %s", s.GetType().GetCommand()))
	}

	database, errResp := ValidateDatabase(s.runner)
	if errResp != nil {
		return errResp
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

	directionStr, found := parser.Get("direction")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", s.GetType().GetCommand()))
	}

	direction, err := ParseDirection(directionStr)
	if err != nil {
		return model.Fail(err.Error())
	}

	limit, found := parser.Get("limit")
	if !found {
		limit = "25"
	}
	ranges, _ := parser.Get("ranges")

	table, errResp := ValidateTable(s.runner, args)
	if errResp != nil {
		return errResp
	}

	return s.doScan(database, table, index, start, direction.String(), limit, ranges)
}

func (s *Scan) doScan(database, table, index, start, direction, limit, ranges string) *model.Response {
	response := s.client.Scan(
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
		data := map[string]interface{}{
			"#":          strconv.Itoa(idx + 1),
			"version":    util.ToString(edge.Version),
			"source":     util.ToString(edge.Source),
			"target":     util.ToString(edge.Target),
			"properties": FormatEdgeProperties(edge.Properties),
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
