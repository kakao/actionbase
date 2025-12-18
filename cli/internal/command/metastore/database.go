package metastore

import (
	"fmt"
	"strconv"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/client/model"
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

func (d *Database) ShowAll() {
	response := d.actionbaseClient.GetDatabases()
	if response == nil {
		return
	}
	content := response.Content
	filtered := util.FilterInPlace(content, func(d model.DatabaseEntity) bool {
		return d.Name != "sys"
	})

	var results []map[string]interface{}
	for idx, databaseEntity := range filtered {
		data := map[string]interface{}{
			"#":      "[" + strconv.Itoa(idx) + "]",
			"name":   databaseEntity.Name,
			"desc":   databaseEntity.Desc,
			"active": databaseEntity.Active,
		}
		results = append(results, data)
	}

	columnOrder := []string{"#", "name", "desc", "active"}

	if len(results) > 0 {
		fmt.Printf("Available databases (%d)\n", len(results))
		fmt.Println(util.PrettyPrintRowsWithOrder(results, columnOrder))
		fmt.Println()
	} else {
		fmt.Println("No available databases found")
	}
}

func (d *Database) Use(name string) bool {
	response := d.actionbaseClient.GetDatabase(name)
	if response == nil {
		return false
	}

	d.runner.SetCurrentDatabase(name)
	d.runner.SetCurrentTable("")

	fmt.Printf("The database is changed to '%s'\n", name)

	return true
}
