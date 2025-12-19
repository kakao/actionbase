package metastore

import (
	"fmt"
	"strconv"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/util"
)

type Table struct {
	runner           TableRunner
	actionbaseClient *client.ActionbaseClient
}

type TableRunner interface {
	GetCurrentDatabase() string
	GetCurrentTable() string
	SetCurrentTable(table string)
}

func NewTable(runner TableRunner, actionbaseClient *client.ActionbaseClient) *Table {
	return &Table{runner: runner, actionbaseClient: actionbaseClient}
}

func (t *Table) ShowAll() {
	database := t.runner.GetCurrentDatabase()
	if database == "" {
		fmt.Println("No database selected. Use 'use database <name>'")
		return
	}

	response := t.actionbaseClient.GetTables(database)
	if response.IsError() {
		fmt.Printf("Failed to get tables in %s\n", database)
		return
	}

	tableEntity := response.Body
	content := tableEntity.Content

	var results []map[string]interface{}
	for idx, table := range content {
		data := map[string]interface{}{
			"#":      "[" + strconv.Itoa(idx+1) + "]",
			"active": table.Active,
			"name":   table.Name,
			"desc":   table.Desc,
			"type":   table.Type,
		}
		results = append(results, data)
	}

	if len(results) == 0 {
		emptyTable := map[string]interface{}{
			"#":      "",
			"name":   "",
			"desc":   "",
			"target": "",
			"active": "",
		}
		results = append(results, emptyTable)
	}

	fmt.Println()
	fmt.Printf("%v Tables in database\n", tableEntity.Count)

	columnOrder := []string{"#", "active", "name", "desc", "type"}
	fmt.Println(util.PrettyPrintRowsWithOrder(results, columnOrder))
	return
}

func (t *Table) ShowIndices(name string) {
	if t.runner.GetCurrentDatabase() == "" {
		fmt.Println("No database selected. Use 'use database <name>'")
		return
	}

	t.showIndices(t.runner.GetCurrentDatabase(), name)
}

func (t *Table) ShowGroups(table string) {
	if t.runner.GetCurrentDatabase() == "" {
		fmt.Println("No database selected. Use 'use database <name>'")
		return
	}

	t.showGroups(t.runner.GetCurrentDatabase(), table)
}

func (t *Table) Desc(name string) {
	database := t.runner.GetCurrentDatabase()
	if database == "" {
		fmt.Println("No database selected. Use 'use database <name>'")
		return
	}

	response := t.actionbaseClient.GetTable(database, name)
	if response.IsError() {
		fmt.Printf("Failed to get table '%s' in %s\n", name, database)
		return
	}

	tableEntity := response.Body
	table := map[string]interface{}{
		"name":     tableEntity.Name,
		"desc":     tableEntity.Desc,
		"type":     tableEntity.Type,
		"dirType":  tableEntity.DirType,
		"event":    tableEntity.Event,
		"readOnly": tableEntity.ReadOnly,
		"mode":     tableEntity.Mode,
	}
	fmt.Println()
	tableColumnOrder := []string{"name", "desc", "type", "dirType", "event", "readOnly", "mode"}
	fmt.Println(util.PrettyPrintWithOrder(table, tableColumnOrder))

	schema := tableEntity.Schema
	schemaColumnOrder := []string{"type", "desc"}

	source := map[string]interface{}{
		"type": schema.Src.Type,
		"desc": *schema.Src.Desc,
	}
	fmt.Println("\n[Source]")
	fmt.Println(util.PrettyPrintWithOrder(source, schemaColumnOrder))

	target := map[string]interface{}{
		"type": schema.Tgt.Type,
		"desc": *schema.Tgt.Desc,
	}
	fmt.Println("\n[Target]")
	fmt.Println(util.PrettyPrintWithOrder(target, schemaColumnOrder))

	fields := schema.Fields
	results := []map[string]interface{}{}
	for idx, field := range fields {
		data := map[string]interface{}{
			"#":        "[" + strconv.Itoa(idx+1) + "]",
			"name":     *field.Name,
			"type":     field.Type,
			"nullable": field.Nullable,
			"desc":     *field.Desc,
		}
		results = append(results, data)
	}

	if len(results) == 0 {
		emptyField := map[string]interface{}{
			"#":        "",
			"name":     "",
			"type":     "",
			"nullable": "",
			"desc":     "",
		}
		results = append(results, emptyField)
	}

	fmt.Println()
	fmt.Printf("[Fields (%d)]\n", len(results))

	fieldColumnOrder := []string{"#", "name", "type", "nullable", "desc"}
	fmt.Println(util.PrettyPrintRowsWithOrder(results, fieldColumnOrder))
	return
}

func (t *Table) Use(table string) {
	database := t.runner.GetCurrentDatabase()
	if database == "" {
		fmt.Println("No database selected. Use 'use database <name>'")
		return
	}

	response := t.actionbaseClient.GetTable(database, table)
	if response.IsError() {
		fmt.Printf("Failed to get table '%s'\n", table)
		return
	}

	t.runner.SetCurrentTable(table)

	fmt.Printf("The Table is changed to '%s:%s'\n", database, table)
}

func (t *Table) showIndices(database string, table string) {
	response := t.actionbaseClient.GetTable(database, table)
	if response.IsError() {
		fmt.Printf("Failed to get table '%s'\n", table)
		return
	}

	tableEntity := response.Body

	indices := tableEntity.Indices
	var results []map[string]interface{}
	for idx, index := range indices {
		fields := index.Fields
		name := ""
		order := ""

		for idx, field := range fields {
			fieldName := field.Name
			name += fieldName
			if idx < len(fields)-1 {
				name += "\n"
			}

			fieldOrder := field.Order
			order += fieldOrder
			if idx < len(fields)-1 {
				order += "\n"
			}
		}

		data := map[string]interface{}{
			"#":              "[" + strconv.Itoa(idx+1) + "]",
			"name":           index.Name,
			"desc":           index.Desc,
			"fields[].name":  name,
			"fields[].order": order,
		}
		results = append(results, data)
	}

	if len(indices) == 0 {
		emptyIndex := map[string]interface{}{
			"#":              "",
			"name":           "",
			"desc":           "",
			"fields[].name":  "",
			"fields[].order": "",
		}
		results = append(results, emptyIndex)
	}

	fmt.Println()
	fmt.Printf("%d Indices in %s\n", len(indices), table)

	columnOrder := []string{"#", "name", "desc", "fields[].name", "fields[].order"}
	fmt.Println(util.PrettyPrintRowsWithOrder(results, columnOrder))
}

func (t *Table) showGroups(database string, table string) {
	response := t.actionbaseClient.GetTable(database, table)
	if response.IsError() {
		fmt.Printf("Failed to get table '%s'\n", table)
		return
	}

	tableEntity := response.Body

	groups := tableEntity.Groups
	results := []map[string]interface{}{}
	for idx, group := range groups {
		fields := group.Fields

		fieldNames := ""
		fieldBucketTypes := ""
		fieldBucketNames := ""
		fieldBucketUnits := ""
		fieldBucketTimezones := ""
		fieldBucketFormats := ""

		for idx, field := range fields {
			fieldNames += field.Name
			if idx < len(fields)-1 {
				fieldNames += "\n"
			}

			bucket := field.Bucket
			if bucket != nil {
				fieldType := bucket.Type
				fieldBucketTypes += fieldType
				if idx < len(fields)-1 {
					fieldBucketTypes += "\n"
				}

				bucketName := bucket.Name
				fieldBucketNames += bucketName
				if idx < len(fields)-1 {
					fieldBucketNames += "\n"
				}

				bucketUnit := bucket.Unit
				fieldBucketUnits += bucketUnit
				if idx < len(fields)-1 {
					fieldBucketUnits += "\n"
				}

				bucketTimezone := bucket.Timezone
				fieldBucketTimezones += bucketTimezone
				if idx < len(fields)-1 {
					fieldBucketTimezones += "\n"
				}

				bucketFormat := bucket.Format
				fieldBucketFormats += bucketFormat
				if idx < len(fields)-1 {
					fieldBucketFormats += "\n"
				}
			} else {
				fieldBucketTypes += ""
				fieldBucketNames += ""
				fieldBucketUnits += ""
				fieldBucketTimezones += ""
				fieldBucketFormats += ""
			}
		}

		data := map[string]interface{}{
			"#":                        "[" + strconv.Itoa(idx+1) + "]",
			"group":                    group.Group,
			"type":                     group.Type,
			"valueField":               group.ValueField,
			"comment":                  group.Comment,
			"directionType":            group.DirectionType,
			"ttl":                      fmt.Sprintf("%d", group.Ttl),
			"fields[].name":            fieldNames,
			"fields[].bucket.type":     fieldBucketTypes,
			"fields[].bucket.name":     fieldBucketNames,
			"fields[].bucket.unit":     fieldBucketUnits,
			"fields[].bucket.timezone": fieldBucketTimezones,
			"fields[].bucket.format":   fieldBucketFormats,
		}
		results = append(results, data)
	}

	if len(groups) == 0 {
		emptyGroup := map[string]interface{}{
			"#":                        "",
			"group":                    "",
			"type":                     "",
			"valueField":               "",
			"comment":                  "",
			"directionType":            "",
			"ttl":                      "",
			"fields[].name":            "",
			"fields[].bucket.type":     "",
			"fields[].bucket.name":     "",
			"fields[].bucket.unit":     "",
			"fields[].bucket.timezone": "",
			"fields[].bucket.format":   "",
		}
		results = append(results, emptyGroup)
	}

	fmt.Println()
	fmt.Printf("%d Groups in %s\n", len(groups), table)

	columnOrder := []string{
		"#",
		"group",
		"type",
		"valueField",
		"comment",
		"directionType",
		"ttl",
		"fields[].name",
		"fields[].bucket.type",
		"fields[].bucket.name",
		"fields[].bucket.unit",
		"fields[].bucket.timezone",
		"fields[].bucket.format",
	}
	fmt.Println(util.PrettyPrintRowsWithOrder(results, columnOrder))
}
