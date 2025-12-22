package command

import (
	"fmt"

	"github.com/kakao/actionbase/internal/guides"
)

type Guide struct{}

func NewGuide() *Guide {
	return &Guide{}
}

func (s *Guide) Execute(args []string) {
	if len(args) < 1 {
		fmt.Printf("Usage: %s\n", s.GetType().GetCommand())
		return
	}

	switch args[1] {
	case "start":
		if err := guides.Start(args[0]); err != nil {
			fmt.Println("Failed to start guide server:", err)
		}
	case "stop":
		if err := guides.Stop(); err != nil {
			fmt.Println("Failed to stop guide server:", err)
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
