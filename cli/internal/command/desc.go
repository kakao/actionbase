package command

import (
	"fmt"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/metastore"
)

type Desc struct {
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

func (d *Desc) Execute(args []string) {
	if len(args) < 1 {
		fmt.Printf("Usage: %s\n", d.GetType().GetCommand())
		return
	}

	resourceType := args[0]
	if resourceType != "table" && resourceType != "alias" {
		fmt.Printf("Usage: %s\n", d.GetType().GetCommand())
		return
	}

	if len(args) >= 2 {
		name := args[1]

		if resourceType == "table" {
			d.tableCommand.Desc(name)
		} else {
			d.aliasCommand.Desc(name)
		}
		return
	}

	if resourceType == "table" {
		currentTable := d.runner.GetCurrentTable()

		if currentTable == "" {
			fmt.Println("No table selected. Use 'use <table|alias> <name>'")
			return
		}
		d.tableCommand.Desc(currentTable)
		return
	}

	currentAlias := d.runner.GetCurrentAlias()
	if currentAlias == "" {
		fmt.Println("No alias selected. Use 'use alias <name>'")
		return
	}
	d.aliasCommand.Desc(currentAlias)
}

func (d *Desc) GetDescription() string {
	return "Describe table"
}

func (d *Desc) GetType() Type {
	return TypeDesc
}
