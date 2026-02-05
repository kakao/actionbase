package model

import (
	"regexp"

	"github.com/kakao/actionbase/internal/util"
)

var ansiRegex = regexp.MustCompile(`\x1b\[[0-9;]*m`)

type Response struct {
	IsSuccess    bool
	ErrorMessage *string
	Result       *string
}

func Success() *Response {
	return &Response{IsSuccess: true}
}

func SuccessWithResult(result string) *Response {
	util.Println(result)
	cleanResult := stripANSICodes(result)
	return &Response{IsSuccess: true, Result: &cleanResult}
}
func SuccessWithResultNoOut(result string) *Response {
	cleanResult := stripANSICodes(result)
	return &Response{IsSuccess: true, Result: &cleanResult}
}

func Fail(message string) *Response {
	util.Println(message)
	return &Response{IsSuccess: false, ErrorMessage: &message}
}

func FailWithNoOut(message string) *Response {
	return &Response{IsSuccess: false, ErrorMessage: &message}
}

func stripANSICodes(s string) string {
	return ansiRegex.ReplaceAllString(s, "")
}
