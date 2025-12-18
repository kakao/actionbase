package command

import (
	"fmt"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/util"
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

func (c *Context) Execute(args []string) {
	database := c.runner.GetCurrentDatabase()
	if database == "" {
		database = "-"
	}

	table := c.runner.GetCurrentTable()
	if table == "" {
		table = "-"
	}

	alias := c.runner.GetCurrentAlias()
	if alias == "" {
		alias = "-"
	}

	fieldColumnOrder := []string{"host", "database", "table", "alias", "is_debug_enabled"}

	status := map[string]interface{}{
		"host":             c.actionbaseClient.GetHost(),
		"database":         database,
		"table":            table,
		"alias":            alias,
		"is_debug_enabled": c.runner.IsDebugEnabled(),
	}
	fmt.Println(util.PrettyPrintRowsWithOrder([]map[string]interface{}{status}, fieldColumnOrder))
}

func (c *Context) GetDescription() string {
	return "Show current status"
}

func (c *Context) GetType() Type {
	return TypeContext
}
