package util

import (
	"encoding/json"
	"strconv"
	"strings"
	"time"
)

const (
	reservedWordCurrentTimestamp = "__CURRENT_TIMESTAMP__"
)

type Parser struct {
	values map[string]string
}

func ReplaceTimestampInString(str string) string {
	if !strings.Contains(str, reservedWordCurrentTimestamp) {
		return str
	}

	timestamp := time.Now().UnixMilli()
	timestampStr := strconv.FormatInt(timestamp, 10)
	return strings.ReplaceAll(str, reservedWordCurrentTimestamp, timestampStr)
}

func ParseArgs(args []string) *Parser {
	p := &Parser{
		values: make(map[string]string),
	}

	for i := 0; i < len(args); i++ {
		arg := args[i]

		// --key=value
		if strings.HasPrefix(arg, "--") && strings.Contains(arg, "=") {
			parts := strings.SplitN(arg[2:], "=", 2)
			key := parts[0]
			value := parts[1]
			p.values[key] = value
			continue
		}

		// --key value
		if strings.HasPrefix(arg, "--") {
			key := arg[2:]

			if i+1 >= len(args) || strings.HasPrefix(args[i+1], "--") {
				p.values[key] = ""
				continue
			}

			var sb strings.Builder
			first := true
			for j := i + 1; j < len(args); j++ {
				if strings.HasPrefix(args[j], "--") {
					break
				}
				if !first {
					sb.WriteByte(' ')
				}
				sb.WriteString(args[j])
				first = false
			}
			p.values[key] = sb.String()
		}
	}

	return p
}

func (p *Parser) Get(key string) (string, bool) {
	v, ok := p.values[key]

	if v == "" {
		return "", false
	}

	return v, ok
}

func (p *Parser) GetLenient(key string) (string, bool) {
	v, ok := p.values[key]
	return v, ok
}

func (p *Parser) GetParsed(key string) (interface{}, bool) {
	raw, ok := p.values[key]
	if !ok {
		return nil, false
	}

	rawTrimmed := strings.TrimSpace(raw)

	if len(rawTrimmed) >= 2 {
		if (rawTrimmed[0] == '\'' && rawTrimmed[len(rawTrimmed)-1] == '\'') ||
			(rawTrimmed[0] == '"' && rawTrimmed[len(rawTrimmed)-1] == '"') {
			rawTrimmed = rawTrimmed[1 : len(rawTrimmed)-1]
		}
	}

	var parsed interface{}
	if err := json.Unmarshal([]byte(rawTrimmed), &parsed); err == nil {
		return parsed, true
	}

	return rawTrimmed, true
}
