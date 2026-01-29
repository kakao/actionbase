package util

import "testing"

func TestInt64WithCommas(t *testing.T) {
	tests := []struct {
		name     string
		input    int64
		expected string
	}{
		{"zero", 0, "0"},
		{"small positive", 123, "123"},
		{"exactly three digits", 999, "999"},
		{"four digits", 1000, "1,000"},
		{"large number", 123456789, "123,456,789"},
		{"million", 1000000, "1,000,000"},
		{"negative small", -123, "-123"},
		{"negative four digits", -1000, "-1,000"},
		{"negative large", -123456789, "-123,456,789"},
		{"min int64", -9223372036854775808, "-9,223,372,036,854,775,808"},
		{"max int64", 9223372036854775807, "9,223,372,036,854,775,807"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := Int64WithCommas(tt.input)
			if result != tt.expected {
				t.Errorf("Int64WithCommas(%d) = %q, want %q", tt.input, result, tt.expected)
			}
		})
	}
}