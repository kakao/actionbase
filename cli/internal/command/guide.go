package command

import (
	"fmt"
	"os"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/guides"
	"github.com/kakao/actionbase/internal/httpserver"
	"github.com/kakao/actionbase/internal/util"
)

type Guide struct {
	runner GuideRunner
	client *client.ActionbaseClient
}

type GuideRunner interface {
	GetCurrentPort() string
	SetRunning(running bool)
}

func NewGuide(runner GuideRunner, client *client.ActionbaseClient) *Guide {
	return &Guide{runner: runner, client: client}
}

func (g *Guide) Execute(args []string) *model.Response {
	if len(args) != 2 {
		fmt.Printf("Usage: %s\n", g.GetType().GetCommand())
		return nil
	}

	if args[0] != "start" {
		fmt.Printf("Usage: %s\n", g.GetType().GetCommand())
		return nil
	}

	serverPort := g.runner.GetCurrentPort()

	if serverPort == "" {
		fmt.Println("Server mode is required. Run `actionbase --proxy` to continue")
		return nil
	}

	guideTypeString := args[1]
	guideType, found := guides.TypeFromString(guideTypeString)
	if !found {
		fmt.Printf("Invalid guide '%s': only '%s' are supported\n", guideTypeString, strings.Join(guides.SupportedGuideTypes, ","))
		return nil
	}

	filename := guideType.Name + "-latest.zip"
	url := "https://github.com/kakao/actionbase/releases/download/guides/" + guideType.Name + "/" + filename
	if ok := util.Download(filename, url); !ok {
		return nil
	}

	cwd, err := os.Getwd()
	if err != nil {
		fmt.Println("Failed to get current working directory:", err)
		return nil
	}

	src := cwd + "/" + guideType.Name + "-latest.zip"

	if err := util.Unzip(src, cwd); err != nil {
		fmt.Println("Failed to unzip guide:", err)
		return nil
	}

	host := g.client.GetHost()
	if err := httpserver.StartGuide(cwd, guideType.Name, host, serverPort); err != nil {
		fmt.Println("Failed to start guide server:", err)
	}

	return nil
}

func (g *Guide) GetDescription() string {
	return "Start Actionbase guide"
}

func (g *Guide) GetType() Type {
	return TypeGuide
}
