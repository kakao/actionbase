package metastore

import (
	"fmt"
	"strconv"

	"github.com/kakao/actionbase/internal/client"
	clientModel "github.com/kakao/actionbase/internal/client/model"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/util"
)

type Database struct {
	runner           DatabaseRunner
	actionbaseClient *client.ActionbaseClient
}

type DatabaseRunner interface {
	SetCurrentDatabase(database string)
	SetCurrentTable(table string)
	GetCurrentDatabase() string
}

func NewDatabase(runner DatabaseRunner, actionbaseClient *client.ActionbaseClient) *Database {
	return &Database{runner: runner, actionbaseClient: actionbaseClient}
}

func (d *Database) ShowAll() *model.Response {
	response := d.actionbaseClient.GetDatabases()
	if response.IsError() {
		return model.Fail("Failed to get databases")
	}

	content := response.Body.Content
	filtered := util.FilterInPlace(content, func(d clientModel.DatabaseEntity) bool {
		return d.Name != "sys"
	})

	var results []map[string]interface{}
	for idx, databaseEntity := range filtered {
		data := map[string]interface{}{
			"#":      strconv.Itoa(idx + 1),
			"name":   databaseEntity.Name,
			"desc":   databaseEntity.Desc,
			"active": databaseEntity.Active,
		}
		results = append(results, data)
	}

	columnOrder := []string{"#", "name", "desc", "active"}
	resultMessage := "\n" +
		fmt.Sprintf("Available databases (%d)\n", len(results)) +
		util.PrettyPrintRowsWithOrder(results, columnOrder)

	return model.SuccessWithResult(resultMessage)
}

func (d *Database) Use(name string) *model.Response {
	response := d.actionbaseClient.GetDatabase(name)
	if response.IsError() {
		return model.Fail(fmt.Sprintf("No database '%s' found", name))
	}

	d.runner.SetCurrentDatabase(name)
	d.runner.SetCurrentTable("")

	return model.SuccessWithResult(fmt.Sprintf("The database is changed to '%s'", name))
}
