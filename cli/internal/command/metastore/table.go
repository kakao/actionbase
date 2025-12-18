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
	if response == nil {
		return
	}
	content := response.Content

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

	// Define column order explicitly
	columnOrder := []string{"#", "active", "name", "desc", "type"}

	if len(results) > 0 {
		fmt.Printf("%v Tables in %s:\n", response.Count, database)
		fmt.Println(util.PrettyPrintRowsWithOrder(results, columnOrder))
		fmt.Println()
		return
	}

	emptyTable := map[string]interface{}{
		"#":      "",
		"name":   "",
		"desc":   "",
		"target": "",
		"active": "",
	}
	fmt.Printf("0 Tables in %s:\n", database)
	fmt.Println(util.PrettyPrintWithOrder(emptyTable, columnOrder))
	fmt.Println()
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

	t.showGroups(t.runner.GetCurrentDatabase(), t.runner.GetCurrentTable())
}

// Desc describes a table
func (t *Table) Desc(name string) {
	database := t.runner.GetCurrentDatabase()
	if database == "" {
		fmt.Println("No database selected. Use 'use database <name>'")
		return
	}

	response := t.actionbaseClient.GetTable(database, name)
	if response == nil {
		return
	}

	table := map[string]interface{}{
		"name":     response.Name,
		"desc":     response.Desc,
		"type":     response.Type,
		"dirType":  response.DirType,
		"event":    response.Event,
		"readOnly": response.ReadOnly,
		"mode":     response.Mode,
	}
	tableColumnOrder := []string{"name", "desc", "type", "dirType", "event", "readOnly", "mode"}
	fmt.Println(util.PrettyPrintWithOrder(table, tableColumnOrder))
	fmt.Println()

	schema := response.Schema
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
	results := []map[string]interface{}{}
	for idx, field := range fields {
		data := map[string]interface{}{
			"#":        "[" + strconv.Itoa(idx+1) + "]",
			"name":     field.Name,
			"type":     field.Type,
			"nullable": field.Nullable,
			"desc":     field.Desc,
		}
		results = append(results, data)
	}

	fieldColumnOrder := []string{"#", "name", "type", "nullable", "desc"}

	if len(results) > 0 {
		fmt.Printf(">> Fields (%d) :\n", len(results))
		fmt.Println(util.PrettyPrintRowsWithOrder(results, fieldColumnOrder))
		fmt.Println()
		return
	}

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

// Use selects a table to use
func (t *Table) Use(table string) bool {
	database := t.runner.GetCurrentDatabase()
	if database == "" {
		fmt.Println("No database selected. Use 'use database <name>'")
		return false
	}

	response := t.actionbaseClient.GetTable(database, table)
	if response == nil {
		return false
	}

	t.runner.SetCurrentTable(table)

	fmt.Printf("The Table is changed to '%s:%s'\n", database, table)

	return true
}

func (t *Table) showIndices(database string, table string) {
	response := t.actionbaseClient.GetTable(database, table)
	if response == nil {
		return
	}

	indices := response.Indices
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

	columnOrder := []string{"#", "name", "desc", "fields[].name", "fields[].order"}

	if len(results) > 0 {
		fmt.Printf("%d Indices in %s:\n", len(indices), table)
		fmt.Println(util.PrettyPrintRowsWithOrder(results, columnOrder))
		fmt.Println()
		return
	}
	emptyIndex := map[string]interface{}{
		"#":              "",
		"name":           "",
		"desc":           "",
		"fields[].name":  "",
		"fields[].order": "",
	}
	fmt.Printf("0 Indices in %s:\n", table)
	fmt.Println(util.PrettyPrintWithOrder(emptyIndex, columnOrder))
	fmt.Println()
}

func (t *Table) showGroups(database string, table string) {
	response := t.actionbaseClient.GetTable(database, table)
	if response == nil {
		return
	}

	groups := response.Groups
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

		// Convert ttl to string (avoid scientific notation)
		ttl := fmt.Sprintf("%d", group.Ttl)

		data := map[string]interface{}{
			"#":                        "[" + strconv.Itoa(idx+1) + "]",
			"group":                    group.Group,
			"type":                     group.Type,
			"valueField":               group.ValueField,
			"comment":                  group.Comment,
			"directionType":            group.DirectionType,
			"ttl":                      ttl,
			"fields[].name":            fieldNames,
			"fields[].bucket.type":     fieldBucketTypes,
			"fields[].bucket.name":     fieldBucketNames,
			"fields[].bucket.unit":     fieldBucketUnits,
			"fields[].bucket.timezone": fieldBucketTimezones,
			"fields[].bucket.format":   fieldBucketFormats,
		}
		results = append(results, data)
	}

	// Define column order explicitly
	columnOrder := []string{
		"#", "group", "type", "valueField", "comment", "directionType", "ttl",
		"fields[].name", "fields[].bucket.type", "fields[].bucket.name",
		"fields[].bucket.unit", "fields[].bucket.timezone", "fields[].bucket.format",
	}

	if len(results) > 0 {
		fmt.Printf("%d Groups in %s:\n", len(groups), table)
		fmt.Println(util.PrettyPrintRowsWithOrder(results, columnOrder))
		fmt.Println()
	} else {
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
		fmt.Printf("0 Groups in %s:\n", table)
		fmt.Println(util.PrettyPrintWithOrder(emptyGroup, columnOrder))
		fmt.Println()
	}
}
