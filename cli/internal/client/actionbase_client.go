package client

import (
	"net/url"

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
	return Post[*model.StorageCreateRequest, model.DdlStatus[model.StorageEntity]](
		a.client, "/graph/v2/storage/"+name, requestBody)
}

func (a *ActionbaseClient) CreateDatabase(name string, requestBody *model.DatabaseCreateRequest) *Response[model.DdlStatus[model.DatabaseEntity]] {
	return Post[*model.DatabaseCreateRequest, model.DdlStatus[model.DatabaseEntity]](
		a.client, "/graph/v2/service/"+name, requestBody)
}

func (a *ActionbaseClient) CreateTable(
	database string,
	name string,
	requestBody *model.TableCreateRequest,
) *Response[model.DdlStatus[model.TableEntity]] {
	return Post[*model.TableCreateRequest, model.DdlStatus[model.TableEntity]](
		a.client, "/graph/v2/service/"+database+"/label/"+name, requestBody)
}

func (a *ActionbaseClient) CreateAlias(database string, name string, requestBody *model.AliasCreateRequest) *Response[model.DdlStatus[model.AliasEntity]] {
	return Post[*model.AliasCreateRequest, model.DdlStatus[model.AliasEntity]](
		a.client, "/graph/v2/service/"+database+"/alias/"+name, requestBody)
}

func (a *ActionbaseClient) GetTenant() *Response[model.Tenant] {
	return Get[model.Tenant](a.client, "/graph/v3")
}

func (a *ActionbaseClient) GetDatabases() *Response[model.DdlPage[model.DatabaseEntity]] {
	return Get[model.DdlPage[model.DatabaseEntity]](a.client, "/graph/v2/service")
}

func (a *ActionbaseClient) GetDatabase(name string) *Response[model.DatabaseEntity] {
	return Get[model.DatabaseEntity](a.client, "/graph/v2/service/"+name)
}

func (a *ActionbaseClient) GetStorages() *Response[model.DdlPage[model.StorageEntity]] {
	return Get[model.DdlPage[model.StorageEntity]](a.client, "/graph/v2/storage")
}

func (a *ActionbaseClient) GetTables(database string) *Response[model.DdlPage[model.TableEntity]] {
	return Get[model.DdlPage[model.TableEntity]](a.client, "/graph/v2/service/"+database+"/label")
}

func (a *ActionbaseClient) GetTable(database, table string) *Response[model.TableEntity] {
	return Get[model.TableEntity](a.client, "/graph/v2/service/"+database+"/label/"+table)
}

func (a *ActionbaseClient) GetAliases(database string) *Response[model.DdlPage[model.AliasEntity]] {
	return Get[model.DdlPage[model.AliasEntity]](a.client, "/graph/v2/service/"+database+"/alias")
}

func (a *ActionbaseClient) GetAlias(database, name string) *Response[model.AliasEntity] {
	return Get[model.AliasEntity](a.client, "/graph/v2/service/"+database+"/alias/"+name)
}

func (a *ActionbaseClient) Get(
	database, table, source, target string) *Response[model.Get] {
	return Get[model.Get](
		a.client,
		"/graph/v3/databases/"+database+"/tables/"+table+"/edges/get?source="+source+"&target="+target)
}

func (a *ActionbaseClient) Counts(
	database, table, start, direction string) *Response[model.Counts] {
	return Get[model.Counts](a.client, "/graph/v3/databases/"+database+"/tables/"+table+"/edges/counts?start="+start+"&direction="+direction)
}

func (a *ActionbaseClient) Scan(
	database, table, index, start, direction, limit string, ranges *string,
) *Response[model.Scan] {
	u := &url.URL{Path: "/graph/v3/databases/" + database + "/tables/" + table + "/edges/scan/" + index}
	q := u.Query()
	q.Set("start", start)
	q.Set("direction", direction)
	q.Set("limit", limit)
	if ranges != nil && *ranges != "" {
		q.Set("&ranges=", *ranges)
	}
	u.RawQuery = q.Encode()

	return Get[model.Scan](a.client, u.String())
}

func (a *ActionbaseClient) Mutate(
	database string,
	table string,
	request *model.EdgeBulkMutation,
) *Response[model.Mutation] {
	return Post[*model.EdgeBulkMutation, model.Mutation](
		a.client,
		"/graph/v3/databases/"+database+"/tables/"+table+"/edges",
		request,
	)
}

func (a *ActionbaseClient) GetHost() string {
	return a.client.baseUrl
}
