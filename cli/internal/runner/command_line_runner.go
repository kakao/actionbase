package runner

import (
	"bufio"
	"fmt"
	"os"
	"strings"

	"github.com/kakao/actionbase/internal/command"
	"github.com/kakao/actionbase/internal/command/model"
)

const (
	defaultPrompt = ">"
)

type CommandLineRunner struct {
	name     string
	running  bool
	commands map[string]command.Command
	reader   *bufio.Reader
	prompt   string
}

func NewCommandLineRunner(name, version string) *CommandLineRunner {
	runner := &CommandLineRunner{
		name:     name,
		running:  true,
		commands: make(map[string]command.Command),
		reader:   bufio.NewReader(os.Stdin),
		prompt:   name + defaultPrompt,
	}

	// Register default commands
	runner.RegisterCommand(command.TypeHelp.GetName(), command.NewHelp(runner))
	runner.RegisterCommand(command.TypeExit.GetName(), command.NewExit(runner))

	return runner
}

func (r *CommandLineRunner) RegisterCommand(name string, cmd command.Command) {
	r.commands[strings.ToLower(name)] = cmd
}

func (r *CommandLineRunner) GetCommands() map[string]command.Command {
	return r.commands
}

func (r *CommandLineRunner) SetRunning(running bool) {
	r.running = running
}

func (r *CommandLineRunner) BuildPrompt() string {
	return strings.ToLower(r.name)
}

func (r *CommandLineRunner) parseCommand(line string) []string {
	return strings.Fields(strings.TrimSpace(line))
}

func (r *CommandLineRunner) executeCommand(cmdName string, args []string) *model.Response {
	cmd, ok := r.commands[strings.ToLower(cmdName)]

	if ok {
		defer func() {
			if rec := recover(); rec != nil {
				fmt.Printf("Error executing command: %v\n", rec)
			}
		}()
		return cmd.Execute(args)
	}
	fmt.Println("Unknown command: " + cmdName)
	fmt.Println("Type 'help' for available commands.")

	return nil
}
