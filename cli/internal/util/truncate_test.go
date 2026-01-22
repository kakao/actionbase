package util

import "testing"

func TestTruncate(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		maxLen   int
		expected string
	}{
		{"shorter than max", "hello", 10, "hello"},
		{"exact length", "hello", 5, "hello"},
		{"longer than max", "hello world", 8, "hello..."},
		{"empty string", "", 10, ""},
		{"max less than 3", "hello", 2, "..."},
		{"max equals 3", "hello", 3, "..."},
		{"max equals 4", "hello", 4, "h..."},
		{"json truncation", `{"edges":[{"version":1768909550245,"source":"Alice","target":"Phone"}]}`, 60, `{"edges":[{"version":1768909550245,"source":"Alice","targ...`},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := Truncate(tt.input, tt.maxLen)
			if result != tt.expected {
				t.Errorf("Truncate(%q, %d) = %q, want %q", tt.input, tt.maxLen, result, tt.expected)
			}
		})
	}
}
