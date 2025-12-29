package main

import (
	"os"

	"github.com/kakao/actionbase/internal/runner"
	"github.com/kakao/actionbase/internal/util"
)

const (
	DefaultHost = "http://localhost:8080"
)

func main() {
	parser := util.ParseArgs(os.Args)

	host, found := parser.Get("host")
	if !found {
		host = DefaultHost
	}

	authKey, _ := parser.Get("authKey")
	console := runner.NewActionbaseCommandLineRunner(host, &authKey, "", false)
	console.CheckConnection()
	console.StartServer(parser)
	console.Run()
}
