package util

import (
	"bytes"
	"fmt"
	"os"
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

// Show renders a list of rows as a formatted table string
// Column order is not guaranteed due to map iteration
func Show(rows []map[string]interface{}) string {
	if len(rows) == 0 {
		return ""
	}

	// Collect all headers (order not guaranteed)
	headerSet := make(map[string]bool)
	for _, row := range rows {
		for key := range row {
			headerSet[key] = true
		}
	}

	// Convert to slice
	headers := make([]string, 0, len(headerSet))
	for key := range headerSet {
		headers = append(headers, key)
	}

	return ShowWithOrder(rows, headers)
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

	table.Render()
	return strings.TrimSpace(buf.String())
}

// PrintTable prints the table directly to stdout
func PrintTable(rows []map[string]interface{}) {
	fmt.Fprintln(os.Stdout, Show(rows))
}

// PrintTableWithOrder prints the table with explicit column order to stdout
func PrintTableWithOrder(rows []map[string]interface{}, orderedKeys []string) {
	fmt.Fprintln(os.Stdout, ShowWithOrder(rows, orderedKeys))
}
