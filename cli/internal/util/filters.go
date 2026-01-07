package util

func FilterInPlace[T any](s []T, fn func(T) bool) []T {
	n := 0
	for _, v := range s {
		if fn(v) {
			s[n] = v
			n++
		}
	}
	return s[:n]
}
