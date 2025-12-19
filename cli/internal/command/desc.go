package command

import (
	"fmt"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/metastore"
	"github.com/kakao/actionbase/internal/util"
)

type Desc struct {
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
		tableCommand: metastore.NewTable(runner, actionbaseClient),
		aliasCommand: metastore.NewAlias(runner, actionbaseClient),
	}
}

func (d *Desc) Execute(args []string) {
	if len(args) < 1 {
		fmt.Printf("Usage: %s\n", d.GetType().GetCommand())
		return
	}

	parser := util.ParseArgs(args)

	resourceType := args[0]
	if resourceType == "table" || resourceType == "alias" {
		name, found := parser.Get("name")

		if !found {
			fmt.Println("Usage: desc <table|alias> <name>")
			return
		}

		if resourceType == "table" {
			d.tableCommand.Desc(name)
		} else {
			d.aliasCommand.Desc(name)
		}

		return
	}

	using, found := parser.Get("using")
	if !found {
		fmt.Printf("Usage: %s\n", d.GetType().GetCommand())
		return
	}

	usingType := args[0]
	if using == "table" {
		d.tableCommand.Desc(usingType)
		return
	} else if using == "alias" {
		d.aliasCommand.Desc(usingType)
		return
	}

	fmt.Printf("Usage: %s\n", d.GetType().GetCommand())
}

func (d *Desc) GetDescription() string {
	return "Describe table"
}

func (d *Desc) GetType() Type {
	return TypeDesc
}
