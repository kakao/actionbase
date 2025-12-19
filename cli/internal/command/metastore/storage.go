package metastore

import (
	"encoding/json"
	"fmt"
	"strconv"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/util"
)

type Storage struct {
	runner           DatabaseRunner
	actionbaseClient *client.ActionbaseClient
}

type StorageRunner interface {
	SetCurrentDatabase(database string)
	SetCurrentTable(table string)
	GetCurrentDatabase() string
}

func NewStorage(runner StorageRunner, actionbaseClient *client.ActionbaseClient) *Storage {
	return &Storage{runner: runner, actionbaseClient: actionbaseClient}
}

func (s *Storage) ShowAll() {
	response := s.actionbaseClient.GetStorages()
	if response.IsError() {
		fmt.Println("Failed to get storages")
		return
	}

	content := response.Body.Content
	var results []map[string]interface{}
	for idx, storage := range content {
		conf, err := json.Marshal(storage.Conf)
		if err != nil {
			fmt.Println("Failed to parse conf", err)
			return
		}

		data := map[string]interface{}{
			"#":      "[" + strconv.Itoa(idx) + "]",
			"active": storage.Active,
			"name":   storage.Name,
			"desc":   storage.Desc,
			"type":   storage.Type,
			"conf":   string(conf),
		}
		results = append(results, data)
	}

	if len(results) == 0 {
		emptyStorage := map[string]interface{}{
			"#":      "",
			"active": "",
			"name":   "",
			"desc":   "",
			"type":   "",
			"conf":   "",
		}
		results = append(results, emptyStorage)
	}

	fmt.Println()
	fmt.Printf("Available storages (%d)\n", len(results))

	columnOrder := []string{"#", "active", "name", "desc", "type", "conf"}
	fmt.Println(util.PrettyPrintRowsWithOrder(results, columnOrder))
}
