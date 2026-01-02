package command

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	clientModel "github.com/kakao/actionbase/internal/client/model"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/util"
)

type Create struct {
	context          *Context
	actionbaseClient *client.ActionbaseClient
}

type CreateRunner interface {
	GetCurrentDatabase() string
	GetCurrentTable() string
	GetCurrentAlias() string
	SetCurrentDatabase(database string)
	SetCurrentTable(table string)
	SetCurrentAlias(alias string)
}

func NewCreate(actionbaseClient *client.ActionbaseClient) *Create {
	return &Create{
		actionbaseClient: actionbaseClient,
	}
}

const databaseUsagePrompt = "Usage: create database --name <name> --comment <comment>"
const storageUsagePrompt = "Usage: create storage --hbaseNamespace <hbaseNamespace> --hbaseTable <hbaseTable> --storageType <storageType>--name <name> --comment <comment>"
const tableUsagePrompt = "Usage: create table --database <database> --storage <storage> --name <name> --comment <comment> --type <type> --direction <direction> --schema <schema> --indices <indices> --groups <groups>"
const aliasUsagePrompt = "Usage: create alias --database <database> --table <table> --name <name> --comment <comment>"

func (c *Create) Execute(args []string) *model.Response {
	if len(args) < 1 {
		return model.Fail(fmt.Sprintf("Usage: %s", c.GetType().GetCommand()))
	}

	parser := util.ParseArgs(args)

	resourceType := args[0]
	switch resourceType {
	case "database":
		if len(args) < 3 {
			return model.Fail(databaseUsagePrompt)
		}
		return c.createDatabase(parser)
	case "storage":
		if len(args) < 4 {
			return model.Fail(storageUsagePrompt)
		}
		return c.createStorage(parser)
	case "table":
		if len(args) < 10 {
			return model.Fail(tableUsagePrompt)
		}
		return c.createTable(parser)
	case "alias":
		if len(args) < 5 {
			return model.Fail(aliasUsagePrompt)
		}
		return c.createAlias(parser)
	default:
		return model.Fail(fmt.Sprintf("Usage: %s", c.GetType().GetCommand()))
	}
}

func (c *Create) GetDescription() string {
	return "Create database, storage, table or alias"
}

func (c *Create) GetType() Type {
	return TypeCreate
}

func (c *Create) createDatabase(parser *util.Parser) *model.Response {
	name, found := parser.Get("name")
	if !found {
		return model.Fail(databaseUsagePrompt)
	}

	comment, found := parser.GetParsed("comment")
	if !found {
		return model.Fail(databaseUsagePrompt)
	}

	requestBody := clientModel.DatabaseCreateRequest{
		Desc: comment.(string),
	}
	response := c.actionbaseClient.CreateDatabase(name, &requestBody)
	if response.IsError() || response.Body.Status == "ERROR" {
		return model.Fail(fmt.Sprintf("Failed to create database '%s'", name))
	}

	return model.SuccessWithResult(fmt.Sprintf("The database '%s' is created", name))
}

func (c *Create) createStorage(parser *util.Parser) *model.Response {
	hbaseNamespace, found := parser.Get("hbaseNamespace")
	if !found {
		return model.Fail(storageUsagePrompt)
	}

	hbaseTable, found := parser.Get("hbaseTable")
	if !found {
		return model.Fail(storageUsagePrompt)
	}

	storageType, found := parser.Get("storageType")
	if !found {
		return model.Fail(storageUsagePrompt)
	}

	name, found := parser.Get("name")
	if !found {
		return model.Fail(storageUsagePrompt)
	}

	comment, found := parser.GetParsed("comment")
	if !found {
		return model.Fail(storageUsagePrompt)
	}

	requestBody := clientModel.StorageCreateRequest{
		Desc: comment.(string),
		Type: storageType,
		Conf: map[string]interface{}{
			"hbaseNamespace": hbaseNamespace,
			"hbaseTable":     hbaseTable,
		},
	}
	response := c.actionbaseClient.CreateStorage(name, &requestBody)
	if response.IsError() || response.Body.Status == "ERROR" {
		return model.Fail(fmt.Sprintf("Failed to create storage '%s'", name))
	}

	return model.SuccessWithResult(fmt.Sprintf("The storage '%s' is created", name))
}

func (c *Create) createTable(parser *util.Parser) *model.Response {
	database, found := parser.Get("database")
	if !found {
		return model.Fail(tableUsagePrompt)
	}

	storage, found := parser.Get("storage")
	if !found {
		return model.Fail(tableUsagePrompt)
	}

	name, found := parser.Get("name")
	if !found {
		return model.Fail(tableUsagePrompt)
	}

	comment, found := parser.GetParsed("comment")
	if !found {
		return model.Fail(tableUsagePrompt)
	}

	tableType, found := parser.Get("type")
	if !found {
		return model.Fail(tableUsagePrompt)
	}

	directionStr, found := parser.Get("direction")
	if !found {
		return model.Fail(tableUsagePrompt)
	}

	direction, err := ParseDirection(directionStr)
	if err != nil {
		return model.Fail(err.Error())
	}

	schema, found := parser.Get("schema")
	if !found {
		return model.Fail(tableUsagePrompt)
	}

	schema = strings.Trim(schema, "'")
	var schemaRequest clientModel.Schema
	if err = json.Unmarshal([]byte(schema), &schemaRequest); err != nil {
		return model.Fail(tableUsagePrompt)
	}

	indices, _ := parser.Get("indices")
	var indicesRequest []clientModel.Index
	if indices != "" {
		indices = strings.Trim(indices, "'")
		err = json.Unmarshal([]byte(indices), &indicesRequest)
		if err != nil {
			return model.Fail(tableUsagePrompt)
		}
	} else {
		indicesRequest = []clientModel.Index{}
	}

	groups, _ := parser.Get("groups")
	var groupsRequest []clientModel.Group
	if groups != "" {
		groups = strings.Trim(groups, "'")
		err = json.Unmarshal([]byte(groups), &groupsRequest)
		if err != nil {
			return model.Fail(tableUsagePrompt)
		}
	} else {
		groupsRequest = []clientModel.Group{}
	}

	tableCreateRequest := clientModel.TableCreateRequest{
		Desc:          comment.(string),
		Type:          tableType,
		Schema:        &schemaRequest,
		DirectionType: direction.String(),
		Storage:       storage,
		Groups:        &groupsRequest,
		Indices:       &indicesRequest,
		Event:         false,
		ReadOnly:      strings.ToUpper(tableType) == "MULTI_EDGE",
		Mode:          "SYNC",
	}

	response := c.actionbaseClient.CreateTable(
		database,
		name,
		&tableCreateRequest)
	if response.IsError() || response.Body.Status == "ERROR" {
		return model.Fail(fmt.Sprintf("Failed to create table '%s'", name))
	}

	return model.SuccessWithResult(fmt.Sprintf("The table '%s' is created", name))
}

func (c *Create) createAlias(parser *util.Parser) *model.Response {
	database, found := parser.Get("database")
	if !found {
		return model.Fail(aliasUsagePrompt)
	}

	table, found := parser.Get("table")
	if !found {
		return model.Fail(aliasUsagePrompt)
	}

	name, found := parser.Get("name")
	if !found {
		return model.Fail(aliasUsagePrompt)
	}

	comment, found := parser.GetParsed("comment")
	if !found {
		return model.Fail(aliasUsagePrompt)
	}

	response := c.actionbaseClient.CreateAlias(
		database,
		name,
		&clientModel.AliasCreateRequest{
			Target: fmt.Sprintf("%s.%s", database, table),
			Desc:   comment.(string),
		})

	if response.IsError() || response.Body.Status == "ERROR" {
		return model.Fail(fmt.Sprintf("Failed to create alias '%s'", name))
	}

	resultMessage := fmt.Sprintf("The alias '%s' is created", name)
	return model.SuccessWithResult(resultMessage)
}
