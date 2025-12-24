package command

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/util"
)

type Count struct {
	context          *Context
	runner           CountRunner
	actionbaseClient *client.ActionbaseClient
}

type CountRunner interface {
	GetCurrentDatabase() string
	GetCurrentTable() string
	GetCurrentAlias() string
	SetCurrentTable(table string)
}

func NewCount(runner CountRunner, actionbaseClient *client.ActionbaseClient) *Count {
	return &Count{runner: runner, actionbaseClient: actionbaseClient}
}

func (c *Count) Execute(args []string) *model.Response {
	if len(args) < 1 {
		return model.Fail(fmt.Sprintf("Usage: %s", c.GetType().GetCommand()))
	}

	database := c.runner.GetCurrentDatabase()
	if database == "" {
		return model.Fail("No database selected. Use 'use database <name>'")
	}

	parser := util.ParseArgs(args)

	start, found := parser.Get("start")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", c.GetType().GetCommand()))
	}

	direction, found := parser.Get("direction")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", c.GetType().GetCommand()))
	}

	if !strings.HasPrefix(args[0], "--") {
		return c.doCount(database, args[0], start, direction)
	}

	currentTable := c.runner.GetCurrentTable()
	if currentTable == "" {
		return model.Fail("No table selected. Use 'use <table|alias> <name>'")
	}

	return c.doCount(database, currentTable, start, direction)
}

func (c *Count) doCount(database string, table string, start string, direction string) *model.Response {
	response := c.actionbaseClient.Counts(database, table, start, direction)

	if response.IsError() {
		return model.Fail(fmt.Sprintf("Failed to get counts of table '%s' in %s", table, database))
	}

	var results []map[string]interface{}
	for idx, count := range response.Body.Counts {
		data := map[string]interface{}{
			"#":         strconv.Itoa(idx + 1),
			"start":     util.ToString(count.Start),
			"direction": util.ToString(count.Direction),
			"count":     util.ToString(count.Count),
		}

		results = append(results, data)
	}

	fmt.Println()

	columnOrder := []string{"#", "start", "direction", "count"}
	resultMessage := fmt.Sprintf("The count of %s edges found", util.Int64WithCommas(response.Body.Count)) +
		"\n" +
		util.PrettyPrintRowsWithOrder(results, columnOrder)

	return model.SuccessWithResult(resultMessage)
}

func (c *Count) GetDescription() string {
	return "Query 'scan' table"
}

func (c *Count) GetType() Type {
	return TypeCount
}
