package util

import (
	"reflect"
	"strings"
	"testing"
)

func TestReplaceTimestampInString(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected func(string) bool // since timestamp is dynamic
	}{
		{
			name:  "no placeholder",
			input: "hello world",
			expected: func(result string) bool {
				return result == "hello world"
			},
		},
		{
			name:  "with placeholder",
			input: "timestamp: __CURRENT_TIMESTAMP__",
			expected: func(result string) bool {
				return strings.HasPrefix(result, "timestamp: ") && strings.Contains(result, "timestamp: ") && len(result) > len("timestamp: ")
			},
		},
		{
			name:  "multiple placeholders",
			input: "__CURRENT_TIMESTAMP__ and __CURRENT_TIMESTAMP__",
			expected: func(result string) bool {
				parts := strings.Split(result, " and ")
				return len(parts) == 2 && parts[0] == parts[1] && len(parts[0]) > 0
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := ReplaceTimestampInString(tt.input)
			if !tt.expected(result) {
				t.Errorf("ReplaceTimestampInString(%q) = %q, did not match expected condition", tt.input, result)
			}
		})
	}
}

func TestParseArgs(t *testing.T) {
	tests := []struct {
		name     string
		args     []string
		expected map[string]string
	}{
		{
			name:     "empty args",
			args:     []string{},
			expected: map[string]string{},
		},
		{
			name:     "key=value",
			args:     []string{"--key=value"},
			expected: map[string]string{"key": "value"},
		},
		{
			name:     "key value",
			args:     []string{"--key", "value"},
			expected: map[string]string{"key": "value"},
		},
		{
			name:     "key with multiple values",
			args:     []string{"--key", "val1", "val2"},
			expected: map[string]string{"key": "val1 val2"},
		},
		{
			name:     "flag without value",
			args:     []string{"--flag"},
			expected: map[string]string{"flag": ""},
		},
		{
			name:     "multiple keys",
			args:     []string{"--a=1", "--b", "2", "--c", "3", "4"},
			expected: map[string]string{"a": "1", "b": "2", "c": "3 4"},
		},
		{
			name:     "key with equals in value",
			args:     []string{"--key=a=b=c"},
			expected: map[string]string{"key": "a=b=c"},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			parser := ParseArgs(tt.args)
			if !reflect.DeepEqual(parser.values, tt.expected) {
				t.Errorf("ParseArgs(%v) = %v, want %v", tt.args, parser.values, tt.expected)
			}
		})
	}
}

func TestParser_Get(t *testing.T) {
	parser := &Parser{values: map[string]string{
		"present": "value",
		"empty":   "",
	}}

	tests := []struct {
		name     string
		key      string
		expected string
		ok       bool
	}{
		{"present", "present", "value", true},
		{"empty", "empty", "", false},
		{"missing", "missing", "", false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, ok := parser.Get(tt.key)
			if result != tt.expected || ok != tt.ok {
				t.Errorf("Get(%q) = (%q, %v), want (%q, %v)", tt.key, result, ok, tt.expected, tt.ok)
			}
		})
	}
}

func TestParser_GetLenient(t *testing.T) {
	parser := &Parser{values: map[string]string{
		"present": "value",
		"empty":   "",
	}}

	tests := []struct {
		name     string
		key      string
		expected string
		ok       bool
	}{
		{"present", "present", "value", true},
		{"empty", "empty", "", true},
		{"missing", "missing", "", false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, ok := parser.GetLenient(tt.key)
			if result != tt.expected || ok != tt.ok {
				t.Errorf("GetLenient(%q) = (%q, %v), want (%q, %v)", tt.key, result, ok, tt.expected, tt.ok)
			}
		})
	}
}

func TestParser_GetParsed(t *testing.T) {
	parser := &Parser{values: map[string]string{
		"string":     "hello",
		"quoted":     `"world"`,
		"single":     "'test'",
		"number":     "42",
		"float":      "3.14",
		"boolean":    "true",
		"null":       "null",
		"array":      `[1,2,3]`,
		"object":     `{"key":"value"}`,
		"invalid":    `{"invalid":}`,
		"whitespace": "  spaced  ",
	}}

	tests := []struct {
		name     string
		key      string
		expected interface{}
		ok       bool
	}{
		{"string", "string", "hello", true},
		{"quoted double", "quoted", "world", true},
		{"quoted single", "single", "test", true},
		{"number", "number", float64(42), true},
		{"float", "float", 3.14, true},
		{"boolean", "boolean", true, true},
		{"null", "null", nil, true},
		{"array", "array", []interface{}{float64(1), float64(2), float64(3)}, true},
		{"object", "object", map[string]interface{}{"key": "value"}, true},
		{"invalid json", "invalid", `{"invalid":}`, true},
		{"whitespace", "whitespace", "spaced", true}, // trimmed and no quotes
		{"missing", "missing", nil, false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, ok := parser.GetParsed(tt.key)
			if !reflect.DeepEqual(result, tt.expected) || ok != tt.ok {
				t.Errorf("GetParsed(%q) = (%v, %v), want (%v, %v)", tt.key, result, ok, tt.expected, tt.ok)
			}
		})
	}
}