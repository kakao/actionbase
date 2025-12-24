package command

import (
	"fmt"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/metastore"
	"github.com/kakao/actionbase/internal/command/model"
)

type Desc struct {
	context      *Context
	runner       DescRunner
	tableCommand *metastore.Table
	aliasCommand *metastore.Alias
}

type DescRunner interface {
	GetCurrentDatabase() string
	GetCurrentTable() string
	GetCurrentAlias() string
	SetCurrentTable(table string)
	SetCurrentDatabase(database string)
	SetCurrentAlias(alias string)
}

func NewDesc(runner DescRunner, actionbaseClient *client.ActionbaseClient) *Desc {
	return &Desc{
		runner:       runner,
		tableCommand: metastore.NewTable(runner, actionbaseClient),
		aliasCommand: metastore.NewAlias(runner, actionbaseClient),
	}
}

func (d *Desc) Execute(args []string) *model.Response {
	if len(args) < 1 {
		return model.Fail(fmt.Sprintf("Usage: %s", d.GetType().GetCommand()))
	}

	resourceType := args[0]
	if resourceType != "table" && resourceType != "alias" {
		return model.Fail(fmt.Sprintf("Usage: %s", d.GetType().GetCommand()))
	}

	if len(args) >= 2 {
		name := args[1]

		if resourceType == "table" {
			return d.tableCommand.Desc(name)
		}

		return d.aliasCommand.Desc(name)
	}

	if resourceType == "table" {
		currentTable := d.runner.GetCurrentTable()

		if currentTable == "" {
			return model.Fail("No table selected. Use 'use <table|alias> <name>'")
		}
		return d.tableCommand.Desc(currentTable)
	}

	currentAlias := d.runner.GetCurrentAlias()
	if currentAlias == "" {
		return model.Fail("No alias selected. Use 'use alias <name>'")
	}

	return d.aliasCommand.Desc(currentAlias)
}

func (d *Desc) GetDescription() string {
	return "Describe table"
}

func (d *Desc) GetType() Type {
	return TypeDesc
}
