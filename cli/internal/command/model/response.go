package model

import "fmt"

type Response struct {
	IsSuccess    bool
	ErrorMessage *string
	Result       *string
}

func Success() *Response {
	return &Response{IsSuccess: true}
}

func SuccessWithResult(result string) *Response {
	fmt.Println(result)
	return &Response{IsSuccess: true, Result: &result}
}

func Fail(message string) *Response {
	fmt.Println(message)
	return &Response{IsSuccess: false, ErrorMessage: &message}
}
