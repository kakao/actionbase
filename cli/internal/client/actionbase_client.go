package client

import (
	"fmt"
	"strings"

	"github.com/kakao/actionbase/internal/client/model"
)

type ActionbaseClient struct {
	client  *HTTPClient
	context *Context
}

func NewActionbaseClient(client *HTTPClient, context *Context) *ActionbaseClient {
	return &ActionbaseClient{client: client, context: context}
}

func (a *ActionbaseClient) CreateStorage(name string, requestBody *model.StorageCreateRequest) *model.DdlStatus[model.StorageEntity] {
	var clientResponse = Post[*model.StorageCreateRequest, model.DdlStatus[model.StorageEntity]](a.client, fmt.Sprintf("/graph/v2/storage/%s", name), requestBody)

	if clientResponse.Error != nil {
		return nil
	}

	return clientResponse.Body
}

func (a *ActionbaseClient) CreateDatabase(name string, requestBody *model.DatabaseCreateRequest) *model.DdlStatus[model.DatabaseEntity] {
	clientResponse := Post[*model.DatabaseCreateRequest, model.DdlStatus[model.DatabaseEntity]](a.client, fmt.Sprintf("/graph/v2/service/%s", name), requestBody)
	if clientResponse.Error != nil {
		fmt.Printf("Failed to create database '%s': %s\n", name, clientResponse.Error.Error())
		return nil
	}

	return clientResponse.Body
}

func (a *ActionbaseClient) CreateTable(
	database string,
	name string,
	request *model.TableCreateRequest,
) *model.DdlStatus[model.TableEntity] {
	clientResponse := Post[*model.TableCreateRequest, model.DdlStatus[model.TableEntity]](a.client, fmt.Sprintf("/graph/v2/service/%s/label/%s", database, name), request)

	if clientResponse.Error != nil {
		fmt.Printf("Failed to create table '%s': %s\n", name, clientResponse.Error.Error())
		return nil
	}

	return clientResponse.Body
}

func (a *ActionbaseClient) CreateAlias(database string, table string, name string, comment interface{}) *model.DdlStatus[model.AliasEntity] {
	requestBody := map[string]interface{}{
		"target": fmt.Sprintf("%s.%s", database, table),
		"desc":   comment,
	}

	clientResponse := Post[map[string]interface{}, model.DdlStatus[model.AliasEntity]](a.client, fmt.Sprintf("/graph/v2/service/%s/alias/%s", database, name), requestBody)

	if clientResponse.Error != nil {
		fmt.Printf("Failed to create alias '%s': %s\n", name, clientResponse.Error.Error())
		return nil
	}

	return clientResponse.Body
}

func (a *ActionbaseClient) GetTenant() *model.Tenant {
	clientResponse := Get[model.Tenant](a.client, fmt.Sprintf("/graph/v3"))
	return clientResponse.Body
}

func (a *ActionbaseClient) GetDatabases() *model.DdlPage[model.DatabaseEntity] {
	clientResponse := Get[model.DdlPage[model.DatabaseEntity]](a.client, fmt.Sprintf("/graph/v2/service"))
	if clientResponse.Error != nil {
		return nil
	}

	return clientResponse.Body
}

func (a *ActionbaseClient) GetDatabase(name string) *model.DatabaseEntity {
	clientResponse := Get[model.DatabaseEntity](a.client, fmt.Sprintf("/graph/v2/service/%s", name))
	if clientResponse.Error != nil {
		return nil
	}

	return clientResponse.Body
}

func (a *ActionbaseClient) GetStorages() *model.DdlPage[model.StorageEntity] {
	clientResponse := Get[model.DdlPage[model.StorageEntity]](a.client, fmt.Sprintf("/graph/v2/storage"))
	if clientResponse.Error != nil {
		return nil
	}

	return clientResponse.Body
}

func (a *ActionbaseClient) GetTables(database string) *model.DdlPage[model.TableEntity] {
	clientResponse := Get[model.DdlPage[model.TableEntity]](a.client, fmt.Sprintf("/graph/v2/service/%s/label", database))
	if clientResponse.Error != nil {
		return nil
	}

	return clientResponse.Body
}

func (a *ActionbaseClient) GetTable(database, table string) *model.TableEntity {
	clientResponse := Get[model.TableEntity](a.client, fmt.Sprintf("/graph/v2/service/%s/label/%s", database, table))
	if clientResponse.Error != nil {
		return nil
	}

	return clientResponse.Body
}

func (a *ActionbaseClient) GetAliases(database string) *model.DdlPage[model.AliasEntity] {
	clientResponse := Get[model.DdlPage[model.AliasEntity]](a.client, fmt.Sprintf("/graph/v2/service/%s/alias", database))
	if clientResponse.Error != nil {
		return nil
	}

	return clientResponse.Body
}

func (a *ActionbaseClient) GetAlias(database, name string) *model.AliasEntity {
	clientResponse := Get[model.AliasEntity](a.client, fmt.Sprintf("/graph/v2/service/%s/alias/%s", database, name))
	if clientResponse.Error != nil {
		return nil
	}

	return clientResponse.Body
}

func (a *ActionbaseClient) Get(
	database, table, source, target string) *model.Get {
	clientResponse := Get[model.Get](
		a.client,
		fmt.Sprintf(
			"/graph/v3/databases/%s/tables/%s/edges/get?source=%s&target=%s",
			database,
			table,
			source,
			target),
	)

	if clientResponse.Error != nil {
		return nil
	}

	return clientResponse.Body
}

func (a *ActionbaseClient) Counts(
	database, table, start, direction string) *model.Counts {
	clientResponse := Get[model.Counts](a.client,
		fmt.Sprintf("/graph/v3/databases/%s/tables/%s/edges/counts?start=%s&direction=%s",
			database,
			table,
			start,
			direction),
	)

	if clientResponse.Error != nil {
		return nil
	}

	return clientResponse.Body
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

	clientResponse := Get[model.Scan](a.client, uriBuilder.String())

	if clientResponse.Error != nil {
		return nil
	}

	return clientResponse.Body
}

func (a *ActionbaseClient) Mutate(
	database string,
	table string,
	request *model.EdgeBulkMutation,
) *model.Mutation {
	clientResponse := Post[*model.EdgeBulkMutation, model.Mutation](a.client,
		fmt.Sprintf("/graph/v3/databases/%s/tables/%s/edges", database, table),
		request,
	)
	if clientResponse.Error != nil {
		return nil
	}

	return clientResponse.Body
}

func (a *ActionbaseClient) GetHost() string {
	return a.client.baseUrl
}
