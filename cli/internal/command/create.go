package command

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/client/model"
	"github.com/kakao/actionbase/internal/util"
)

type Create struct {
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

func (c *Create) Execute(args []string) {
	if len(args) < 1 {
		fmt.Printf("Usage: %s\n", c.GetType().GetCommand())
		return
	}

	parser := util.ParseArgs(args)

	resourceType := args[0]
	switch resourceType {
	case "database":
		if len(args) < 3 {
			fmt.Println(databaseUsagePrompt)
			return
		}
		c.createDatabase(parser)
	case "storage":
		if len(args) < 4 {
			fmt.Println(storageUsagePrompt)
			return
		}
		c.createStorage(parser)
	case "table":
		if len(args) < 10 {
			fmt.Println(tableUsagePrompt)
			return
		}
		c.createTable(parser)
	case "alias":
		if len(args) < 5 {
			fmt.Println(aliasUsagePrompt)
			return
		}
		c.createAlias(parser)
	default:
		fmt.Printf("Usage: %s\n", c.GetType().GetCommand())
	}
}

func (c *Create) GetDescription() string {
	return "Create database, storage, table or alias"
}

func (c *Create) GetType() Type {
	return TypeCreate
}

func (c *Create) createDatabase(parser *util.Parser) {
	name, found := parser.Get("name")
	if !found {
		fmt.Println(databaseUsagePrompt)
		return
	}

	comment, found := parser.GetParsed("comment")
	if !found {
		fmt.Println(databaseUsagePrompt)
		return
	}

	requestBody := model.DatabaseCreateRequest{
		Desc: comment.(string),
	}
	ddlStatus := c.actionbaseClient.CreateDatabase(name, &requestBody)
	if ddlStatus == nil {
		fmt.Printf("Failed to create database '%s'\n", name)
		return
	}

	fmt.Printf("The database '%s' is created\n", name)
}

func (c *Create) createStorage(parser *util.Parser) {
	hbaseNamespace, found := parser.Get("hbaseNamespace")
	if !found {
		fmt.Println(storageUsagePrompt)
		return
	}

	hbaseTable, found := parser.Get("hbaseTable")
	if !found {
		fmt.Println(storageUsagePrompt)
		return
	}

	storageType, found := parser.Get("storageType")
	if !found {
		fmt.Println(storageUsagePrompt)
		return
	}

	name, found := parser.Get("name")
	if !found {
		fmt.Println(storageUsagePrompt)
		return
	}

	comment, found := parser.GetParsed("comment")
	if !found {
		fmt.Println(storageUsagePrompt)
		return
	}

	requestBody := model.StorageCreateRequest{
		Desc: comment.(string),
		Type: storageType,
		Conf: map[string]interface{}{
			"hbaseNamespace": hbaseNamespace,
			"hbaseTable":     hbaseTable,
		},
	}
	ddlStatus := c.actionbaseClient.CreateStorage(name, &requestBody)
	if ddlStatus == nil {
		fmt.Printf("Failed to create storage '%s'\n", name)
		return
	}

	fmt.Printf("The storage '%s' is created\n", name)
}

func (c *Create) createTable(parser *util.Parser) {
	database, found := parser.Get("database")
	if !found {
		fmt.Println(tableUsagePrompt)
		return
	}

	storage, found := parser.Get("storage")
	if !found {
		fmt.Println(tableUsagePrompt)
		return
	}

	name, found := parser.Get("name")
	if !found {
		fmt.Println(tableUsagePrompt)
		return
	}

	comment, found := parser.GetParsed("comment")
	if !found {
		fmt.Println(tableUsagePrompt)
		return
	}

	tableType, found := parser.Get("type")
	if !found {
		fmt.Println(tableUsagePrompt)
		return
	}

	direction, found := parser.Get("direction")
	if !found {
		fmt.Println(tableUsagePrompt)
		return
	}

	schema, found := parser.Get("schema")
	if !found {
		fmt.Println(tableUsagePrompt)
		return
	}

	schema = strings.Trim(schema, "'")
	var schemaRequest model.Schema
	err := json.Unmarshal([]byte(schema), &schemaRequest)
	if err != nil {
		fmt.Printf("Failed to parse data.schema: %s\n", err.Error())
		return
	}

	indices, _ := parser.Get("indices")
	var indicesRequest []model.Index
	if indices != "" {
		indices = strings.Trim(indices, "'")
		err = json.Unmarshal([]byte(indices), &indicesRequest)
		if err != nil {
			fmt.Printf("Failed to parse data.indices: %s\n", err.Error())
			return
		}
	} else {
		indicesRequest = []model.Index{}
	}

	groups, _ := parser.Get("groups")
	var groupsRequest []model.Group
	if groups != "" {
		groups = strings.Trim(groups, "'")
		err = json.Unmarshal([]byte(groups), &groupsRequest)
		if err != nil {
			fmt.Printf("Failed to parse data.groups: %s\n", err.Error())
			return
		}
	} else {
		groupsRequest = []model.Group{}
	}

	tableCreateRequest := model.TableCreateRequest{
		Desc:          comment.(string),
		Type:          tableType,
		Schema:        &schemaRequest,
		DirectionType: direction,
		Storage:       storage,
		Groups:        &groupsRequest,
		Indices:       &indicesRequest,
		Event:         false,
		ReadOnly:      strings.ToUpper(tableType) == "MULTI_EDGE",
		Mode:          "SYNC",
	}

	ddlStatus := c.actionbaseClient.CreateTable(
		database,
		name,
		&tableCreateRequest)
	if ddlStatus == nil {
		fmt.Printf("Failed to create table '%s'\n", name)
		return
	}

	fmt.Printf("The table '%s' is created\n", name)
}

func (c *Create) createAlias(parser *util.Parser) {
	database, found := parser.Get("database")
	if !found {
		fmt.Println(aliasUsagePrompt)
		return
	}

	table, found := parser.Get("table")
	if !found {
		fmt.Println(aliasUsagePrompt)
		return
	}

	name, found := parser.Get("name")
	if !found {
		fmt.Println(aliasUsagePrompt)
		return
	}

	comment, found := parser.GetParsed("comment")
	if !found {
		fmt.Println(aliasUsagePrompt)
		return
	}

	ddlStatus := c.actionbaseClient.CreateAlias(database, table, name, comment)
	if ddlStatus == nil {
		fmt.Printf("Failed to create alias '%s'\n", name)
		return
	}

	fmt.Printf("The alias '%s' is created\n", name)
	return
}
