package command

import "github.com/kakao/actionbase/internal/command/model"

type Command interface {
	Execute(args []string) *model.Response
	GetDescription() string
	GetType() Type
}
