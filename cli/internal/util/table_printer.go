package util

import (
	"bytes"
	"fmt"
	"strings"

	"github.com/olekukonko/tablewriter"
)

// PrettyPrintWithOrder formats and prints a single row with explicit column order
func PrettyPrintWithOrder(data map[string]interface{}, orderedKeys []string) string {
	rows := []map[string]interface{}{data}
	return ShowWithOrder(rows, orderedKeys)
}

// PrettyPrintRowsWithOrder formats and prints multiple rows with explicit column order
func PrettyPrintRowsWithOrder(rows []map[string]interface{}, orderedKeys []string) string {
	return ShowWithOrder(rows, orderedKeys)
}

// ShowWithOrder renders a list of rows with explicit column order
func ShowWithOrder(rows []map[string]interface{}, orderedKeys []string) string {
	if len(rows) == 0 {
		return ""
	}

	// Use provided order
	headers := orderedKeys

	// Create table buffer
	var buf bytes.Buffer
	table := tablewriter.NewWriter(&buf)
	table.SetHeader(headers)
	table.SetAutoWrapText(false)
	table.SetAutoFormatHeaders(true)
	table.SetHeaderAlignment(tablewriter.ALIGN_LEFT)
	table.SetAlignment(tablewriter.ALIGN_LEFT)
	table.SetCenterSeparator("|")
	table.SetColumnSeparator("|")
	table.SetRowSeparator("-")
	table.SetHeaderLine(true)
	table.SetBorder(true)
	table.SetTablePadding(" ")
	table.SetNoWhiteSpace(false)

	// Add rows
	for _, row := range rows {
		var values []string
		for _, header := range headers {
			value := row[header]
			if value == nil {
				values = append(values, "null")
			} else {
				values = append(values, fmt.Sprintf("%v", value))
			}
		}
		table.Append(values)
	}

	var colors []tablewriter.Colors
	for range headers {
		colors = append(colors, tablewriter.Colors{tablewriter.FgBlueColor})

	}
	table.SetColumnColor(colors...)
	table.Render()
	return strings.TrimSpace(buf.String())
}
