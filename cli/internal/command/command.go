package command

type Command interface {
	Execute(args []string)
	GetDescription() string
	GetType() Type
}
