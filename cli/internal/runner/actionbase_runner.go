package runner

import (
	"fmt"
	"log"
	"log/slog"
	"os"
	"strings"
	"time"

	"github.com/chzyer/readline"
	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command"
	"github.com/kakao/actionbase/internal/util"
)

type ActionbaseCommandLineRunner struct {
	logger *slog.Logger
	*CommandLineRunner
	client          *client.ActionbaseClient
	clientContext   *client.Context
	currentDatabase string
	currentAlias    string
	currentTable    string
}

func NewActionbaseCommandLineRunner(host string, authKey *string) *ActionbaseCommandLineRunner {
	logger := util.NewLogger(slog.LevelDebug)
	slog.SetDefault(logger)

	clientContext := client.Context{IsDebugEnabled: false}
	httpClient := client.NewHTTPClient(host, authKey, &clientContext)

	runner := &ActionbaseCommandLineRunner{
		logger:            logger,
		CommandLineRunner: NewCommandLineRunner("Actionbase", "0.0.1"),
		client:            client.NewActionbaseClient(httpClient, &clientContext),
		clientContext:     &clientContext,
		currentDatabase:   "",
		currentAlias:      "",
		currentTable:      "",
	}

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

func (r *ActionbaseCommandLineRunner) Run() {
	r.showBanner()
	command.PrintContext(r.client.GetHost(), r.currentDatabase, r.currentTable, r.currentAlias, r.IsDebugEnabled())

	rl, err := readline.New(defaultPrompt + " ")
	if err != nil {
		log.Fatal(err)
	}
	defer func(rl *readline.Instance) {
		err := rl.Close()
		if err != nil {
		}
	}(rl)

	for {
		var buffer []string
		rl.SetPrompt(fmt.Sprintf("\033[34m%s%s \033[0m", r.BuildPrompt(), defaultPrompt))
		for {
			line, err := rl.Readline()
			if err != nil {
				fmt.Println("\nBye!")
				return
			}

			trimmed := strings.TrimSpace(line)
			if strings.HasSuffix(trimmed, "\\") {
				buffer = append(buffer, trimmed[:len(trimmed)-1])
				rl.SetPrompt("")
				continue
			}

			buffer = append(buffer, line)

			if r.isOpenString(buffer) {
				rl.SetPrompt("")
				continue
			}

			break
		}

		input := strings.Join(buffer, "\n")
		if input == "" {
			continue
		}
		r.runCommand(input)
	}
}

func (r *ActionbaseCommandLineRunner) runCommand(input string) {
	parts := r.parseCommand(input)
	cmdName := parts[0]
	var args []string
	if len(parts) > 1 {
		args = parts[1:]
	}

	start := time.Now()

	r.executeCommand(cmdName, args)

	elapsed := time.Since(start)
	fmt.Printf("\033[90m(Took %.4f seconds)\n\n\033[0m", elapsed.Seconds())
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
	return r.clientContext.IsDebugEnabled
}

func (r *ActionbaseCommandLineRunner) SetCurrentTable(table string) {
	r.currentTable = table
}

func (r *ActionbaseCommandLineRunner) SetCurrentAlias(alias string) {
	r.currentAlias = alias
}

func (r *ActionbaseCommandLineRunner) SetIsDebugEnabled(debugging bool) {
	r.clientContext.IsDebugEnabled = debugging
}

func (r *ActionbaseCommandLineRunner) BuildPrompt() string {
	if r.currentAlias != "" {
		return fmt.Sprintf("%s(%s:%s)", "actionbase", r.currentDatabase, r.currentAlias)
	}

	if r.currentTable != "" {
		return fmt.Sprintf("%s(%s:%s)", "actionbase", r.currentDatabase, r.currentTable)
	}

	if r.currentDatabase != "" {
		return fmt.Sprintf("%s(%s)", "actionbase", r.currentDatabase)
	}

	return r.CommandLineRunner.BuildPrompt()
}

func (r *ActionbaseCommandLineRunner) CheckConnection() {
	response := r.client.GetTenant()
	if response == nil {
		fmt.Println("Connection Failed. Check if a server is available")
		os.Exit(0)
	}
}

func (r *ActionbaseCommandLineRunner) showBanner() {
	banner := "    _        _   _             _\n" +
		"   / \\   ___| |_(_) ___  _ __ | |__   __ _ ___  ___\n" +
		"  / _ \\ / __| __| |/ _ \\| '_ \\| '_ \\ / _` / __|/ _ \\\n" +
		" / ___ \\ (__| |_| | (_) | | | | |_) | (_| \\__ \\  __/\n" +
		"/_/   \\_\\___|\\__|_|\\___/|_| |_|_.__/ \\__,_|___/\\___|\n"

	fmt.Printf("\033[33m%s\033[0m\n", banner)
}

func (r *ActionbaseCommandLineRunner) isOpenString(buffer []string) bool {
	fullInput := strings.Join(buffer, "\n")
	singleQuotes := strings.Count(fullInput, "'")
	doubleQuotes := strings.Count(fullInput, `"`)
	return singleQuotes%2 != 0 || doubleQuotes%2 != 0
}
