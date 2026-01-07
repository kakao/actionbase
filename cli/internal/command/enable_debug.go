package command

import (
	"fmt"

	"github.com/kakao/actionbase/internal/command/model"
)

type Debug struct {
	runner DebugRunner
}

type DebugRunner interface {
	SetIsDebugEnabled(debugging bool)
}

func NewDebug(runner DebugRunner) *Debug {
	return &Debug{runner: runner}
}

func (d *Debug) Execute(args []string) *model.Response {
	if len(args) != 1 {
		return model.Fail(fmt.Sprintf("Usage: %s", d.GetType().GetCommand()))
	}

	toggle := args[0]

	switch toggle {
	case "on":
		d.runner.SetIsDebugEnabled(true)
		return model.Success()
	case "off":
		d.runner.SetIsDebugEnabled(false)
		return model.Success()
	default:
		return model.Fail(fmt.Sprintf("Usage: %s", d.GetType().GetCommand()))
	}
}

func (d *Debug) GetDescription() string {
	return "Enable Debugging or not"
}

func (d *Debug) GetType() Type {
	return TypeDebug
}
