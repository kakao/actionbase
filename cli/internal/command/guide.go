package command

import (
	"fmt"
	"os"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/guides"
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
	if len(args) < 1 {
		fmt.Printf("Usage: %s\n", g.GetType().GetCommand())
		return nil
	}

	if args[0] == "stop" {
		if err := guides.Stop(); err != nil {
			fmt.Println("Failed to stop guide server:", err)
		}
		return nil
	}

	switch args[1] {
	case "start":
		guideTypeString := args[0]
		guideType, found := guides.TypeFromString(guideTypeString)
		if !found {
			fmt.Printf("Invalid guide '%s': only '%s' are supported\n", guideTypeString, strings.Join(guides.SupportedGuideTypes, ","))
			return nil
		}

		if ok := guides.Download(guideType.Name); !ok {
			return nil
		}

		cwd, err := os.Getwd()
		if err != nil {
			fmt.Println("Failed to get current working directory:", err)
			return nil
		}

		src := fmt.Sprintf("%s/%s-latest.zip", cwd, guideType.Name)
		dest := fmt.Sprintf("%s", cwd)

		if err := guides.Unzip(src, dest); err != nil {
			fmt.Println("Failed to unzip guide:", err)
			return nil
		}

		host := g.client.GetHost()
		if err := guides.Start(cwd, guideType.Name, host, g.runner.GetCurrentPort()); err != nil {
			fmt.Println("Failed to start guide server:", err)
		}

	default:
		fmt.Printf("Usage: %s\n", g.GetType().GetCommand())
	}

	return nil
}

func (g *Guide) GetDescription() string {
	return "Start Actionbase guide"
}

func (g *Guide) GetType() Type {
	return TypeGuide
}
