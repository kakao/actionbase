package metastore

import (
	"encoding/json"
	"fmt"
	"strconv"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/model"
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

func (s *Storage) ShowAll() *model.Response {
	response := s.actionbaseClient.GetStorages()
	if response.IsError() {
		return model.Fail("Failed to get storages")
	}

	content := response.Body.Content
	var results []map[string]interface{}
	for idx, storage := range content {
		conf, err := json.Marshal(storage.Conf)
		if err != nil {
			return model.Fail(fmt.Sprintf("Failed to parse conf: %s", err))
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

	columnOrder := []string{"#", "active", "name", "desc", "type", "conf"}
	resultMessage := "\n" +
		fmt.Sprintf("Available storages (%d)\n", len(results)) +
		util.PrettyPrintRowsWithOrder(results, columnOrder)

	return model.SuccessWithResult(resultMessage)
}
