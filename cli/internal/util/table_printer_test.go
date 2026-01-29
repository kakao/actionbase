package util

import (
	"strings"
	"testing"
)

func TestPrettyPrintWithOrder(t *testing.T) {
	tests := []struct {
		name        string
		data        map[string]interface{}
		orderedKeys []string
		check       func(string) bool
	}{
		{
			name:        "single row",
			data:        map[string]interface{}{"name": "Alice", "age": 30},
			orderedKeys: []string{"name", "age"},
			check: func(output string) bool {
				return strings.Contains(output, "NAME") && strings.Contains(output, "AGE") &&
					strings.Contains(output, "Alice") && strings.Contains(output, "30")
			},
		},
		{
			name:        "with nil value",
			data:        map[string]interface{}{"key": nil},
			orderedKeys: []string{"key"},
			check: func(output string) bool {
				return strings.Contains(output, "KEY") && strings.Contains(output, "null")
			},
		},
		{
			name:        "empty map",
			data:        map[string]interface{}{},
			orderedKeys: []string{"a", "b"},
			check: func(output string) bool {
				return strings.Contains(output, "A") && strings.Contains(output, "B") && strings.Contains(output, "null")
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := PrettyPrintWithOrder(tt.data, tt.orderedKeys)
			if !tt.check(result) {
				t.Errorf("PrettyPrintWithOrder() output = %q, check failed", result)
			}
		})
	}
}

func TestPrettyPrintRowsWithOrder(t *testing.T) {
	tests := []struct {
		name        string
		rows        []map[string]interface{}
		orderedKeys []string
		check       func(string) bool
	}{
		{
			name: "multiple rows",
			rows: []map[string]interface{}{
				{"name": "Alice", "age": 30},
				{"name": "Bob", "age": 25},
			},
			orderedKeys: []string{"name", "age"},
			check: func(output string) bool {
				return strings.Contains(output, "NAME") && strings.Contains(output, "AGE") &&
					strings.Contains(output, "Alice") && strings.Contains(output, "30") &&
					strings.Contains(output, "Bob") && strings.Contains(output, "25")
			},
		},
		{
			name:        "empty rows",
			rows:        []map[string]interface{}{},
			orderedKeys: []string{"a", "b"},
			check: func(output string) bool {
				return strings.Contains(output, "A") && strings.Contains(output, "B")
			},
		},
		{
			name: "rows with nil",
			rows: []map[string]interface{}{
				{"key": "value"},
				{"key": nil},
			},
			orderedKeys: []string{"key"},
			check: func(output string) bool {
				return strings.Contains(output, "KEY") && strings.Contains(output, "value") && strings.Contains(output, "null")
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := PrettyPrintRowsWithOrder(tt.rows, tt.orderedKeys)
			if !tt.check(result) {
				t.Errorf("PrettyPrintRowsWithOrder() output = %q, check failed", result)
			}
		})
	}
}