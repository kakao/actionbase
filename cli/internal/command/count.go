package command

import (
	"fmt"
	"strconv"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/util"
)

type Count struct {
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

func (c *Count) Execute(args []string) {
	if len(args) < 1 {
		fmt.Printf("Usage: %s\n", c.GetType().GetCommand())
		return
	}

	database := c.runner.GetCurrentDatabase()
	if database == "" {
		fmt.Println("No database selected. Use 'use database <name>'")
		return
	}

	table := c.runner.GetCurrentTable()
	if table == "" {
		fmt.Println("No table selected. Use 'use <table|alias> <name>'")
		return
	}

	parser := util.ParseArgs(args)

	start, found := parser.Get("start")
	if !found {
		fmt.Printf("Usage: %s\n", c.GetType().GetCommand())
		return
	}

	direction, found := parser.Get("direction")
	if !found {
		fmt.Printf("Usage: %s\n", c.GetType().GetCommand())
		return
	}

	response := c.actionbaseClient.Counts(database, table, start, direction)

	if response == nil {
		return
	}

	var results []map[string]interface{}
	for idx, count := range response.Counts {
		data := map[string]interface{}{
			"#":         "[" + strconv.Itoa(idx+1) + "]",
			"start":     util.ToString(count.Start),
			"direction": util.ToString(count.Direction),
			"count":     util.ToString(count.Count),
		}

		results = append(results, data)
	}

	columnOrder := []string{"#", "start", "direction", "count"}

	fmt.Printf("The count of %s edges found\n", util.Int64WithCommas(response.Count))

	fmt.Println(util.PrettyPrintRowsWithOrder(results, columnOrder))
	fmt.Println()
	return
}

func (c *Count) GetDescription() string {
	return "Query 'scan' table"
}

func (c *Count) GetType() Type {
	return TypeCount
}
