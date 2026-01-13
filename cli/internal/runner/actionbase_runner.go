package runner

import (
	"encoding/json"
	"fmt"
	"log"
	"log/slog"
	"net/http"
	"os"
	"strings"
	"sync/atomic"
	"time"

	"github.com/chzyer/readline"
	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/httpserver"
	"github.com/kakao/actionbase/internal/util"
)

const (
	DefaultServerPort = "9300"
)

type ActionbaseCommandLineRunner struct {
	ReadLine *readline.Instance
	logger   *slog.Logger
	*CommandLineRunner
	handler         *atomic.Value
	client          *client.ActionbaseClient
	clientContext   *client.Context
	currentDatabase string
	currentAlias    string
	currentTable    string
	currentPort     string
}

func NewActionbaseCommandLineRunner(host string, authKey *string, currentPort string, IsServerEnabled bool) *ActionbaseCommandLineRunner {
	logger := util.NewLogger(slog.LevelDebug)
	slog.SetDefault(logger)

	clientContext := client.Context{IsServerModeEnabled: IsServerEnabled, IsDebugEnabled: false}
	httpClient := client.NewHTTPClient(host, authKey, &clientContext)

	runner := &ActionbaseCommandLineRunner{
		logger:            logger,
		CommandLineRunner: NewCommandLineRunner("Actionbase", "0.0.1"),
		client:            client.NewActionbaseClient(httpClient, &clientContext),
		clientContext:     &clientContext,
		currentDatabase:   "",
		currentAlias:      "",
		currentTable:      "",
		currentPort:       currentPort,
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
	runner.RegisterCommand(command.TypeGuide.GetName(), command.NewGuide(runner, actionbaseClient))

	return runner
}

func (r *ActionbaseCommandLineRunner) Run() {
	r.showBanner()
	command.PrintContext(r.client.GetHost(), r.currentDatabase, r.currentTable, r.currentAlias, r.GetCurrentPort(), r.IsServerModeEnabled(), r.IsDebugEnabled())

	rl, err := readline.New(defaultPrompt + " ")
	if err != nil {
		log.Fatal(err)
	}
	defer func(rl *readline.Instance) {
		err := rl.Close()
		if err != nil {
		}
	}(rl)

	r.ReadLine = rl
	for {
		if !r.running {
			return
		}

		rl.SetPrompt(fmt.Sprintf("\033[34m%s%s \033[0m", r.BuildPrompt(), defaultPrompt))

		buffer, exit := r.readLines(rl)
		if exit {
			return
		}

		input := strings.Join(buffer, "\n")
		if input == "" {
			continue
		}

		r.RunCommand(input)
	}
}

func (r *ActionbaseCommandLineRunner) readLines(rl *readline.Instance) ([]string, bool) {
	var buffer []string

	for {
		line, err := rl.Readline()
		if err != nil {
			fmt.Println("\nBye!")
			return nil, true
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
	return buffer, false
}

func (r *ActionbaseCommandLineRunner) RunCommand(input string) (*model.Response, float64) {
	parts := r.parseCommand(input)
	cmdName := parts[0]
	var args []string
	if len(parts) > 1 {
		args = parts[1:]
	}

	start := time.Now()
	result := r.executeCommand(cmdName, args)
	elapsed := time.Since(start).Seconds()

	fmt.Printf("\033[90m(Took %.4f seconds)\n\n\033[0m", elapsed)
	return result, elapsed
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

func (r *ActionbaseCommandLineRunner) IsServerModeEnabled() bool {
	return r.clientContext.IsServerModeEnabled
}

func (r *ActionbaseCommandLineRunner) IsDebugEnabled() bool {
	return r.clientContext.IsDebugEnabled
}

func (r *ActionbaseCommandLineRunner) GetCurrentPort() string {
	return r.currentPort
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

func (r *ActionbaseCommandLineRunner) SetIsServerModeEnabled(isServerModeEnabled bool) {
	r.clientContext.IsServerModeEnabled = isServerModeEnabled
}

func (r *ActionbaseCommandLineRunner) SetCurrentPort(port string) {
	r.currentPort = port
}

func (r *ActionbaseCommandLineRunner) GetHandler() *atomic.Value {
	return r.handler
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

	if response.IsError() {
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

func (r *ActionbaseCommandLineRunner) StartServer(parser *util.Parser) {
	port := r.getServerPort(parser)
	if port == "" {
		return
	}

	serverReady := make(chan error, 1)
	go func() {
		if err := httpserver.Start(port, serverReady, r.commandHandler); err != nil {
			fmt.Printf("Failed to start actionbase as server mode. %v\n", err)
			os.Exit(1)
		}
	}()

	if err := <-serverReady; err != nil {
		os.Exit(1)
	}

	r.SetCurrentPort(port)
	r.SetIsServerModeEnabled(true)
}

func (r *ActionbaseCommandLineRunner) getServerPort(parser *util.Parser) string {
	if port, found := parser.Get("proxy"); found {
		return port
	}
	if _, found := parser.GetLenient("proxy"); found {
		return DefaultServerPort
	}
	return ""
}

func (r *ActionbaseCommandLineRunner) commandHandler(w http.ResponseWriter, req *http.Request) {
	if req.Method != http.MethodPost {
		fmt.Println("Method not allowed")
		httpserver.BuildResponse(w, http.StatusMethodNotAllowed, nil)
		return
	}

	var request httpserver.CommandRequest
	if err := json.NewDecoder(req.Body).Decode(&request); err != nil {
		errorMessage := fmt.Sprintf("Invalid request: %v", err)
		httpserver.BuildResponse(
			w,
			http.StatusBadRequest,
			&httpserver.CommandResponse{
				Success: false,
				Error:   &errorMessage,
			})
		return
	}

	if request.Command == "" {
		errorMessage := "Command is required"

		httpserver.BuildResponse(
			w,
			http.StatusBadRequest,
			&httpserver.CommandResponse{
				Success: false,
				Error:   &errorMessage,
			})
		return
	}

	normalizedCommand := strings.TrimSpace(request.Command)

	r.ReadLine.Terminal.Print(fmt.Sprintf("\n\033[38;5;208m%s>\033[0m %s\n", r.BuildPrompt(), normalizedCommand))
	result, elapsed := r.RunCommand(normalizedCommand)

	var response httpserver.CommandResponse
	if result == nil {
		errorMessage := "Unsupported command"
		response = httpserver.CommandResponse{
			Success: false,
			Elapsed: fmt.Sprintf("%.4f seconds", elapsed),
			Error:   &errorMessage,
		}
	} else {
		response = httpserver.CommandResponse{
			Success: result.IsSuccess,
			Elapsed: fmt.Sprintf("%.4f seconds", elapsed),
			Result:  result.Result,
			Error:   result.ErrorMessage,
		}
	}

	statusCode := http.StatusOK
	if !response.Success {
		statusCode = http.StatusInternalServerError
	}

	httpserver.BuildResponse(w, statusCode, &response)

	r.ReadLine.SetPrompt(fmt.Sprintf("\033[34m%s%s \033[0m", r.BuildPrompt(), defaultPrompt))
	r.ReadLine.Terminal.Print(fmt.Sprintf("\033[34m%s>\033[0m ", r.BuildPrompt()))
}
