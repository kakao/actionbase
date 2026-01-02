package util

import (
	"bytes"
	"fmt"
	"strings"

	"github.com/olekukonko/tablewriter"
)

func PrettyPrintWithOrder(data map[string]interface{}, orderedKeys []string) string {
	rows := []map[string]interface{}{data}
	return showWithOrder(rows, orderedKeys)
}

func PrettyPrintRowsWithOrder(rows []map[string]interface{}, orderedKeys []string) string {
	return showWithOrder(rows, orderedKeys)
}

func showWithOrder(rows []map[string]interface{}, orderedKeys []string) string {
	headers := orderedKeys

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

	var colors []tablewriter.Colors
	for range headers {
		colors = append(colors, tablewriter.Colors{tablewriter.FgGreenColor})
	}
	table.SetHeaderColor(colors...)

	for _, row := range rows {
		values := make([]string, len(headers))
		for i, header := range headers {
			value := row[header]
			if value == nil {
				values[i] = "null"
			} else {
				values[i] = fmt.Sprintf("%v", value)
			}
		}
		table.Append(values)
	}

	table.Render()
	return strings.TrimSpace(buf.String())
}
