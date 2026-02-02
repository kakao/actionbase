package com.kakao.actionbase.core.java.logo;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;

/** Class that generates Actionbase logo in SVG format */
public class ActionbaseLogo {

  // Default logo constant definitions
  private static final int DEFAULT_WIDTH = 220;
  private static final int DEFAULT_HEIGHT = 250;
  private static final String DEFAULT_BORDER_COLOR = "white";
  private static final double DEFAULT_BORDER_SIZE = 1.5;
  private static final String DEFAULT_COLOR = "#00C2A0";
  private static final double DEFAULT_STROKE_WIDTH = 7;
  private static final int DEFAULT_SMALL_NODE_RADIUS = 7;
  private static final int DEFAULT_LARGE_NODE_RADIUS = 17;

  // Base values for coordinate calculation
  private static final double BASE_X = 107.5;
  private static final double BASE_Y = 20.0;

  // Node position constant definitions (relative to base values)
  private static final double X1 = 1;
  private static final double Y1 = 1;
  private static final double X2 = 15.3 / BASE_X;
  private static final double Y2 = 70.8 / BASE_Y;
  private static final double X3 = 205.5 / BASE_X;
  private static final double Y3 = 178.5 / BASE_Y;
  private static final double Y4 = 232.0 / BASE_Y;
  private static final double XHL1 = 174.3 / BASE_X;
  private static final double YHL1 = 99.5 / BASE_Y;
  private static final double XHL2 = 39.5 / BASE_X;
  private static final double YHL2 = 130.5 / BASE_Y;
  private static final double XHL3 = 142.8 / BASE_X;
  private static final double YHL3 = 186.0 / BASE_Y;

  // Object for decimal formatting
  private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#0.0");

  /** Inner class that defines logo position and size */
  static class Position {
    final double baseX;
    final double baseY;
    final double scaleX;
    final double scaleY;

    public Position(double baseX, double baseY, double scaleX, double scaleY) {
      this.baseX = baseX;
      this.baseY = baseY;
      this.scaleX = scaleX;
      this.scaleY = scaleY;
    }
  }

  /** Inner class that defines logo style */
  static class Style {
    final String color;
    final double width;

    public Style(String color, double width) {
      this.color = color;
      this.width = width;
    }
  }

  /**
   * Creates logo with default parameters
   *
   * @return Logo string in SVG format
   */
  public String createLogo() {
    return createLogo(
        DEFAULT_WIDTH,
        DEFAULT_HEIGHT,
        DEFAULT_BORDER_COLOR,
        DEFAULT_BORDER_SIZE,
        DEFAULT_COLOR,
        DEFAULT_COLOR,
        DEFAULT_COLOR,
        DEFAULT_STROKE_WIDTH,
        DEFAULT_SMALL_NODE_RADIUS,
        DEFAULT_LARGE_NODE_RADIUS,
        false);
  }

  /**
   * Generates Actionbase logo SVG with custom parameters
   *
   * @param width Logo width
   * @param height Logo height
   * @param borderColor Border color
   * @param borderSize Border size
   * @param edgeColor Edge color
   * @param smallNodeColor Small node color
   * @param largeNodeColor Large node color
   * @param strokeWidth Stroke width
   * @param smallNodeRadius Small node radius
   * @param largeNodeRadius Large node radius
   * @param background Whether to include background
   * @return Logo string in SVG format
   */
  public String createLogo(
      int width,
      int height,
      String borderColor,
      double borderSize,
      String edgeColor,
      String smallNodeColor,
      String largeNodeColor,
      double strokeWidth,
      int smallNodeRadius,
      int largeNodeRadius,
      boolean background) {

    // Calculate scale
    double scaleX = (double) width / DEFAULT_WIDTH;
    double scaleY = (double) height / DEFAULT_HEIGHT;

    Position position = new Position(BASE_X, BASE_Y, scaleX, scaleY);

    // Define styles
    Style backgroundEdgeStyle = new Style(borderColor, strokeWidth + borderSize * 2);
    Style backgroundSmallNodeStyle = new Style(smallNodeColor, smallNodeRadius + borderSize);
    Style backgroundLargeNodeStyle = new Style(largeNodeColor, largeNodeRadius + borderSize);
    Style edgeStyle = new Style(edgeColor, strokeWidth);
    Style smallNodeStyle = new Style(smallNodeColor, smallNodeRadius);
    Style largeNodeStyle = new Style(largeNodeColor, largeNodeRadius);

    StringBuilder svg = new StringBuilder();

    // Add SVG header
    appendSvgHeader(svg, width, height);

    // Add background elements (if requested)
    if (background) {
      appendBackground(
          svg, position, backgroundEdgeStyle, backgroundSmallNodeStyle, backgroundLargeNodeStyle);
    }

    // Add graph edges
    appendEdges(svg, position, edgeStyle);

    // Add small nodes
    appendSmallNodes(svg, position, smallNodeStyle);

    // Add large nodes
    appendLargeNodes(svg, position, largeNodeStyle);

    // Add SVG closing tag
    svg.append("</svg>");

    return svg.toString();
  }

  /** Add SVG header */
  private void appendSvgHeader(StringBuilder svg, int width, int height) {
    svg.append("<svg xmlns='http://www.w3.org/2000/svg' width='")
        .append(width)
        .append("' height='")
        .append(height)
        .append("' viewBox='0 0 ")
        .append(width)
        .append(" ")
        .append(height)
        .append("'>\n");
  }

  /** Add background elements */
  private void appendBackground(
      StringBuilder svg,
      Position position,
      Style edgeStyle,
      Style smallNodeStyle,
      Style largeNodeStyle) {

    // Add background edges
    appendBackgroundEdges(svg, position, edgeStyle);

    // Add background nodes
    appendCircle(svg, X1, Y1, smallNodeStyle, position);
    appendCircle(svg, X2, Y2, smallNodeStyle, position);
    appendCircle(svg, X2, Y3, smallNodeStyle, position);
    appendCircle(svg, X1, Y4, smallNodeStyle, position);
    appendCircle(svg, X3, Y3, smallNodeStyle, position);
    appendCircle(svg, X3, Y2, smallNodeStyle, position);

    appendCircle(svg, XHL1, YHL1, largeNodeStyle, position);
    appendCircle(svg, XHL2, YHL2, largeNodeStyle, position);
    appendCircle(svg, XHL3, YHL3, largeNodeStyle, position);
  }

  /** Add background edges */
  private void appendBackgroundEdges(StringBuilder svg, Position position, Style edgeStyle) {
    // Basic shape edges
    appendLine(svg, X1, Y1, X2, Y2, edgeStyle, position);
    appendLine(svg, X1, Y1, X3, Y2, edgeStyle, position);
    appendLine(svg, X2, Y2, X2, Y3, edgeStyle, position);
    appendLine(svg, X3, Y2, X3, Y3, edgeStyle, position);
    appendLine(svg, X2, Y3, X1, Y4, edgeStyle, position);
    appendLine(svg, X3, Y3, X1, Y4, edgeStyle, position);

    // Edges connecting highlighted nodes
    appendLine(svg, XHL1, YHL1, X1, Y1, edgeStyle, position);
    appendLine(svg, XHL1, YHL1, X3, Y2, edgeStyle, position);
    appendLine(svg, XHL1, YHL1, X3, Y3, edgeStyle, position);
    appendLine(svg, XHL1, YHL1, XHL3, YHL3, edgeStyle, position);
    appendLine(svg, XHL3, YHL3, X3, Y3, edgeStyle, position);
    appendLine(svg, XHL3, YHL3, X1, Y4, edgeStyle, position);
    appendLine(svg, XHL2, YHL2, X1, Y1, edgeStyle, position);
    appendLine(svg, XHL2, YHL2, X2, Y2, edgeStyle, position);
    appendLine(svg, XHL2, YHL2, X2, Y3, edgeStyle, position);
    appendLine(svg, XHL2, YHL2, X1, Y4, edgeStyle, position);
    appendLine(svg, XHL2, YHL2, XHL3, YHL3, edgeStyle, position);
    appendLine(svg, XHL2, YHL2, XHL1, YHL1, edgeStyle, position);
  }

  /** Add graph edges */
  private void appendEdges(StringBuilder svg, Position position, Style edgeStyle) {
    svg.append("  <!-- Graph edges with specified stroke width -->\n");

    // Add basic edges
    appendBackgroundEdges(svg, position, edgeStyle);
  }

  /** Add small nodes */
  private void appendSmallNodes(StringBuilder svg, Position position, Style nodeStyle) {
    svg.append("\n  <!-- Small nodes -->\n");
    appendCircle(svg, X1, Y1, nodeStyle, position);
    appendCircle(svg, X2, Y2, nodeStyle, position);
    appendCircle(svg, X2, Y3, nodeStyle, position);
    appendCircle(svg, X1, Y4, nodeStyle, position);
    appendCircle(svg, X3, Y3, nodeStyle, position);
    appendCircle(svg, X3, Y2, nodeStyle, position);
  }

  /** Add large nodes */
  private void appendLargeNodes(StringBuilder svg, Position position, Style nodeStyle) {
    svg.append("\n  <!-- Highlighted larger nodes -->\n");
    appendCircle(svg, XHL1, YHL1, nodeStyle, position);
    appendCircle(svg, XHL2, YHL2, nodeStyle, position);
    appendCircle(svg, XHL3, YHL3, nodeStyle, position);
  }

  /** Add line element */
  private void appendLine(
      StringBuilder svg,
      double relX1,
      double relY1,
      double relX2,
      double relY2,
      Style style,
      Position position) {

    // Convert relative coordinates to absolute coordinates
    double x1 = relX1 * position.baseX * position.scaleX;
    double y1 = relY1 * position.baseY * position.scaleY;
    double x2 = relX2 * position.baseX * position.scaleX;
    double y2 = relY2 * position.baseY * position.scaleY;

    svg.append("  <line x1='")
        .append(format(x1))
        .append("' y1='")
        .append(format(y1))
        .append("' x2='")
        .append(format(x2))
        .append("' y2='")
        .append(format(y2))
        .append("' stroke='")
        .append(style.color)
        .append("' stroke-width='")
        .append(style.width)
        .append("'/>\n");
  }

  /** Add circle element */
  private void appendCircle(
      StringBuilder svg, double relCx, double relCy, Style style, Position position) {

    // Convert relative coordinates to absolute coordinates
    double cx = relCx * position.baseX * position.scaleX;
    double cy = relCy * position.baseY * position.scaleY;
    double radius = style.width * Math.min(position.scaleX, position.scaleY);

    svg.append("  <circle cx='")
        .append(format(cx))
        .append("' cy='")
        .append(format(cy))
        .append("' r='")
        .append(format(radius))
        .append("' fill='")
        .append(style.color)
        .append("' />\n");
  }

  /** Decimal formatting */
  private static String format(double value) {
    return DECIMAL_FORMAT.format(value);
  }

  /** Usage example */
  public static void main(String[] args) {
    ActionbaseLogo logo = new ActionbaseLogo();

    // Create and save default logo
    String svg = logo.createLogo();
    System.out.println("Default logo creation completed");
    saveToResourcesDirectory(svg, "actionbase_logo.svg");

    // Additional logo 1 (wide black background with Actionbase text)
    String logo2 = createLogoWithText(1190, 250, "black", true);
    saveToResourcesDirectory(logo2, "actionbase_logo2.svg");
    System.out.println("Logo2 creation completed");

    // Additional logo 2 (white background with small Actionbase text)
    String logo3 = createLogoWithText(220, 300, "white", false);
    saveToResourcesDirectory(logo3, "actionbase_logo3.svg");
    System.out.println("Logo3 creation completed");
  }

  /**
   * Creates logo with Actionbase text
   *
   * @param width Width
   * @param height Height
   * @param backgroundColor Background color
   * @param isLarge Whether text is large
   * @return Logo string in SVG format
   */
  private static String createLogoWithText(
      int width, int height, String backgroundColor, boolean isLarge) {
    ActionbaseLogo logoCreator = new ActionbaseLogo();

    // Create base logo
    String baseColor = "#00C2A0";
    String baseSvg =
        logoCreator.createLogo(
            220, 250, "white", 1.5, baseColor, baseColor, baseColor, 7, 7, 17, false);

    // Add background and text
    StringBuilder svg = new StringBuilder();
    svg.append("<svg xmlns='http://www.w3.org/2000/svg' width='")
        .append(width)
        .append("' height='")
        .append(height)
        .append("' viewBox='0 0 ")
        .append(width)
        .append(" ")
        .append(height)
        .append("'>\n");

    // Add background
    svg.append("  <rect width=\"100%\" height=\"100%\" fill=\"")
        .append(backgroundColor)
        .append("\"/>\n");

    // Remove SVG tags from base logo and extract content only
    String logoContent = baseSvg.substring(baseSvg.indexOf(">") + 1, baseSvg.lastIndexOf("</svg>"));
    svg.append(logoContent);

    // Add text - use relative values
    double textX;
    double textY;
    double fontSize; // Left aligned
    // 93% of total height
    // 18% of logo width
    if (isLarge) {
      // Text for large logo (large text on the right)
      textX = width * 0.21;
      textY = height * 0.72;
      fontSize = Math.min(width, height) * 0.68;

    } else {
      // Text for small logo (small text at the bottom)
      textX = width * 0.0;
      textY = height * 0.93;
      fontSize = width * 0.18;
    }
    svg.append("  <!-- Actionbase text -->\n")
        .append("  <text x='")
        .append(format(textX))
        .append("' y='")
        .append(format(textY))
        .append("' font-family='Arial, sans-serif' font-weight='700' ")
        .append("font-size='")
        .append(format(fontSize))
        .append("' fill='")
        .append(baseColor)
        .append("'>Actionbase</text>\n");

    svg.append("</svg>");
    return svg.toString();
  }

  /** Finds the project's resources directory and saves SVG */
  private static void saveToResourcesDirectory(String svg, String fileName) {
    try {
      // Find resources directory based on class location
      String resourcePath = findResourcesDirectory();
      Path filePath = Paths.get(resourcePath, fileName);

      // Create directory if needed
      Files.createDirectories(filePath.getParent());
      Files.write(filePath, svg.getBytes());
      System.out.println("SVG saved to " + filePath.toAbsolutePath());
    } catch (IOException e) {
      System.err.println("Error saving SVG file: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Searches for project resources directory location. Checks multiple possible paths and returns
   * appropriate resources directory.
   *
   * @return Resources directory path
   * @throws IOException If resources directory cannot be found
   */
  private static String findResourcesDirectory() throws IOException {
    // 1. Find resources directory based on current class location
    URL classResource =
        ActionbaseLogo.class.getResource(ActionbaseLogo.class.getSimpleName() + ".class");
    if (classResource != null) {
      String classPath = classResource.getPath();
      // Find target/classes or build/classes in classpath and change to src/main/resources
      if (classPath.contains("/target/classes/") || classPath.contains("\\target\\classes\\")) {
        return classPath.replaceFirst(
            "([/\\\\])target([/\\\\])classes([/\\\\]).*", "$1src$2main$3resources");
      } else if (classPath.contains("/build/classes/")
          || classPath.contains("\\build\\classes\\")) {
        return classPath.replaceFirst(
            "([/\\\\])build([/\\\\])classes([/\\\\]).*", "$1src$2main$3resources");
      }
    }

    // 2. Find based on current working directory
    String currentDir = System.getProperty("user.dir");
    String[] possiblePaths = {
      currentDir + "/src/main/resources",
      currentDir + "\\src\\main\\resources",
      currentDir + "/resources",
      currentDir + "\\resources"
    };

    for (String path : possiblePaths) {
      if (Files.exists(Paths.get(path))) {
        return path;
      }
    }

    // 3. If resources directory cannot be found, create resources folder in current directory
    Path resourcesDir = Paths.get(currentDir, "resources");
    Files.createDirectories(resourcesDir);
    return resourcesDir.toString();
  }
}
