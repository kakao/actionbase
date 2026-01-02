package command

import (
	"strings"

	"github.com/kakao/actionbase/internal/command/model"
)

func ValidateDatabase(runner TableCommandRunner) (string, *model.Response) {
	database := runner.GetCurrentDatabase()
	if database == "" {
		return "", model.Fail("No database selected. Use 'use database <name>'")
	}
	return database, nil
}

func ValidateTable(runner TableCommandRunner, args []string) (string, *model.Response) {
	var table string
	if len(args) > 0 && !strings.HasPrefix(args[0], "--") {
		table = args[0]
	} else {
		table = runner.GetCurrentTable()
		if table == "" {
			return "", model.Fail("No table selected. Use 'use <table|alias> <name>'")
		}
	}
	return table, nil
}
