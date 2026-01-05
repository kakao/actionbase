package command

import (
	"fmt"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/metastore"
)

type Use struct {
	runner          UseRunner
	databaseCommand *metastore.Database
	tableCommand    *metastore.Table
	aliasCommand    *metastore.Alias
}

type UseRunner interface {
	GetCurrentDatabase() string
	GetCurrentTable() string
	GetCurrentAlias() string
	SetCurrentDatabase(database string)
	SetCurrentTable(table string)
	SetCurrentAlias(alias string)
}

func NewUse(runner UseRunner, actionbaseClient *client.ActionbaseClient) *Use {
	return &Use{
		runner:          runner,
		databaseCommand: metastore.NewDatabase(runner, actionbaseClient),
		tableCommand:    metastore.NewTable(runner, actionbaseClient),
		aliasCommand:    metastore.NewAlias(runner, actionbaseClient),
	}
}

// Execute executes the use command
func (u *Use) Execute(args []string) {
	if len(args) < 1 {
		fmt.Printf("Usage: %s\n", u.GetType().GetCommand())
		return
	}

	commandType := args[0]

	if commandType == "database" {
		if len(args) < 2 {
			fmt.Printf("Usage: %s\n", u.GetType().GetCommand())
			return
		}
		u.databaseCommand.Use(args[1])
		return
	}

	if commandType == "table" {
		if len(args) < 2 {
			fmt.Printf("Usage: %s\n", u.GetType().GetCommand())
			return
		}
		u.tableCommand.Use(args[1])
		return
	}

	if commandType == "alias" {
		if len(args) < 2 {
			fmt.Printf("Usage: %s\n", u.GetType().GetCommand())
			return
		}
		u.aliasCommand.Use(args[1])
		return
	}

	fmt.Printf("Usage: %s\n", u.GetType().GetCommand())
}

func (u *Use) GetDescription() string {
	return "Select a database, table or alias to use"
}

func (u *Use) GetType() Type {
	return TypeUse
}
