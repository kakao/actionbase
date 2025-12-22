package command

import (
	"fmt"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/guides"
)

type Guide struct {
	client *client.ActionbaseClient
}

func NewGuide(client *client.ActionbaseClient) *Guide {
	return &Guide{client: client}
}

func (s *Guide) Execute(args []string) {
	if len(args) < 1 {
		fmt.Printf("Usage: %s\n", s.GetType().GetCommand())
		return
	}

	if args[0] == "stop" {
		if err := guides.Stop(); err != nil {
			fmt.Println("Failed to stop guide server:", err)
		}
		return
	}

	switch args[1] {
	case "start":
		guideTypeString := args[0]
		guideType, found := guides.TypeFromString(guideTypeString)
		if !found {
			fmt.Printf("Invalid guide '%s': only '%s' are supported\n", guideTypeString, strings.Join(guides.SupportedGuideTypes, ","))
			return
		}

		ok := guides.Install(guideType)
		if !ok {
			return
		}

		host := s.client.GetHost()
		if err := guides.Start(guideType.Organization, guideType.PackageName, host); err != nil {
			fmt.Println("Failed to start guide server:", err)
		}
	default:
		fmt.Printf("Usage: %s\n", s.GetType().GetCommand())
	}
}

func (s *Guide) GetDescription() string {
	return "Start Actionbase guide"
}

func (s *Guide) GetType() Type {
	return TypeGuide
}
