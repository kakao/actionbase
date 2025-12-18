package client

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/kakao/actionbase/internal/client/model"
)

type ActionbaseClient struct {
	client *HTTPClient
}

func NewActionbaseClient(client *HTTPClient) *ActionbaseClient {
	return &ActionbaseClient{
		client: client,
	}
}

func (a *ActionbaseClient) CreateStorage(name string, requestBody *model.StorageCreateRequest) *model.DdlStatus[model.StorageEntity] {
	clientResponse := Post(fmt.Sprintf("/graph/v2/storage/%s", name), requestBody, a.client)

	if clientResponse.Error != nil {
		fmt.Printf("Failed to create storage '%s': %s\n", name, clientResponse.Error.Error())
		return nil
	}

	var ddlStatus model.DdlStatus[model.StorageEntity]
	if err := json.Unmarshal([]byte(clientResponse.Body), &clientResponse); err != nil {
		fmt.Printf("Failed to parse clientResponse: %s\n", err.Error())
		return nil
	}

	if ddlStatus.Status == "ERROR" {
		fmt.Printf("Failed to create storage '%s': %s\n", name, *ddlStatus.Message)
		return nil
	}

	return &ddlStatus
}

func (a *ActionbaseClient) CreateDatabase(name string, requestBody *model.DatabaseCreateRequest) *model.DdlStatus[model.DatabaseEntity] {
	clientResponse := Post(fmt.Sprintf("/graph/v2/service/%s", name), requestBody, a.client)
	if clientResponse.Error != nil {
		fmt.Printf("Failed to create database '%s': %s\n", name, clientResponse.Error.Error())
		return nil
	}

	var ddlStatus model.DdlStatus[model.DatabaseEntity]
	if err := json.Unmarshal([]byte(clientResponse.Body), &clientResponse); err != nil {
		fmt.Printf("Failed to parse clientResponse: %s\n", err.Error())
		return nil
	}

	if ddlStatus.Status == "ERROR" {
		fmt.Printf("Failed to create database '%s': %s\n", name, *ddlStatus.Message)
		return nil
	}

	return &ddlStatus
}

func (a *ActionbaseClient) CreateTable(
	database string,
	name string,
	request *model.TableCreateRequest,
) *model.DdlStatus[model.TableEntity] {
	clientResponse := Post(fmt.Sprintf("/graph/v2/service/%s/label/%s", database, name), request, a.client)

	if clientResponse.Error != nil {
		fmt.Printf("Failed to create table '%s': %s\n", name, clientResponse.Error.Error())
		return nil
	}

	var ddlStatus model.DdlStatus[model.TableEntity]
	if err := json.Unmarshal([]byte(clientResponse.Body), &clientResponse); err != nil {
		fmt.Printf("Failed to parse clientResponse: %s\n", err.Error())
		return nil
	}

	if ddlStatus.Status == "ERROR" {
		fmt.Printf("Failed to create table '%s': %s\n", name, *ddlStatus.Message)
		return nil
	}

	return &ddlStatus
}

func (a *ActionbaseClient) CreateAlias(database string, table string, name string, comment interface{}) *model.DdlStatus[model.AliasEntity] {
	requestBody := map[string]interface{}{
		"target": fmt.Sprintf("%s.%s", database, table),
		"desc":   comment,
	}

	clientResponse := Post(fmt.Sprintf("/graph/v2/service/%s/alias/%s", database, name), requestBody, a.client)

	if clientResponse.Error != nil {
		fmt.Printf("Failed to create alias '%s': %s\n", name, clientResponse.Error.Error())
		return nil
	}

	var ddlStatus model.DdlStatus[model.AliasEntity]
	if err := json.Unmarshal([]byte(clientResponse.Body), &ddlStatus); err != nil {
		fmt.Printf("Failed to parse clientResponse: %s\n", err.Error())
		return nil
	}

	if ddlStatus.Status == "ERROR" {
		fmt.Printf("Failed to create alias '%s': %s\n", name, *ddlStatus.Message)
		return nil
	}

	return &ddlStatus
}

func (a *ActionbaseClient) GetTenant() *Response {
	return a.client.Get(fmt.Sprintf("/graph/v3"))
}

func (a *ActionbaseClient) GetDatabases() *model.DdlPage[model.DatabaseEntity] {
	clientResponse := a.client.Get(fmt.Sprintf("/graph/v2/service"))
	if clientResponse.Error != nil {
		fmt.Printf("Failed to get databases: %s\n", clientResponse.Error.Error())
		return nil
	}

	var ddlPage model.DdlPage[model.DatabaseEntity]
	if err := json.Unmarshal([]byte(clientResponse.Body), &ddlPage); err != nil {
		fmt.Printf("Failed to parse responseBody: %s\n", err.Error())
		return nil
	}

	return &ddlPage
}

func (a *ActionbaseClient) GetDatabase(name string) *model.DatabaseEntity {
	clientResponse := a.client.Get(fmt.Sprintf("/graph/v2/service/%s", name))
	if clientResponse.Error != nil {
		fmt.Printf("Failed to get database: %s\n", clientResponse.Error.Error())
		return nil
	}

	var database model.DatabaseEntity
	if err := json.Unmarshal([]byte(clientResponse.Body), &database); err != nil {
		fmt.Printf("Failed to parse responseBody: %s\n", err.Error())
	}

	return &database
}

func (a *ActionbaseClient) GetStorages() *model.DdlPage[model.StorageEntity] {
	clientResponse := a.client.Get(fmt.Sprintf("/graph/v2/storage"))
	if clientResponse.Error != nil {
		fmt.Printf("Failed to get storages: %s\n", clientResponse.Error.Error())
		return nil
	}

	var ddlPage model.DdlPage[model.StorageEntity]
	if err := json.Unmarshal([]byte(clientResponse.Body), &ddlPage); err != nil {
		fmt.Printf("Failed to parse responseBody: %s\n", err.Error())
		return nil
	}

	return &ddlPage
}

func (a *ActionbaseClient) GetTables(database string) *model.DdlPage[model.TableEntity] {
	clientResponse := a.client.Get(fmt.Sprintf("/graph/v2/service/%s/label", database))
	if clientResponse.Error != nil {
		fmt.Printf("Failed to get tables: %s\n", clientResponse.Error.Error())
		return nil
	}

	var ddlPage model.DdlPage[model.TableEntity]
	if err := json.Unmarshal([]byte(clientResponse.Body), &ddlPage); err != nil {
		fmt.Printf("Failed to parse responseBody: %s\n", err.Error())
		return nil
	}

	return &ddlPage
}

func (a *ActionbaseClient) GetTable(database, table string) *model.TableEntity {
	clientResponse := a.client.Get(fmt.Sprintf("/graph/v2/service/%s/label/%s", database, table))
	if clientResponse.Error != nil {
		fmt.Printf("Failed to get table: %s\n", clientResponse.Error.Error())
		return nil
	}

	var tableEntity model.TableEntity
	if err := json.Unmarshal([]byte(clientResponse.Body), &tableEntity); err != nil {
		fmt.Printf("Failed to parse responseBody: %s\n", err.Error())
	}

	return &tableEntity
}

func (a *ActionbaseClient) GetAliases(database string) *model.DdlPage[model.AliasEntity] {
	clientResponse := a.client.Get(fmt.Sprintf("/graph/v2/service/%s/alias", database))
	if clientResponse.Error != nil {
		fmt.Printf("Failed to get aliases: %s\n", clientResponse.Error.Error())
		return nil
	}

	var ddlPage model.DdlPage[model.AliasEntity]
	if err := json.Unmarshal([]byte(clientResponse.Body), &ddlPage); err != nil {
		fmt.Printf("Failed to parse responseBody: %s\n", err.Error())
		return nil
	}

	return &ddlPage
}

func (a *ActionbaseClient) GetAlias(database, name string) *model.AliasEntity {
	clientResponse := a.client.Get(fmt.Sprintf("/graph/v2/service/%s/alias/%s",
		database,
		name))
	if clientResponse.Error != nil {
		fmt.Printf("Failed to get alias: %s\n", clientResponse.Error.Error())
		return nil
	}

	var alias model.AliasEntity
	if err := json.Unmarshal([]byte(clientResponse.Body), &alias); err != nil {
		fmt.Printf("Failed to parse responseBody: %s\n", err.Error())
	}

	return &alias
}

func (a *ActionbaseClient) Get(
	database, table, source, target string) *model.Get {
	clientResponse := a.client.Get(
		fmt.Sprintf("/graph/v3/databases/%s/tables/%s/edges/get?source=%s&target=%s",
			database,
			table,
			source,
			target),
	)

	if clientResponse.Error != nil {
		fmt.Printf("Failed to call get: %s\n", clientResponse.Error.Error())
		return nil
	}

	var get model.Get
	if err := json.Unmarshal([]byte(clientResponse.Body), &get); err != nil {
		fmt.Printf("Failed to parse responseBody: %s\n", err.Error())
		return nil
	}

	return &get
}

func (a *ActionbaseClient) Counts(
	database, table, start, direction string) *model.Counts {
	clientResponse := a.client.Get(
		fmt.Sprintf("/graph/v3/databases/%s/tables/%s/edges/counts?start=%s&direction=%s",
			database,
			table,
			start,
			direction),
	)

	if clientResponse.Error != nil {
		fmt.Printf("Failed to call counts: %s\n", clientResponse.Error.Error())
		return nil
	}

	var counts model.Counts
	if err := json.Unmarshal([]byte(clientResponse.Body), &counts); err != nil {
		fmt.Printf("Failed to parse responseBody: %s\n", err.Error())
		return nil
	}

	return &counts
}

func (a *ActionbaseClient) Scan(
	database, table, index, start, direction, limit string, ranges *string,
) *model.Scan {
	var uriBuilder strings.Builder
	uriBuilder.WriteString(
		fmt.Sprintf("/graph/v3/databases/%s/tables/%s/edges/scan/%s?start=%s&direction=%s&limit=%s",
			database,
			table,
			index,
			start,
			direction,
			limit),
	)

	if ranges != nil && *ranges != "" {
		uriBuilder.WriteString(fmt.Sprintf("&ranges=%s", *ranges))
	}

	clientResponse := a.client.Get(uriBuilder.String())

	if clientResponse.Error != nil {
		fmt.Printf("Failed to call scan: %s\n", clientResponse.Error.Error())
		return nil
	}

	var scan model.Scan
	if err := json.Unmarshal([]byte(clientResponse.Body), &scan); err != nil {
		fmt.Printf("Failed to parse responseBody: %s\n", err.Error())
		return nil
	}

	return &scan
}

func (a *ActionbaseClient) Mutate(
	database string,
	table string,
	request *model.EdgeBulkMutation,
) *model.Mutation {
	clientResponse := Post(
		fmt.Sprintf("/graph/v3/databases/%s/tables/%s/edges", database, table),
		request,
		a.client,
	)
	if clientResponse.Error != nil {
		fmt.Printf("Failed to mutate edge: %s\n", clientResponse.Error.Error())
		return nil
	}

	var mutation model.Mutation
	if err := json.Unmarshal([]byte(clientResponse.Body), &mutation); err != nil {
		fmt.Printf("Failed to parse responseBody: %s\n", err.Error())
		return nil
	}

	return &mutation
}

func (a *ActionbaseClient) GetHost() string {
	return a.client.baseUrl
}
