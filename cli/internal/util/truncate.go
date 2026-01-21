package util

// Truncate returns a string truncated to maxLen characters with "..." suffix.
// If the string is shorter than or equal to maxLen, it returns the original string.
// If maxLen is less than 3, it returns "..." to ensure the suffix is always present.
func Truncate(s string, maxLen int) string {
	if maxLen < 3 {
		return "..."
	}
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen-3] + "..."
}
