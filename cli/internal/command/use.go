package command

import (
	"fmt"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/metastore"
	"github.com/kakao/actionbase/internal/command/model"
)

type Use struct {
	context         *Context
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

func (u *Use) Execute(args []string) *model.Response {
	if len(args) < 1 {
		return model.Fail(fmt.Sprintf("Usage: %s", u.GetType().GetCommand()))
	}

	commandType := args[0]

	if commandType == "database" {
		if len(args) < 2 {
			return model.Fail(fmt.Sprintf("Usage: %s", u.GetType().GetCommand()))
		}

		return u.databaseCommand.Use(args[1])
	}

	if commandType == "table" {
		if len(args) < 2 {
			return model.Fail(fmt.Sprintf("Usage: %s", u.GetType().GetCommand()))
		}

		return u.tableCommand.Use(args[1])
	}

	if commandType == "alias" {
		if len(args) < 2 {
			return model.Fail(fmt.Sprintf("Usage: %s", u.GetType().GetCommand()))
		}

		return u.aliasCommand.Use(args[1])
	}

	return model.Fail(fmt.Sprintf("Usage: %s", u.GetType().GetCommand()))
}

func (u *Use) GetDescription() string {
	return "Select a database, table or alias to use"
}

func (u *Use) GetType() Type {
	return TypeUse
}
