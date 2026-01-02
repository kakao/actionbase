package command

import "fmt"

type Direction struct {
	value string
}

var (
	DirectionIN = Direction{
		value: "IN",
	}

	DirectionOUT = Direction{
		value: "OUT",
	}

	DirectionBOTH = Direction{
		value: "BOTH",
	}
)

func (d Direction) String() string {
	return d.value
}

func (d Direction) GetValue() string {
	return d.value
}

func ParseDirection(s string) (*Direction, error) {
	switch s {
	case "IN", "in":
		return &DirectionIN, nil
	case "OUT", "out":
		return &DirectionOUT, nil
	case "BOTH", "both":
		return &DirectionBOTH, nil
	default:
		return nil, fmt.Errorf("invalid direction: %s. Must be IN, OUT or BOTH", s)
	}
}
