package command

import (
	"fmt"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/model"
)

type Context struct {
	runner           ContextRunner
	actionbaseClient *client.ActionbaseClient
}

type ContextRunner interface {
	GetCurrentPort() string
	IsServerModeEnabled() bool
	IsDebugEnabled() bool
	GetCurrentDatabase() string
	GetCurrentTable() string
	GetCurrentAlias() string
}

func NewContext(runner ContextRunner, actionbaseClient *client.ActionbaseClient) *Context {
	return &Context{runner: runner, actionbaseClient: actionbaseClient}
}

func (c *Context) Execute(_ []string) *model.Response {
	return PrintContext(
		c.actionbaseClient.GetHost(),
		c.runner.GetCurrentDatabase(),
		c.runner.GetCurrentTable(),
		c.runner.GetCurrentAlias(),
		c.runner.GetCurrentPort(),
		c.runner.IsServerModeEnabled(),
		c.runner.IsDebugEnabled())
}

func PrintContext(host, database, table, alias, currentPort string, isServerModeEnabled, isDebugEnabled bool) *model.Response {
	if database == "" {
		database = "-"
	}

	if table == "" {
		table = "-"
	}

	if alias == "" {
		alias = "-"
	}

	serverMode := "on"
	if !isServerModeEnabled {
		serverMode = "off"
	}

	debug := "on"
	if !isDebugEnabled {
		debug = "off"
	}

	port := "-"
	if currentPort != "" {
		port = currentPort
	}

	resultMessage := fmt.Sprintf("\033[33m╭────────────────────────────────────────────────────────────────────────────────────────╮\033[0m\n") +
		fmt.Sprintf("\033[33m│                                                                                        │\033[0m\n") +
		fmt.Sprintf("\033[33m│  host\033[0m %-80s \033[33m│\033[0m\n", host) +
		fmt.Sprintf("\033[33m│  database\033[0m %-76s \033[33m│\033[0m\n", database) +
		fmt.Sprintf("\033[33m│  table\033[0m %-79s \033[33m│\033[0m\n", table) +
		fmt.Sprintf("\033[33m│  alias\033[0m %-79s \033[33m│\033[0m\n", alias) +
		fmt.Sprintf("\033[33m│                                                                                        │\033[0m\n") +
		fmt.Sprintf("\033[33m│  serverMode\033[0m %-74s \033[33m│\033[0m\n", serverMode) +
		fmt.Sprintf("\033[33m│  serverPort\033[0m %-74s \033[33m│\033[0m\n", port) +
		fmt.Sprintf("\033[33m│  debug\033[0m %-79s \033[33m│\033[0m\n", debug) +
		fmt.Sprintf("\033[33m│                                                                                        │\033[0m\n") +
		fmt.Sprintf("\033[33m╰────────────────────────────────────────────────────────────────────────────────────────╯\033[0m")

	return model.SuccessWithResult(resultMessage)
}

func (c *Context) GetDescription() string {
	return "Show current status"
}

func (c *Context) GetType() Type {
	return TypeContext
}
