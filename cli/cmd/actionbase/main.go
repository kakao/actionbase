package main

import (
	"os"

	"github.com/kakao/actionbase/internal/runner"
	"github.com/kakao/actionbase/internal/util"
)

const (
	DefaultHost = "http://localhost:8080"

	hostParamKey = "host"
	authParamKey = "authKey"
)

func main() {
	parser := util.ParseArgs(os.Args)

	host, found := parser.Get(hostParamKey)
	if !found {
		host = DefaultHost
	}

	authKey, _ := parser.Get(authParamKey)
	console := runner.NewActionbaseCommandLineRunner(host, &authKey, "", false)
	console.CheckConnection()
	console.StartServer(parser)
	console.Run()
}
