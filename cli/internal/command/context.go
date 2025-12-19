package command

import (
	"fmt"

	"github.com/kakao/actionbase/internal/client"
)

type Context struct {
	runner           ContextRunner
	actionbaseClient *client.ActionbaseClient
}

type ContextRunner interface {
	IsDebugEnabled() bool
	GetCurrentDatabase() string
	GetCurrentTable() string
	GetCurrentAlias() string
}

func NewContext(runner ContextRunner, actionbaseClient *client.ActionbaseClient) *Context {
	return &Context{runner: runner, actionbaseClient: actionbaseClient}
}

func (c *Context) Execute(_ []string) {
	PrintContext(
		c.actionbaseClient.GetHost(),
		c.runner.GetCurrentDatabase(),
		c.runner.GetCurrentTable(),
		c.runner.GetCurrentAlias(),
		c.runner.IsDebugEnabled())
}

func PrintContext(host string, database string, table string, alias string, isDebugEnabled bool) {
	if database == "" {
		database = "-"
	}

	if table == "" {
		table = "-"
	}

	if alias == "" {
		alias = "-"
	}

	debug := "on"
	if !isDebugEnabled {
		debug = "off"
	}

	fmt.Println("\033[33m╭────────────────────────────────────────────────────────────────────────────────────────╮\033[0m")
	fmt.Println("\033[33m│                                                                                        │\033[0m")
	fmt.Printf("\033[33m│  host\033[0m %-80s \033[33m│\033[0m\n", host)
	fmt.Printf("\033[33m│  database\033[0m %-76s \033[33m│\033[0m\n", database)
	fmt.Printf("\033[33m│  table\033[0m %-79s \033[33m│\033[0m\n", table)
	fmt.Printf("\033[33m│  alias\033[0m %-79s \033[33m│\033[0m\n", alias)
	fmt.Printf("\033[33m│  debug\033[0m %-79s \033[33m│\033[0m\n", debug)
	fmt.Println("\033[33m│                                                                                        │\033[0m")
	fmt.Println("\033[33m╰────────────────────────────────────────────────────────────────────────────────────────╯\033[0m")
}

func (c *Context) GetDescription() string {
	return "Show current status"
}

func (c *Context) GetType() Type {
	return TypeContext
}
