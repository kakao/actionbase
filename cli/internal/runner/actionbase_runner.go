package runner

import (
	"fmt"
	"os"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command"
	"github.com/kakao/actionbase/internal/util"
)

// ActionbaseCommandLineRunner represents an Actionbase-specific CLI runner
type ActionbaseCommandLineRunner struct {
	*CommandLineRunner
	client          *client.ActionbaseClient
	clientContext   *client.Context
	currentDatabase string
	currentAlias    string
	currentTable    string
}

// NewActionbaseCommandLineRunner creates a new ActionbaseCommandLineRunner instance
func NewActionbaseCommandLineRunner(host string, authKey *string) *ActionbaseCommandLineRunner {
	clientContext := client.Context{IsDebuggingEnabled: false}
	runner := &ActionbaseCommandLineRunner{
		CommandLineRunner: NewCommandLineRunner("Actionbase", "0.0.1"),
		client:            client.NewActionbaseClient(client.NewHTTPClient(host, authKey, &clientContext)),
		clientContext:     &clientContext,
		currentDatabase:   "",
		currentAlias:      "",
		currentTable:      "",
	}

	// Register Actionbase-specific commands
	actionbaseClient := runner.client

	runner.RegisterCommand(command.TypeContext.GetName(), command.NewContext(runner, actionbaseClient))
	runner.RegisterCommand(command.TypeCreate.GetName(), command.NewCreate(actionbaseClient))
	runner.RegisterCommand(command.TypeShow.GetName(), command.NewShow(runner, actionbaseClient))
	runner.RegisterCommand(command.TypeUse.GetName(), command.NewUse(runner, actionbaseClient))
	runner.RegisterCommand(command.TypeDesc.GetName(), command.NewDesc(runner, actionbaseClient))
	runner.RegisterCommand(command.TypeMutate.GetName(), command.NewMutate(runner, actionbaseClient))
	runner.RegisterCommand(command.TypeGet.GetName(), command.NewGet(runner, actionbaseClient))
	runner.RegisterCommand(command.TypeScan.GetName(), command.NewScan(runner, actionbaseClient))
	runner.RegisterCommand(command.TypeCount.GetName(), command.NewCount(runner, actionbaseClient))
	runner.RegisterCommand(command.TypeLoad.GetName(), command.NewLoad(runner, actionbaseClient))
	runner.RegisterCommand(command.TypeDebug.GetName(), command.NewDebug(runner))

	return runner
}

func (r *ActionbaseCommandLineRunner) GetCurrentDatabase() string {
	return r.currentDatabase
}

func (r *ActionbaseCommandLineRunner) SetCurrentDatabase(database string) {
	r.currentDatabase = database
}

func (r *ActionbaseCommandLineRunner) GetCurrentTable() string {
	return r.currentTable
}

func (r *ActionbaseCommandLineRunner) GetCurrentAlias() string {
	return r.currentAlias
}

func (r *ActionbaseCommandLineRunner) IsDebugEnabled() bool {
	return r.clientContext.IsDebuggingEnabled
}

func (r *ActionbaseCommandLineRunner) SetCurrentTable(table string) {
	r.currentTable = table
}

func (r *ActionbaseCommandLineRunner) SetCurrentAlias(alias string) {
	r.currentAlias = alias
}

func (r *ActionbaseCommandLineRunner) SetIsDebugEnabled(debugging bool) {
	r.clientContext.IsDebuggingEnabled = debugging
}

func (r *ActionbaseCommandLineRunner) ShowBanner() {
	separator := util.RepeatChar('=', 60)
	fmt.Println(separator)
	fmt.Println(r.name + " Console")
	fmt.Println(separator)
	fmt.Println("Type 'help' for available commands, 'exit' to quit.")
}

func (r *ActionbaseCommandLineRunner) BuildPrompt() string {
	if r.currentAlias != "" {
		return fmt.Sprintf("%s(%s:%s)> ", "actionbase", r.currentDatabase, r.currentAlias)
	}

	if r.currentTable != "" {
		return fmt.Sprintf("%s(%s:%s)> ", "actionbase", r.currentDatabase, r.currentTable)
	}

	if r.currentDatabase != "" {
		return fmt.Sprintf("%s(%s)> ", "actionbase", r.currentDatabase)
	}

	return r.CommandLineRunner.BuildPrompt()
}

func (r *ActionbaseCommandLineRunner) CheckConnection() {
	response := r.client.GetTenant()
	if response.Error != nil {
		fmt.Println("Connection Failed. Check if a server is available")
		os.Exit(0)
	}

	fmt.Printf("Connected to %s\n", r.client.GetHost())
}
