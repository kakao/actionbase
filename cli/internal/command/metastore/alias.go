package metastore

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/util"
)

type Alias struct {
	runner           AliasRunner
	actionbaseClient *client.ActionbaseClient
}

type AliasRunner interface {
	SetCurrentDatabase(database string)
	SetCurrentTable(table string)
	SetCurrentAlias(alias string)
	GetCurrentDatabase() string
	GetCurrentTable() string
	GetCurrentAlias() string
}

func NewAlias(runner AliasRunner, actionbaseClient *client.ActionbaseClient) *Alias {
	return &Alias{runner: runner, actionbaseClient: actionbaseClient}
}

func (a *Alias) ShowAll() {
	database := a.runner.GetCurrentDatabase()
	if database == "" {
		fmt.Println("No database selected. Use 'use database <name>'")
		return
	}

	response := a.actionbaseClient.GetAliases(database)

	if response == nil {
		return
	}

	var aliases []map[string]interface{}
	for idx, content := range response.Content {
		data := map[string]interface{}{
			"#":      "[" + strconv.Itoa(idx+1) + "]",
			"name":   content.Name,
			"desc":   content.Desc,
			"target": content.Target,
			"active": content.Active,
		}
		aliases = append(aliases, data)
	}

	columnOrder := []string{"#", "name", "desc", "target", "active"}

	if len(aliases) > 0 {
		fmt.Printf("%v Aliases in '%s':\n", response.Count, a.runner.GetCurrentDatabase())
		fmt.Println(util.PrettyPrintRowsWithOrder(aliases, columnOrder))
		fmt.Println()
	} else {
		emptyAlias := map[string]interface{}{
			"#":      "",
			"name":   "",
			"desc":   "",
			"target": "",
			"active": "",
		}
		fmt.Printf("0 Aliases in '%s':\n", a.runner.GetCurrentDatabase())
		fmt.Println(util.PrettyPrintWithOrder(emptyAlias, columnOrder))
		fmt.Println()
	}
}

func (a *Alias) Use(alias string) bool {
	database := a.runner.GetCurrentDatabase()
	if database == "" {
		fmt.Println("No database selected. Use 'use database <name>'")
		return false
	}

	response := a.actionbaseClient.GetAlias(database, alias)
	if response == nil {
		return false
	}

	target := response.Target
	split := strings.Split(target, ".")
	table := split[1]

	fmt.Printf("The Alias is changed to '%s:%s' (table '%s')\n", database, alias, table)

	a.runner.SetCurrentAlias(alias)
	a.runner.SetCurrentTable(table)

	return true
}

func (a *Alias) Desc(name string) {
	database := a.runner.GetCurrentDatabase()
	if database == "" {
		fmt.Println("No database selected. Use 'use database <name>'")
		return
	}

	response := a.actionbaseClient.GetAlias(database, name)
	if response == nil {
		return
	}
	table := response.Table

	result := map[string]interface{}{
		"name":     table.Name,
		"desc":     table.Desc,
		"type":     table.Type,
		"dirType":  table.DirType,
		"event":    table.Event,
		"readOnly": table.ReadOnly,
		"mode":     table.Mode,
	}
	tableColumnOrder := []string{"name", "desc", "type", "dirType", "event", "readOnly", "mode"}
	fmt.Println(util.PrettyPrintWithOrder(result, tableColumnOrder))
	fmt.Println()

	schema := table.Schema
	srcTgtColumnOrder := []string{"type", "desc"}

	source := map[string]interface{}{
		"type": schema.Src.Type,
		"desc": schema.Src.Desc,
	}
	fmt.Println("\n>> Source")
	fmt.Println(util.PrettyPrintWithOrder(source, srcTgtColumnOrder))

	target := map[string]interface{}{
		"type": schema.Tgt.Type,
		"desc": schema.Tgt.Desc,
	}
	fmt.Println("\n>> Target")
	fmt.Println(util.PrettyPrintWithOrder(target, srcTgtColumnOrder))
	fmt.Println()

	fields := schema.Fields

	var fieldResults []map[string]interface{}
	for idx, field := range fields {
		data := map[string]interface{}{
			"#":        "[" + strconv.Itoa(idx+1) + "]",
			"name":     field.Name,
			"type":     field.Type,
			"nullable": field.Nullable,
			"desc":     field.Desc,
		}
		fieldResults = append(fieldResults, data)
	}

	fieldColumnOrder := []string{"#", "name", "type", "nullable", "desc"}

	if len(fields) > 0 {
		fmt.Printf(">> Fields (%d) :\n", len(fields))
		fmt.Println(util.PrettyPrintRowsWithOrder(fieldResults, fieldColumnOrder))
		fmt.Println()
	} else {
		emptyField := map[string]interface{}{
			"#":        "",
			"name":     "",
			"type":     "",
			"nullable": "",
			"desc":     "",
		}
		fmt.Println(">> Fields (0) :")
		fmt.Println(util.PrettyPrintWithOrder(emptyField, fieldColumnOrder))
		fmt.Println()
	}
}
