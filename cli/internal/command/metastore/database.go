package metastore

import (
	"fmt"
	"strconv"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/client/model"
	model2 "github.com/kakao/actionbase/internal/command/model"
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

func (d *Database) ShowAll() *model2.Result {
	response := d.actionbaseClient.GetDatabases()
	if response.IsError() {
		return model2.Fail("Failed to get databases")
	}

	content := response.Body.Content
	filtered := util.FilterInPlace(content, func(d model.DatabaseEntity) bool {
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

	if len(results) == 0 {
		emptyDatabase := map[string]interface{}{
			"#":      "",
			"name":   "",
			"desc":   "",
			"active": "",
		}
		results = append(results, emptyDatabase)
	}

	fmt.Println()
	fmt.Printf("Available databases (%d)\n", len(results))

	columnOrder := []string{"#", "name", "desc", "active"}
	fmt.Println(util.PrettyPrintRowsWithOrder(results, columnOrder))

	return model2.Success()
}

func (d *Database) Use(name string) *model2.Result {
	response := d.actionbaseClient.GetDatabase(name)
	if response.IsError() {
		return model2.Fail(fmt.Sprintf("No database '%s' found\n", name))
	}

	d.runner.SetCurrentDatabase(name)
	d.runner.SetCurrentTable("")

	fmt.Printf("The database is changed to '%s'\n", name)

	return model2.Success()
}
