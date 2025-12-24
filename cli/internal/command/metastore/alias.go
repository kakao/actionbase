package metastore

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/model"
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

func (a *Alias) ShowAll() *model.Result {
	database := a.runner.GetCurrentDatabase()
	if database == "" {
		return model.Fail("No database selected. Use 'use database <name>'")
	}

	response := a.actionbaseClient.GetAliases(database)

	if response.IsError() {
		return model.Fail("Failed to get aliases")
	}

	aliasEntity := response.Body
	var aliases []map[string]interface{}
	for idx, content := range aliasEntity.Content {
		data := map[string]interface{}{
			"#":      strconv.Itoa(idx + 1),
			"name":   content.Name,
			"desc":   content.Desc,
			"target": content.Target,
			"active": content.Active,
		}
		aliases = append(aliases, data)
	}

	columnOrder := []string{"#", "name", "desc", "target", "active"}

	if len(aliases) == 0 {
		emptyAlias := map[string]interface{}{
			"#":      "",
			"name":   "",
			"desc":   "",
			"target": "",
			"active": "",
		}
		aliases = append(aliases, emptyAlias)
	}

	fmt.Println()
	fmt.Printf("%v Aliases in '%s'\n", aliasEntity.Count, a.runner.GetCurrentDatabase())
	fmt.Println(util.PrettyPrintRowsWithOrder(aliases, columnOrder))

	return model.Success()
}

func (a *Alias) Use(name string) *model.Result {
	database := a.runner.GetCurrentDatabase()
	if database == "" {
		return model.Fail("No database selected. Use 'use database <name>'")
	}

	response := a.actionbaseClient.GetAlias(database, name)
	if response.IsError() {
		return model.Fail(fmt.Sprintf("No Alias '%s' found in %s", name, database))
	}

	target := response.Body.Target
	split := strings.Split(target, ".")
	table := split[1]

	fmt.Printf("The Alias is changed to '%s:%s' (table '%s')\n", database, name, table)

	a.runner.SetCurrentAlias(name)
	a.runner.SetCurrentTable(table)

	return model.Success()
}

func (a *Alias) Desc(name string) *model.Result {
	database := a.runner.GetCurrentDatabase()
	if database == "" {
		return model.Fail("No database selected. Use 'use database <name>'")
	}

	response := a.actionbaseClient.GetAlias(database, name)
	if response.IsError() {
		return model.Fail(fmt.Sprintf("No Alias '%s' found in %s", name, database))
	}

	table := response.Body.Table

	result := map[string]interface{}{
		"name":     table.Name,
		"desc":     table.Desc,
		"type":     table.Type,
		"dirType":  table.DirType,
		"event":    table.Event,
		"readOnly": table.ReadOnly,
		"mode":     table.Mode,
	}
	fmt.Println()
	tableColumnOrder := []string{"name", "desc", "type", "dirType", "event", "readOnly", "mode"}
	fmt.Println(util.PrettyPrintWithOrder(result, tableColumnOrder))

	schema := table.Schema
	schemaColumnOrder := []string{"type", "desc"}

	source := map[string]interface{}{
		"type": schema.Src.Type,
		"desc": schema.Src.Desc,
	}
	fmt.Println()
	fmt.Println("[Source]")
	fmt.Println(util.PrettyPrintWithOrder(source, schemaColumnOrder))

	target := map[string]interface{}{
		"type": schema.Tgt.Type,
		"desc": *schema.Tgt.Desc,
	}
	fmt.Println()
	fmt.Println("[Target]")
	fmt.Println(util.PrettyPrintWithOrder(target, schemaColumnOrder))

	var fields []map[string]interface{}
	for idx, field := range schema.Fields {
		fields = append(
			fields,
			map[string]interface{}{
				"#":        strconv.Itoa(idx + 1),
				"name":     *field.Name,
				"type":     field.Type,
				"nullable": field.Nullable,
				"desc":     *field.Desc,
			})
	}

	fieldColumnOrder := []string{"#", "name", "type", "nullable", "desc"}

	if len(fields) == 0 {
		emptyField := map[string]interface{}{
			"#":        "",
			"name":     "",
			"type":     "",
			"nullable": "",
			"desc":     "",
		}
		fields = append(fields, emptyField)
	}

	fmt.Println()
	fmt.Printf("[Fields (%d)]\n", len(fields))
	fmt.Println(util.PrettyPrintRowsWithOrder(fields, fieldColumnOrder))

	return model.Success()
}
