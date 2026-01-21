package util

import (
	"fmt"
	"strings"
	"sync/atomic"
)

const OutputPrefix = "  \u2502 "

var plainMode atomic.Bool

// SetPlainMode enables or disables plain output mode.
// When enabled, output will not have the prefix.
func SetPlainMode(enabled bool) {
	plainMode.Store(enabled)
}

func getPrefix() string {
	if plainMode.Load() {
		return ""
	}
	return OutputPrefix
}

// Print prints formatted output with the standard prefix for CLI output.
func Print(format string, args ...any) {
	msg := fmt.Sprintf(format, args...)
	printWithPrefix(msg, false)
}

// Println prints a line with the standard prefix for CLI output.
// Each line in the message will have the prefix prepended.
func Println(msg string) {
	printWithPrefix(msg, true)
}

// PrintEmpty prints an empty line with the standard prefix.
func PrintEmpty() {
	fmt.Println(getPrefix())
}

func printWithPrefix(msg string, newline bool) {
	prefix := getPrefix()
	lines := strings.Split(msg, "\n")
	for i, line := range lines {
		if i == len(lines)-1 && line == "" {
			// Skip trailing empty line from Split
			continue
		}
		fmt.Print(prefix + line)
		if newline || i < len(lines)-1 {
			fmt.Println()
		}
	}
}
