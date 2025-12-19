package runner

import (
	"bufio"
	"fmt"
	"log"
	"os"
	"strings"
	"time"

	"github.com/chzyer/readline"
	"github.com/kakao/actionbase/internal/command"
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
		running:  false,
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

func (r *ActionbaseCommandLineRunner) Run() {
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

			if isOpenString(buffer) {
				rl.SetPrompt("")
				continue
			}

			break
		}

		r.runCommand(strings.Join(buffer, "\n"))
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

func (r *CommandLineRunner) parseCommand(line string) []string {
	return strings.Fields(strings.TrimSpace(line))
}

func (r *CommandLineRunner) executeCommand(cmdName string, args []string) {
	cmd, ok := r.commands[strings.ToLower(cmdName)]

	if ok {
		defer func() {
			if rec := recover(); rec != nil {
				fmt.Printf("Error executing command: %v\n", rec)
			}
		}()
		cmd.Execute(args)
	} else {
		fmt.Println("Unknown command: " + cmdName)
		fmt.Println("Type 'help' for available commands.")
	}
}

func isOpenString(buffer []string) bool {
	fullInput := strings.Join(buffer, "\n")
	singleQuotes := strings.Count(fullInput, "'")
	doubleQuotes := strings.Count(fullInput, `"`)
	return singleQuotes%2 != 0 || doubleQuotes%2 != 0
}
