package main

import (
	"fmt"
	"os"

	"github.com/kakao/actionbase/internal/runner"
	"github.com/kakao/actionbase/internal/util"
)

var (
	Version = "dev"
)

const (
	DefaultHost = "http://localhost:8080"

	hostParamKey = "host"
	authParamKey = "authKey"
)

func main() {
	parser := util.ParseArgs(os.Args)

	if _, found := parser.GetLenient("version"); found {
		fmt.Println("v" + Version)
		return
	}

	host, found := parser.Get(hostParamKey)
	if !found {
		host = DefaultHost
	}

	isDebugEnabled := false
	if _, found := parser.GetLenient("debug"); found {
		isDebugEnabled = true
	}

	authKey, _ := parser.Get(authParamKey)
	console := runner.NewActionbaseCommandLineRunner(Version, host, &authKey, "", false, isDebugEnabled)
	console.CheckConnection()
	console.StartServer(parser)
	console.Run()
}
