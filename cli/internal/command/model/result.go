package model

import "fmt"

type Result struct {
	IsSuccess    bool
	ErrorMessage *string
}

func Success() *Result {
	return &Result{IsSuccess: true, ErrorMessage: nil}
}

func Fail(message string) *Result {
	fmt.Println(message)
	return &Result{IsSuccess: false, ErrorMessage: &message}
}
