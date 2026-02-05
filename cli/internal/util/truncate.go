package util

// Truncate returns a string truncated to maxLen runes with "..." suffix.
// If the string is shorter than or equal to maxLen, it returns the original string.
// If maxLen is less than 3, it returns "..." to ensure the suffix is always present.
func Truncate(s string, maxLen int) string {
	if maxLen < 3 {
		return "..."
	}
	runes := []rune(s)
	if len(runes) <= maxLen {
		return s
	}
	return string(runes[:maxLen-3]) + "..."
}
