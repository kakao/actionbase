package command

import (
	"fmt"
	"strconv"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/util"
)

type Count struct {
	*BaseCommand
}

func NewCount(runner TableCommandRunner, actionbaseClient *client.ActionbaseClient) *Count {
	return &Count{
		BaseCommand: &BaseCommand{
			client: actionbaseClient,
			runner: runner,
		},
	}
}

func (c *Count) Execute(args []string) *model.Response {
	if len(args) < 1 {
		return model.Fail(fmt.Sprintf("Usage: %s", c.GetType().GetCommand()))
	}

	database, errResp := ValidateDatabase(c.runner)
	if errResp != nil {
		return errResp
	}

	parser := util.ParseArgs(args)

	start, found := parser.Get("start")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", c.GetType().GetCommand()))
	}

	directionStr, found := parser.Get("direction")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", c.GetType().GetCommand()))
	}

	direction, err := ParseDirection(directionStr)
	if err != nil {
		return model.Fail(err.Error())
	}

	table, errResp := ValidateTable(c.runner, args)
	if errResp != nil {
		return errResp
	}

	return c.doCount(database, table, start, direction.String())
}

func (c *Count) doCount(database string, table string, start string, direction string) *model.Response {
	response := c.client.Counts(database, table, start, direction)

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
	return "Query 'count' table"
}

func (c *Count) GetType() Type {
	return TypeCount
}
