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

	using, found := parser.Get("using")
	if !found {
		fmt.Printf("Usage: %s\n", d.GetType().GetCommand())
		return
	}

	name := args[0]
	if using == "table" {
		d.tableCommand.Desc(name)
		return
	} else if using == "alias" {
		d.aliasCommand.Desc(name)
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
