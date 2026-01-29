package util

import (
	"reflect"
	"testing"
)

func TestFilterInPlace(t *testing.T) {
	t.Run("int", func(t *testing.T) {
		tests := []struct {
			name     string
			input    []int
			fn       func(int) bool
			expected []int
		}{
			{
				name:     "empty slice",
				input:    []int{},
				fn:       func(i int) bool { return true },
				expected: []int{},
			},
			{
				name:     "no elements pass",
				input:    []int{1, 2, 3},
				fn:       func(i int) bool { return i > 10 },
				expected: []int{},
			},
			{
				name:     "all elements pass",
				input:    []int{1, 2, 3},
				fn:       func(i int) bool { return i > 0 },
				expected: []int{1, 2, 3},
			},
			{
				name:     "some elements pass",
				input:    []int{1, 2, 3, 4, 5},
				fn:       func(i int) bool { return i%2 == 0 },
				expected: []int{2, 4},
			},
		}

		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				original := make([]int, len(tt.input))
				copy(original, tt.input)
				result := FilterInPlace(tt.input, tt.fn)
				if !reflect.DeepEqual(result, tt.expected) {
					t.Errorf("FilterInPlace() = %v, want %v", result, tt.expected)
				}
				// Check that the slice was modified in place
				for i := 0; i < len(result); i++ {
					if tt.input[i] != result[i] {
						t.Errorf("Slice not modified in place at index %d: got %v, want %v", i, tt.input[i], result[i])
					}
				}
				// Original elements beyond the result should be the same as input, but since in place, they are overwritten if filtered
				// Actually, for in place, the filtered ones are at the beginning, and the rest are the original unfiltered
				// But since we copied original, perhaps no need, but to ensure, maybe check the length
				if len(result) != len(tt.expected) {
					t.Errorf("Result length = %d, want %d", len(result), len(tt.expected))
				}
			})
		}
	})

	t.Run("string", func(t *testing.T) {
		tests := []struct {
			name     string
			input    []string
			fn       func(string) bool
			expected []string
		}{
			{
				name:     "empty slice",
				input:    []string{},
				fn:       func(s string) bool { return true },
				expected: []string{},
			},
			{
				name:     "no elements pass",
				input:    []string{"a", "b", "c"},
				fn:       func(s string) bool { return len(s) > 5 },
				expected: []string{},
			},
			{
				name:     "all elements pass",
				input:    []string{"hello", "world"},
				fn:       func(s string) bool { return len(s) > 0 },
				expected: []string{"hello", "world"},
			},
			{
				name:     "some elements pass",
				input:    []string{"", "a", "", "bc", ""},
				fn:       func(s string) bool { return len(s) > 0 },
				expected: []string{"a", "bc"},
			},
		}

		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				result := FilterInPlace(tt.input, tt.fn)
				if !reflect.DeepEqual(result, tt.expected) {
					t.Errorf("FilterInPlace() = %v, want %v", result, tt.expected)
				}
			})
		}
	})
}