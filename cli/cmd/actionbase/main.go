package main

import (
	"fmt"
	"os"

	"github.com/kakao/actionbase/internal/runner"
	"github.com/kakao/actionbase/internal/util"
)

func main() {
	args := os.Args

	parser := util.ParseArgs(args)

	host, found := parser.Get("host")
	if !found {
		fmt.Println("Use --host <host> [--authKey <authKey>] [--server <port>]")
		return
	}

	authKey, _ := parser.Get("authKey")
	console := runner.NewActionbaseCommandLineRunner(host, &authKey, "", false)

	console.CheckConnection()

	if port, found := parser.Get("server"); found {
		serverReady := make(chan error, 1)
		go func() {
			if err := console.Start(port, serverReady); err != nil {
				fmt.Printf("Failed to start actionbase as server mode. %v\n", err)
				os.Exit(1)
			}
		}()

		if err := <-serverReady; err != nil {
			os.Exit(1)
		}

		console.SetCurrentPort(port)
		console.SetIsServerModeEnabled(true)
	}

	console.Run()
}
