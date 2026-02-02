package com.kakao.actionbase.core.java.dataframe;

import com.kakao.actionbase.core.java.dataframe.row.Row;
import com.kakao.actionbase.core.java.types.common.StructField;

import java.util.ArrayList;
import java.util.List;

// package-private
class DataFramePrinter {

  /** Prints DataFrame as ASCII table with default settings (maxRows=20, truncate=20). */
  static void show(DataFrame df) {
    System.out.println(showString(df));
  }

  /**
   * Prints DataFrame content as ASCII table to console.
   *
   * @param maxRows Maximum number of rows to output (e.g., 20)
   * @param truncate Maximum number of characters per cell (e.g., 20, -1 for no limit)
   */
  static void show(DataFrame df, int maxRows, int truncate) {
    System.out.println(showString(df, maxRows, truncate));
  }

  /** Returns DataFrame as ASCII table string with default settings (maxRows=20, truncate=20). */
  static String showString(DataFrame df) {
    return showString(df, 20, 20);
  }

  /**
   * Returns DataFrame content as ASCII table string.
   *
   * @param maxRows Maximum number of rows to output (e.g., 20)
   * @param truncate Maximum number of characters per cell (e.g., 20, -1 for no limit)
   */
  static String showString(DataFrame df, int maxRows, int truncate) {
    if (df.data().isEmpty()) {
      return "Empty DataFrame";
    }

    StringBuilder result = new StringBuilder();

    // 1. Extract column headers
    List<StructField> fields = df.schema().fields();
    String[] headers = new String[fields.size()];
    for (int i = 0; i < fields.size(); i++) {
      headers[i] = fields.get(i).name();
    }

    // 2. Convert headers and each data row to string arrays
    List<String[]> stringData = new ArrayList<>();
    stringData.add(headers);

    // Determine maximum number of rows to output (choose smaller value between actual data row
    // count and maxRows)
    int rowsToShow = Math.min(maxRows, df.data().size());
    for (int i = 0; i < rowsToShow; i++) {
      Row row = df.data().get(i);
      String[] stringRow = new String[row.size()];
      for (int j = 0; j < row.size(); j++) {
        String cell = (row.get(j) == null) ? "null" : cellToString(row.get(j), truncate);
        if (truncate > 0 && cell.length() > truncate) {
          cell = cell.substring(0, truncate - 3) + "...";
        }
        stringRow[j] = cell;
      }
      stringData.add(stringRow);
    }

    // 3. Calculate maximum width for each column (add 2 spaces padding on left and right)
    int[] colWidths = new int[fields.size()];
    for (int j = 0; j < fields.size(); j++) {
      int maxWidth = 0;
      for (String[] row : stringData) {
        if (row[j].length() > maxWidth) {
          maxWidth = row[j].length();
        }
      }
      colWidths[j] = maxWidth + 2;
    }

    // 4. Create separator line
    StringBuilder sepBuilder = new StringBuilder();
    sepBuilder.append("+");
    for (int j = 0; j < fields.size(); j++) {
      sepBuilder.append(repeat("-", colWidths[j]));
      sepBuilder.append("+");
    }
    String separatorLine = sepBuilder.toString();

    // 5. Output rows
    result.append(separatorLine).append("\n");
    for (int i = 0; i < stringData.size(); i++) {
      String[] row = stringData.get(i);
      StringBuilder rowBuilder = new StringBuilder();
      rowBuilder.append("|");
      for (int j = 0; j < row.length; j++) {
        String cell = row[j];
        int padding = colWidths[j] - cell.length();
        int leftPad = padding / 2;
        int rightPad = padding - leftPad;
        rowBuilder.append(repeat(" ", leftPad));
        rowBuilder.append(cell);
        rowBuilder.append(repeat(" ", rightPad));
        rowBuilder.append("|");
      }
      result.append(rowBuilder).append("\n");
      if (i == 0) { // Output separator line after header row
        result.append(separatorLine).append("\n");
      }
    }
    result.append(separatorLine).append("\n");

    // 6. Display if there are rows not output
    if (df.count() > maxRows) {
      result.append("... ").append(df.count() - maxRows).append(" more rows").append("\n");
    }

    return result.toString();
  }

  private static String repeat(String s, int count) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < count; i++) {
      builder.append(s);
    }
    return builder.toString();
  }

  private static String cellToString(Object value, int truncate) {
    String str;
    if (value == null) {
      str = "null";
    } else if (value.getClass().isArray()) {
      // For arrays, distinguish between Object[] and primitive arrays and output all
      if (value instanceof Object[]) {
        str = java.util.Arrays.deepToString((Object[]) value);
      } else if (value instanceof int[]) {
        str = java.util.Arrays.toString((int[]) value);
      } else if (value instanceof long[]) {
        str = java.util.Arrays.toString((long[]) value);
      } else if (value instanceof double[]) {
        str = java.util.Arrays.toString((double[]) value);
      } else if (value instanceof float[]) {
        str = java.util.Arrays.toString((float[]) value);
      } else if (value instanceof boolean[]) {
        str = java.util.Arrays.toString((boolean[]) value);
      } else if (value instanceof char[]) {
        str = java.util.Arrays.toString((char[]) value);
      } else if (value instanceof byte[]) {
        str = java.util.Arrays.toString((byte[]) value);
      } else if (value instanceof short[]) {
        str = java.util.Arrays.toString((short[]) value);
      } else {
        str = value.toString();
      }
    } else {
      str = value.toString();
    }
    if (truncate > 0 && str.length() > truncate) {
      str = str.substring(0, truncate - 3) + "...";
    }
    return str;
  }
}
