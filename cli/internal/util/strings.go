package util

import (
	"fmt"
	"strconv"
)

func ToString(v any) string {
	switch val := v.(type) {
	case int:
		return strconv.Itoa(val)
	case float64:
		return fmt.Sprintf("%.f", val)
	case string:
		return val
	case nil:
		return "null"
	default:
		return fmt.Sprintf("%v", val)
	}
}
