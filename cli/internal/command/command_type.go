package command

type Type struct {
	name    string
	command string
}

var (
	TypeCreate = Type{
		name:    "create",
		command: "create <database|storage|table|alias> <args>",
	}

	TypeDesc = Type{
		name:    "desc",
		command: "desc <name> --using <table|alias>",
	}

	TypeExit = Type{
		name:    "exit",
		command: "exit",
	}

	TypeHelp = Type{
		name:    "help",
		command: "help",
	}

	TypeShow = Type{
		name:    "show",
		command: "show <databases|storages|tables|indices|groups> --using <table|alias>",
	}

	TypeContext = Type{
		name:    "context",
		command: "context",
	}

	TypeDebug = Type{
		name:    "debug",
		command: "debug <on|off>",
	}

	TypeUse = Type{
		name:    "use",
		command: "use <database|table|alias> <name>",
	}

	TypeGet = Type{
		name:    "get",
		command: "get --source <source> --target <target>",
	}

	TypeScan = Type{
		name:    "scan",
		command: "scan --index <index> --start <start> --direction <direction> [--ranges <ranges>] [--limit <limit>]",
	}

	TypeMutate = Type{
		name:    "mutate",
		command: "mutate --type <type> --table <table> --source <source> --target <target> --version <version> --properties <properties>",
	}

	TypeCount = Type{
		name:    "count",
		command: "count --start <start> --direction <direction>",
	}

	TypeLoad = Type{
		name:    "load",
		command: "load <path>",
	}
)

func (t Type) GetName() string {
	return t.name
}

func (t Type) GetCommand() string {
	return t.command
}
