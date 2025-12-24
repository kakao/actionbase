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

func (c *Count) Execute(args []string) *model.Result {
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
		return model.Fail(fmt.Sprintf("Usage: %s", c.GetType().GetCommand()))
	}

	currentTable := c.runner.GetCurrentTable()
	if currentTable == "" {
		return model.Fail("No table selected. Use 'use <table|alias> <name>'")
	}

	return c.doCount(database, currentTable, start, direction)
}

func (c *Count) doCount(database string, table string, start string, direction string) *model.Result {
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
	fmt.Printf("The count of %s edges found\n", util.Int64WithCommas(response.Body.Count))

	columnOrder := []string{"#", "start", "direction", "count"}
	fmt.Println(util.PrettyPrintRowsWithOrder(results, columnOrder))

	return model.Success()
}

func (c *Count) GetDescription() string {
	return "Query 'scan' table"
}

func (c *Count) GetType() Type {
	return TypeCount
}
