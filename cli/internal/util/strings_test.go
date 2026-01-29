package util

import "testing"

func TestToString(t *testing.T) {
	tests := []struct {
		name     string
		input    any
		expected string
	}{
		{"int zero", 0, "0"},
		{"int positive", 42, "42"},
		{"int negative", -123, "-123"},
		{"float64 integer", 3.0, "3"},
		{"float64 with decimals", 3.14, "3"},
		{"float64 negative", -2.5, "-2"},
		{"string empty", "", ""},
		{"string normal", "hello", "hello"},
		{"nil", nil, "null"},
		{"bool true", true, "true"},
		{"bool false", false, "false"},
		{"slice", []int{1, 2, 3}, "[1 2 3]"},
		{"map", map[string]int{"a": 1}, "map[a:1]"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := ToString(tt.input)
			if result != tt.expected {
				t.Errorf("ToString(%v) = %q, want %q", tt.input, result, tt.expected)
			}
		})
	}
}