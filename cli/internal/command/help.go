package command

import (
	"fmt"

	"github.com/kakao/actionbase/internal/util"
)

type Help struct {
	runner HelpRunner
}

type HelpRunner interface {
	GetCommands() map[string]Command
}

func NewHelp(runner HelpRunner) *Help {
	return &Help{runner: runner}
}

func (h *Help) Execute(_ []string) {
	var commands []map[string]interface{}

	for name, cmd := range h.runner.GetCommands() {
		help := map[string]interface{}{
			"name":        name,
			"description": cmd.GetDescription(),
			"usage":       "`" + cmd.GetType().GetCommand() + "`",
		}
		commands = append(commands, help)
	}

	fmt.Println("\nAvailable commands:")
	columnOrder := []string{"name", "description", "usage"}
	fmt.Println(util.PrettyPrintRowsWithOrder(commands, columnOrder))
}

func (h *Help) GetDescription() string {
	return "Show available commands"
}

func (h *Help) GetType() Type {
	return TypeHelp
}
