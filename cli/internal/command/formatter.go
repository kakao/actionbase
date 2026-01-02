package command

import (
	"strings"

	"github.com/kakao/actionbase/internal/util"
)

func FormatEdgeProperties(properties map[string]interface{}) string {
	if len(properties) == 0 {
		return ""
	}

	var sb strings.Builder
	sb.Grow(len(properties) * 32)

	first := true
	for key, value := range properties {
		if !first {
			sb.WriteByte('\n')
		}
		keyStr := util.ToString(key)
		valueStr := util.ToString(value)
		sb.WriteString(keyStr)
		sb.WriteString(": ")
		sb.WriteString(valueStr)
		first = false
	}
	return sb.String()
}
