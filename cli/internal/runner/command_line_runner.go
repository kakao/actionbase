package runner

import (
	"bufio"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/kakao/actionbase/internal/command"
	"github.com/peterh/liner"
)

const (
	historyFileName = ".curl_repl_history"

	defaultPrompt = "> "

	singleQuote        = '\''
	doubleQuote        = '"'
	leftCurlyBrace     = '{'
	rightCurlyBrace    = '}'
	leftSquareBracket  = '['
	rightSquareBracket = ']'
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
	return fmt.Sprintf("%s%s", strings.ToLower(r.name), defaultPrompt)
}

func (r *ActionbaseCommandLineRunner) Run() {
	state := liner.NewLiner()
	defer func(l *liner.State) {
		err := l.Close()
		if err != nil {
		}
	}(state)
	state.SetCtrlCAborts(true)

	if f, err := os.Open(historyPath()); err == nil {
		_, _ = state.ReadHistory(f)
		err := f.Close()
		if err != nil {
			return
		}
	}

	r.readLines(state)
}

func (r *ActionbaseCommandLineRunner) runCommand(finalInput string) {
	parts := r.parseCommand(finalInput)
	cmdName := parts[0]
	var args []string
	if len(parts) > 1 {
		args = parts[1:]
	}

	start := time.Now()

	r.executeCommand(cmdName, args)

	elapsed := time.Since(start)
	fmt.Printf("Took %.4f seconds\n", elapsed.Seconds())
}

func (r *ActionbaseCommandLineRunner) readLines(state *liner.State) {
	for {
		var b strings.Builder
		currPrompt := r.BuildPrompt()

		for {
			line, err := state.Prompt(currPrompt)
			if errors.Is(err, liner.ErrPromptAborted) {
				fmt.Println("\n^C")
				writeHistory(state)
				os.Exit(0)
			}
			if err == io.EOF {
				fmt.Println()
				writeHistory(state)
				return
			}

			trimmed := strings.TrimSpace(line)
			if trimmed == "exit" {
				writeHistory(state)
				fmt.Println("Bye!")
				return
			}

			// multiline: backslash ending
			if strings.HasSuffix(line, "\\") && !strings.HasSuffix(line, "\\\\") {
				line = line[:len(line)-1]
				b.WriteString(line)
				b.WriteString("\n")
				currPrompt = ""
				continue
			}

			b.WriteString(line)
			b.WriteString("\n")

			// analyze unbalanced quotes or json
			inS, inD, jsonUnbalanced := analyzeBuffer(b.String())
			if jsonUnbalanced {
				if !inS && !inD {
					currPrompt = ""
				} else {
					currPrompt = ""
					continue
				}
			} else if inS {
				currPrompt = ""
				continue
			} else if inD {
				currPrompt = ""
				continue
			}

			break
		}

		entry := strings.TrimSuffix(b.String(), "\n")
		if strings.TrimSpace(entry) == "" {
			continue
		}

		scanner := bufio.NewScanner(strings.NewReader(entry))
		input := ""
		for scanner.Scan() {
			input += scanner.Text()
		}

		state.AppendHistory(entry)

		r.runCommand(input)
	}
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

func historyPath() string {
	home, err := os.UserHomeDir()
	if err != nil {
		return historyFileName
	}
	return filepath.Join(home, historyFileName)
}

func analyzeBuffer(s string) (inSingle, inDouble bool, jsonUnbalanced bool) {
	inSingle = false
	inDouble = false
	escaped := false

	braces := 0
	brackets := 0

	for _, r := range s {
		if r == '\\' && !escaped {
			escaped = true
			continue
		}

		if r == singleQuote && !escaped && !inDouble {
			inSingle = !inSingle
		}

		if r == doubleQuote && !escaped && !inSingle {
			inDouble = !inDouble
		}

		switch r {
		case leftCurlyBrace:
			braces++
		case rightCurlyBrace:
			braces--
		case leftSquareBracket:
			brackets++
		case rightSquareBracket:
			brackets--
		}
		escaped = false
	}

	jsonUnbalanced = braces != 0
	return
}

func writeHistory(l *liner.State) {
	if f, err := os.Create(historyPath()); err == nil {
		_, _ = l.WriteHistory(f)
		err := f.Close()
		if err != nil {
			return
		}
	}
}
