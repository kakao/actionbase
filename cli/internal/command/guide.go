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
	client *client.ActionbaseClient
}

func NewGuide(client *client.ActionbaseClient) *Guide {
	return &Guide{client: client}
}

func (s *Guide) Execute(args []string) *model.Result {
	if len(args) < 1 {
		fmt.Printf("Usage: %s\n", s.GetType().GetCommand())
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
			fmt.Println("Failed to download guide assets")
			return nil
		}

		cwd, err := os.Getwd()
		if err != nil {
			fmt.Println("Failed to get current working directory:", err)
			return nil
		}

		src := fmt.Sprintf("%s/dist.zip", cwd)
		dest := fmt.Sprintf("%s", cwd)

		if err := guides.Unzip(src, dest); err != nil {
			fmt.Println("Failed to unzip guide:", err)
			return nil
		}

		host := s.client.GetHost()
		if err := guides.Start(cwd, guideType.Name, host); err != nil {
			fmt.Println("Failed to start guide server:", err)
		}

	default:
		fmt.Printf("Usage: %s\n", s.GetType().GetCommand())
	}

	return nil
}

func (s *Guide) GetDescription() string {
	return "Start Actionbase guide"
}

func (s *Guide) GetType() Type {
	return TypeGuide
}
