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
	execParamKey = "e"
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

	if cmd, found := parser.Get(execParamKey); found {
		util.SetPlainMode(true)
		console := runner.NewActionbaseCommandLineRunner(Version, host, &authKey, "", false, isDebugEnabled)
		console.CheckConnection()
		resp, _ := console.RunCommand(cmd)
		if resp == nil || !resp.IsSuccess {
			os.Exit(1)
		}
		return
	}

	if _, found := parser.GetLenient("plain"); found {
		util.SetPlainMode(true)
	}

	console := runner.NewActionbaseCommandLineRunner(Version, host, &authKey, "", false, isDebugEnabled)
	console.CheckConnection()
	console.StartServer(parser)
	console.Run()
}
