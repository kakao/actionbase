package command

import (
	"github.com/kakao/actionbase/internal/command/model"
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

func (h *Help) Execute(_ []string) *model.Response {
	var commands []map[string]interface{}

	for name, cmd := range h.runner.GetCommands() {
		help := map[string]interface{}{
			"name":        name,
			"description": cmd.GetDescription(),
			"usage":       "`" + cmd.GetType().GetCommand() + "`",
		}
		commands = append(commands, help)
	}

	columnOrder := []string{"name", "description", "usage"}
	resultMessage := "\nAvailable commands\n" + util.PrettyPrintRowsWithOrder(commands, columnOrder)
	return model.SuccessWithResult(resultMessage)
}

func (h *Help) GetDescription() string {
	return "Show available commands"
}

func (h *Help) GetType() Type {
	return TypeHelp
}
