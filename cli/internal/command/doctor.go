package command

import (
	"fmt"
	"regexp"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/model"
)

var resourceNamePattern = regexp.MustCompile(`^[a-z][a-z0-9_]{0,63}$`)

type Doctor struct {
	client *client.ActionbaseClient
}

func NewDoctor(c *client.ActionbaseClient) *Doctor {
	return &Doctor{client: c}
}

func (d *Doctor) Execute(args []string) *model.Response {
	if len(args) < 1 || args[0] != "names" {
		return model.Fail(fmt.Sprintf("Usage: %s", d.GetType().GetCommand()))
	}

	dbs := d.client.GetDatabases()
	if dbs.IsError() {
		return model.Fail("Failed to fetch databases")
	}

	violations := 0
	databases, tables, aliases, storages := 0, 0, 0, 0

	if resp := d.client.GetStorages(); !resp.IsError() {
		for _, s := range resp.Body.Content {
			storages++
			if !resourceNamePattern.MatchString(s.Name) {
				fmt.Printf("  VIOLATION  [storage] %s\n", s.Name)
				violations++
			}
		}
	}

	for _, db := range dbs.Body.Content {
		databases++
		if !resourceNamePattern.MatchString(db.Name) {
			fmt.Printf("  VIOLATION  [database] %s\n", db.Name)
			violations++
		}

		if resp := d.client.GetTables(db.Name); !resp.IsError() {
			for _, t := range resp.Body.Content {
				tables++
				short := shortName(t.Name)
				if !resourceNamePattern.MatchString(short) {
					fmt.Printf("  VIOLATION  [table] %s  (%s.%s)\n", short, db.Name, short)
					violations++
				}
			}
		}

		if resp := d.client.GetAliases(db.Name); !resp.IsError() {
			for _, a := range resp.Body.Content {
				aliases++
				short := shortName(a.Name)
				if !resourceNamePattern.MatchString(short) {
					fmt.Printf("  VIOLATION  [alias] %s  (%s.%s)\n", short, db.Name, short)
					violations++
				}
			}
		}
	}

	summary := fmt.Sprintf("Scanned %d storage(s), %d database(s), %d table(s), %d alias(es).", storages, databases, tables, aliases)
	if violations == 0 {
		return model.SuccessWithResult(summary + " No violations found.")
	}
	return model.Fail(fmt.Sprintf("%s %d violation(s) found.", summary, violations))
}

func (d *Doctor) GetDescription() string {
	return "Run health checks on the connected server"
}

func (d *Doctor) GetType() Type {
	return TypeDoctor
}

// shortName strips the "db." prefix from names returned as "db.name" by the API.
func shortName(name string) string {
	if i := strings.LastIndex(name, "."); i >= 0 {
		return name[i+1:]
	}
	return name
}
