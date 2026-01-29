package util

import "strconv"

func Int64WithCommas(n int64) string {
	sign := ""
	abs := uint64(n)
	if n < 0 {
		sign = "-"
		abs = uint64(-n)
	}
	s := strconv.FormatUint(abs, 10)
	if len(s) <= 3 {
		return sign + s
	}

	commaCount := (len(s) - 1) / 3
	result := make([]byte, len(s)+commaCount)

	resultIdx := len(result) - 1
	for i := len(s) - 1; i >= 0; i-- {
		result[resultIdx] = s[i]
		resultIdx--
		if i > 0 && (len(s)-i)%3 == 0 {
			result[resultIdx] = ','
			resultIdx--
		}
	}
	return sign + string(result)
}
