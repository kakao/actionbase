package command

import (
	"fmt"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/metastore"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/util"
)

type Show struct {
	context          *Context
	runner           ShowRunner
	actionbaseClient *client.ActionbaseClient
	databaseCommand  *metastore.Database
	storageCommand   *metastore.Storage
	tableCommand     *metastore.Table
	aliasCommand     *metastore.Alias
}

type ShowRunner interface {
	GetCurrentDatabase() string
	GetCurrentTable() string
	GetCurrentAlias() string
	SetCurrentDatabase(database string)
	SetCurrentTable(table string)
	SetCurrentAlias(alias string)
}

func NewShow(runner ShowRunner, actionbaseClient *client.ActionbaseClient) *Show {
	return &Show{
		runner:           runner,
		actionbaseClient: actionbaseClient,
		databaseCommand:  metastore.NewDatabase(runner, actionbaseClient),
		storageCommand:   metastore.NewStorage(runner, actionbaseClient),
		tableCommand:     metastore.NewTable(runner, actionbaseClient),
		aliasCommand:     metastore.NewAlias(runner, actionbaseClient),
	}
}

func (s *Show) Execute(args []string) *model.Response {
	if len(args) < 1 {
		return model.Fail(fmt.Sprintf("Usage: %s", s.GetType().GetCommand()))
	}

	resourceType := args[0]
	switch resourceType {
	case "databases":
		return s.databaseCommand.ShowAll()
	case "storages":
		return s.storageCommand.ShowAll()
	case "aliases":
		return s.aliasCommand.ShowAll()
	case "tables":
		return s.tableCommand.ShowAll()
	case "indices":
		return s.showIndices(args)
	case "groups":
		return s.showGroups(args)
	default:
		return model.Fail(fmt.Sprintf("Usage: %s", s.GetType().GetCommand()))
	}
}

func (s *Show) showIndices(args []string) *model.Response {
	parser := util.ParseArgs(args)
	using, found := parser.Get("using")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", s.GetType().GetCommand()))
	}

	table, found := parser.Get("table")
	if found {
		return s.tableCommand.ShowIndices(table)
	}

	if using == "table" {
		table := s.runner.GetCurrentTable()
		if table == "" {
			return model.Fail("No table selected. Use 'use <table|alias> <name>'")
		}

		return s.tableCommand.ShowIndices(table)
	}

	if using == "alias" {
		alias := s.runner.GetCurrentAlias()
		if alias == "" {
			return model.Fail("No alias selected. Use 'use alias <name>'")
		}

		return s.tableCommand.ShowIndices(s.runner.GetCurrentTable())
	}

	return model.Fail(fmt.Sprintf("Usage: %s", s.GetType().GetCommand()))
}

func (s *Show) showGroups(args []string) *model.Response {
	parser := util.ParseArgs(args)
	using, found := parser.Get("using")
	if !found {
		return model.Fail(fmt.Sprintf("Usage: %s", s.GetType().GetCommand()))
	}

	table, found := parser.Get("table")
	if found {
		return s.tableCommand.ShowGroups(table)
	}

	if using == "table" {
		table := s.runner.GetCurrentTable()
		if table == "" {
			return model.Fail("No table selected. Use 'use <table|alias> <name>'")
		}

		return s.tableCommand.ShowGroups(table)
	}

	if using == "alias" {
		alias := s.runner.GetCurrentAlias()
		if alias == "" {
			return model.Fail("No alias selected. Use 'use alias <name>'")
		}

		return s.tableCommand.ShowGroups(s.runner.GetCurrentTable())
	}

	return model.Fail(fmt.Sprintf("Usage: %s", s.GetType().GetCommand()))
}

func (s *Show) GetDescription() string {
	return "Show databases, tables, table indices, or table groups"
}

func (s *Show) GetType() Type {
	return TypeShow
}
