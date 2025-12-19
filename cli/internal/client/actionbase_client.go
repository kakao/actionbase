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

func (a *ActionbaseClient) CreateStorage(name string, requestBody *model.StorageCreateRequest) *Response[model.DdlStatus[model.StorageEntity]] {
	return Post[*model.StorageCreateRequest, model.DdlStatus[model.StorageEntity]](a.client, fmt.Sprintf("/graph/v2/storage/%s", name), requestBody)
}

func (a *ActionbaseClient) CreateDatabase(name string, requestBody *model.DatabaseCreateRequest) *Response[model.DdlStatus[model.DatabaseEntity]] {
	return Post[*model.DatabaseCreateRequest, model.DdlStatus[model.DatabaseEntity]](a.client, fmt.Sprintf("/graph/v2/service/%s", name), requestBody)
}

func (a *ActionbaseClient) CreateTable(
	database string,
	name string,
	request *model.TableCreateRequest,
) *Response[model.DdlStatus[model.TableEntity]] {
	return Post[*model.TableCreateRequest, model.DdlStatus[model.TableEntity]](a.client, fmt.Sprintf("/graph/v2/service/%s/label/%s", database, name), request)
}

func (a *ActionbaseClient) CreateAlias(database string, table string, name string, comment interface{}) *Response[model.DdlStatus[model.AliasEntity]] {
	requestBody := map[string]interface{}{
		"target": fmt.Sprintf("%s.%s", database, table),
		"desc":   comment,
	}

	return Post[map[string]interface{}, model.DdlStatus[model.AliasEntity]](a.client, fmt.Sprintf("/graph/v2/service/%s/alias/%s", database, name), requestBody)
}

func (a *ActionbaseClient) GetTenant() *Response[model.Tenant] {
	return Get[model.Tenant](a.client, fmt.Sprintf("/graph/v3"))
}

func (a *ActionbaseClient) GetDatabases() *Response[model.DdlPage[model.DatabaseEntity]] {
	return Get[model.DdlPage[model.DatabaseEntity]](a.client, fmt.Sprintf("/graph/v2/service"))
}

func (a *ActionbaseClient) GetDatabase(name string) *Response[model.DatabaseEntity] {
	return Get[model.DatabaseEntity](a.client, fmt.Sprintf("/graph/v2/service/%s", name))
}

func (a *ActionbaseClient) GetStorages() *Response[model.DdlPage[model.StorageEntity]] {
	return Get[model.DdlPage[model.StorageEntity]](a.client, fmt.Sprintf("/graph/v2/storage"))
}

func (a *ActionbaseClient) GetTables(database string) *Response[model.DdlPage[model.TableEntity]] {
	return Get[model.DdlPage[model.TableEntity]](a.client, fmt.Sprintf("/graph/v2/service/%s/label", database))
}

func (a *ActionbaseClient) GetTable(database, table string) *Response[model.TableEntity] {
	return Get[model.TableEntity](a.client, fmt.Sprintf("/graph/v2/service/%s/label/%s", database, table))
}

func (a *ActionbaseClient) GetAliases(database string) *Response[model.DdlPage[model.AliasEntity]] {
	return Get[model.DdlPage[model.AliasEntity]](a.client, fmt.Sprintf("/graph/v2/service/%s/alias", database))
}

func (a *ActionbaseClient) GetAlias(database, name string) *Response[model.AliasEntity] {
	return Get[model.AliasEntity](a.client, fmt.Sprintf("/graph/v2/service/%s/alias/%s", database, name))
}

func (a *ActionbaseClient) Get(
	database, table, source, target string) *Response[model.Get] {
	return Get[model.Get](
		a.client,
		fmt.Sprintf(
			"/graph/v3/databases/%s/tables/%s/edges/get?source=%s&target=%s",
			database,
			table,
			source,
			target),
	)
}

func (a *ActionbaseClient) Counts(
	database, table, start, direction string) *Response[model.Counts] {
	return Get[model.Counts](a.client,
		fmt.Sprintf("/graph/v3/databases/%s/tables/%s/edges/counts?start=%s&direction=%s",
			database,
			table,
			start,
			direction),
	)
}

func (a *ActionbaseClient) Scan(
	database, table, index, start, direction, limit string, ranges *string,
) *Response[model.Scan] {
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

	return Get[model.Scan](a.client, uriBuilder.String())
}

func (a *ActionbaseClient) Mutate(
	database string,
	table string,
	request *model.EdgeBulkMutation,
) *Response[model.Mutation] {
	return Post[*model.EdgeBulkMutation, model.Mutation](a.client,
		fmt.Sprintf("/graph/v3/databases/%s/tables/%s/edges", database, table),
		request,
	)
}

func (a *ActionbaseClient) GetHost() string {
	return a.client.baseUrl
}
