package command

import (
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/util"
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

func (e *Exit) Execute(_ []string) *model.Response {
	util.Println("Goodbye!")
	e.runner.SetRunning(false)

	return model.Success()
}

func (e *Exit) GetDescription() string {
	return "Exit the console"
}

func (e *Exit) GetType() Type {
	return TypeExit
}
