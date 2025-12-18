package command

import (
	"fmt"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/metastore"
	"github.com/kakao/actionbase/internal/util"
)

type Show struct {
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

// Execute executes the show command
func (s *Show) Execute(args []string) {
	if len(args) < 1 {
		fmt.Printf("Usage: %s\n", s.GetType().GetCommand())
		return
	}

	resourceType := args[0]
	switch resourceType {
	case "databases":
		s.databaseCommand.ShowAll()
	case "storages":
		s.storageCommand.ShowAll()
	case "aliases":
		s.aliasCommand.ShowAll()
	case "tables":
		s.tableCommand.ShowAll()
	case "indices":
		s.showIndices(args)
	case "groups":
		s.showGroups(args)
	default:
		fmt.Printf("Usage: %s\n", s.GetType().GetCommand())
	}
}

func (s *Show) showIndices(args []string) {
	parser := util.ParseArgs(args)
	using, found := parser.Get("using")
	if !found {
		fmt.Printf("Usage: %s\n", s.GetType().GetCommand())
		return
	}

	table, found := parser.Get("table")
	if found {
		s.tableCommand.ShowIndices(table)
		return
	}

	if using == "table" {
		table := s.runner.GetCurrentTable()
		if table == "" {
			fmt.Println("No table selected. Use 'use <table|alias> <name>'")
			return
		}
		s.tableCommand.ShowIndices(table)
		return
	}

	if using == "alias" {
		alias := s.runner.GetCurrentAlias()
		if alias == "" {
			fmt.Println("No alias selected. Use 'use alias <name>'")
			return
		}
		s.tableCommand.ShowIndices(s.runner.GetCurrentTable())
		return
	}

	fmt.Printf("Usage: %s\n", s.GetType().GetCommand())
}

func (s *Show) showGroups(args []string) {
	parser := util.ParseArgs(args)
	using, found := parser.Get("using")
	if !found {
		fmt.Printf("Usage: %s\n", s.GetType().GetCommand())
		return
	}

	table, found := parser.Get("table")
	if found {
		s.tableCommand.ShowGroups(table)
		return
	}

	if using == "table" {
		table := s.runner.GetCurrentTable()
		if table == "" {
			fmt.Printf("No table selected. Use 'use <table|alias> <name>'\n")
			return
		}
		s.tableCommand.ShowGroups(table)
		return
	}

	if using == "alias" {
		alias := s.runner.GetCurrentAlias()
		if alias == "" {
			fmt.Println("No alias selected. Use 'use alias <name>'")
			return
		}
		fmt.Println(alias)
		s.tableCommand.ShowGroups(s.runner.GetCurrentTable())
		return
	}

	fmt.Printf("Usage: %s\n", s.GetType().GetCommand())
}

func (s *Show) GetDescription() string {
	return "Show databases, tables, table indices, or table groups"
}

func (s *Show) GetType() Type {
	return TypeShow
}
