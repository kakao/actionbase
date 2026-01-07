package command

import "github.com/kakao/actionbase/internal/client"

type TableCommandRunner interface {
	GetCurrentDatabase() string
	GetCurrentTable() string
	SetCurrentTable(table string)
}

type BaseCommand struct {
	client *client.ActionbaseClient
	runner TableCommandRunner
}

func (b *BaseCommand) GetClient() *client.ActionbaseClient {
	return b.client
}

func (b *BaseCommand) GetRunner() TableCommandRunner {
	return b.runner
}
