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

	toggle := args[0]

	switch toggle {
	case "on":
		d.runner.SetIsDebugEnabled(true)
	case "off":
		d.runner.SetIsDebugEnabled(false)
	default:
		fmt.Printf("Usage: %s\n", d.GetType().GetCommand())
	}
}

func (d *Debug) GetDescription() string {
	return "Enable Debugging or not"
}

func (d *Debug) GetType() Type {
	return TypeDebug
}
