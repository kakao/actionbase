package main

import (
	"fmt"
	"os"

	"github.com/kakao/actionbase/internal/runner"
	"github.com/kakao/actionbase/internal/util"
)

func main() {
	args := os.Args

	if len(args) > 5 {
		fmt.Println("Use --host <host> [--authKey <authKey>]")
		return
	}

	parser := util.ParseArgs(args)

	host, found := parser.Get("host")
	if !found {
		fmt.Println("Use --host <host> [--authKey <authKey>]")
		return
	}

	authKey, _ := parser.Get("authKey")
	console := runner.NewActionbaseCommandLineRunner(host, &authKey)

	console.CheckConnection()
	console.ShowBanner()
	console.Run()
}
