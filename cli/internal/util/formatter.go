package util

import "strconv"

func Int64WithCommas(n int64) string {
	s := strconv.FormatInt(n, 10)
	if len(s) <= 3 {
		return s
	}

	var result []byte
	count := 0

	for i := len(s) - 1; i >= 0; i-- {
		result = append([]byte{s[i]}, result...)
		count++
		if count%3 == 0 && i != 0 {
			result = append([]byte{','}, result...)
		}
	}
	return string(result)
}
