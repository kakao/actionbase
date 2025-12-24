package command

import "github.com/kakao/actionbase/internal/command/model"

type Command interface {
	Execute(args []string) *model.Result
	GetDescription() string
	GetType() Type
}
