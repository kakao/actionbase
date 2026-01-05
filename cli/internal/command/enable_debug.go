package command

import "fmt"

type Debug struct {
	runner DebugRunner
}

type DebugRunner interface {
	SetIsDebugEnabled(debugging bool)
}

func NewDebug(runner DebugRunner) *Debug {
	return &Debug{runner: runner}
}

func (d *Debug) Execute(args []string) {
	if len(args) != 1 {
		fmt.Printf("Usage: %s\n", d.GetType().GetCommand())
		return
	}
	if args[0] == "on" {
		d.runner.SetIsDebugEnabled(true)
		return
	} else if args[1] == "off" {
		d.runner.SetIsDebugEnabled(false)
		return
	}

}

func (d *Debug) GetDescription() string {
	return "Enable Debugging or not"
}

func (d *Debug) GetType() Type {
	return TypeDebug
}
