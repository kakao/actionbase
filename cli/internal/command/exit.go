package command

import (
	"fmt"
)

type Exit struct {
	runner ExitRunner
}

type ExitRunner interface {
	SetRunning(running bool)
}

func NewExit(runner ExitRunner) *Exit {
	return &Exit{runner: runner}
}

func (e *Exit) Execute(args []string) {
	fmt.Println("Goodbye!")
	e.runner.SetRunning(false)
}

func (e *Exit) GetDescription() string {
	return "Exit the console"
}

func (e *Exit) GetType() Type {
	return TypeExit
}
