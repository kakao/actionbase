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
	// Extract -e before ParseArgs, which only handles --key flags.
	var execCmd string
	args := os.Args
	for i, arg := range args {
		if arg == "-e" && i+1 < len(args) {
			execCmd = args[i+1]
			args = append(args[:i:i], args[i+2:]...)
			break
		}
	}

	parser := util.ParseArgs(args)

	if _, found := parser.GetLenient("version"); found {
		fmt.Println("v" + Version)
		return
	}

	host, found := parser.Get(hostParamKey)
	if !found {
		if env := os.Getenv("ACT_HOST"); env != "" {
			host = env
		} else {
			host = DefaultHost
		}
	}

	isDebugEnabled := false
	if _, found := parser.GetLenient("debug"); found {
		isDebugEnabled = true
	}

	authKey, _ := parser.Get(authParamKey)
	if authKey == "" {
		authKey = os.Getenv("ACT_API_KEY")
	}

	if execCmd != "" {
		util.SetPlainMode(true)
		console := runner.NewActionbaseCommandLineRunner(Version, host, &authKey, "", false, isDebugEnabled)
		console.CheckConnection()
		resp, _ := console.RunCommand(execCmd)
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
