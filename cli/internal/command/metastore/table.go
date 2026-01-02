package metastore

import (
	"fmt"
	"strconv"

	"github.com/kakao/actionbase/internal/client"
	"github.com/kakao/actionbase/internal/command/model"
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

func (t *Table) ShowAll() *model.Response {
	database := t.runner.GetCurrentDatabase()
	if database == "" {
		return model.Fail("No database selected. Use 'use database <name>'")
	}

	response := t.actionbaseClient.GetTables(database)
	if response.IsError() {
		return model.Fail(fmt.Sprintf("Failed to get tables in %s", database))
	}

	tableEntity := response.Body
	content := tableEntity.Content

	var results []map[string]interface{}
	for idx, table := range content {
		data := map[string]interface{}{
			"#":      strconv.Itoa(idx + 1),
			"active": table.Active,
			"name":   table.Name,
			"desc":   table.Desc,
			"type":   table.Type,
		}
		results = append(results, data)
	}

	columnOrder := []string{"#", "active", "name", "desc", "type"}
	resultMessage := "\n" +
		fmt.Sprintf("%v Tables in database\n", tableEntity.Count) +
		util.PrettyPrintRowsWithOrder(results, columnOrder)

	return model.SuccessWithResult(resultMessage)
}

func (t *Table) ShowIndices(name string) *model.Response {
	if t.runner.GetCurrentDatabase() == "" {
		return model.Fail("No database selected. Use 'use database <name>'")
	}

	return t.showIndices(t.runner.GetCurrentDatabase(), name)
}

func (t *Table) ShowGroups(table string) *model.Response {
	if t.runner.GetCurrentDatabase() == "" {
		return model.Fail("No database selected. Use 'use database <name>'")
	}

	return t.showGroups(t.runner.GetCurrentDatabase(), table)
}

func (t *Table) Desc(name string) *model.Response {
	database := t.runner.GetCurrentDatabase()
	if database == "" {
		return model.Fail("No database selected. Use 'use database <name>'")
	}

	response := t.actionbaseClient.GetTable(database, name)
	if response.IsError() {
		return model.Fail(fmt.Sprintf("Failed to get table '%s' in %s", name, database))
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

	tableColumnOrder := []string{"name", "desc", "type", "dirType", "event", "readOnly", "mode"}
	resultMessage := "\n" + util.PrettyPrintWithOrder(table, tableColumnOrder)

	schema := tableEntity.Schema
	schemaColumnOrder := []string{"type", "desc"}

	source := map[string]interface{}{
		"type": schema.Src.Type,
		"desc": *schema.Src.Desc,
	}
	resultMessage += "\n[Source]\n" + util.PrettyPrintWithOrder(source, schemaColumnOrder)

	target := map[string]interface{}{
		"type": schema.Tgt.Type,
		"desc": *schema.Tgt.Desc,
	}

	resultMessage += "\n[Target]\n" + util.PrettyPrintWithOrder(target, schemaColumnOrder)

	fields := schema.Fields
	var results []map[string]interface{}
	for idx, field := range fields {
		data := map[string]interface{}{
			"#":        strconv.Itoa(idx + 1),
			"name":     *field.Name,
			"type":     field.Type,
			"nullable": field.Nullable,
			"desc":     *field.Desc,
		}
		results = append(results, data)
	}

	fieldColumnOrder := []string{"#", "name", "type", "nullable", "desc"}
	resultMessage += "\n" +
		fmt.Sprintf("[Fields (%d)]\n", len(results)) +
		util.PrettyPrintRowsWithOrder(results, fieldColumnOrder)

	return model.SuccessWithResult(resultMessage)
}

func (t *Table) Use(table string) *model.Response {
	database := t.runner.GetCurrentDatabase()
	if database == "" {
		return model.Fail("No database selected. Use 'use database <name>'")
	}

	response := t.actionbaseClient.GetTable(database, table)
	if response.IsError() {
		return model.Fail(fmt.Sprintf("Failed to get table '%s'", table))
	}

	t.runner.SetCurrentTable(table)

	return model.SuccessWithResult(fmt.Sprintf("The table is changed to '%s:%s'", database, table))
}

func (t *Table) showIndices(database string, table string) *model.Response {
	response := t.actionbaseClient.GetTable(database, table)
	if response.IsError() {
		return model.Fail(fmt.Sprintf("Failed to get table '%s'", table))
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
			"#":              strconv.Itoa(idx + 1),
			"name":           index.Name,
			"desc":           index.Desc,
			"fields[].name":  name,
			"fields[].order": order,
		}
		results = append(results, data)
	}

	columnOrder := []string{"#", "name", "desc", "fields[].name", "fields[].order"}
	resultMessage := "\n" +
		fmt.Sprintf("%d Indices in %s\n", len(indices), table) +
		util.PrettyPrintRowsWithOrder(results, columnOrder)

	return model.SuccessWithResult(resultMessage)
}

func (t *Table) showGroups(database string, table string) *model.Response {
	response := t.actionbaseClient.GetTable(database, table)
	if response.IsError() {
		return model.Fail(fmt.Sprintf("Failed to get table '%s'", table))
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
			"#":                        strconv.Itoa(idx + 1),
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

	resultMessage := "\n" +
		fmt.Sprintf("%d Groups in %s\n", len(groups), table) +
		util.PrettyPrintRowsWithOrder(results, columnOrder)

	return model.SuccessWithResult(resultMessage)
}
