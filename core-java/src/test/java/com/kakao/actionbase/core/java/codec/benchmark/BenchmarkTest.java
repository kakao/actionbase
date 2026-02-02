package com.kakao.actionbase.core.java.codec.benchmark;

import com.kakao.actionbase.core.java.codec.EdgeStateNoSchema;
import com.kakao.actionbase.core.java.codec.EncodedEdgeStateValue;
import com.kakao.actionbase.core.java.codec.StateCodec;
import com.kakao.actionbase.core.java.codec.StateCodecFactory;
import com.kakao.actionbase.core.java.edge.EdgeState;
import com.kakao.actionbase.core.java.edge.ImmutableEdgeState;
import com.kakao.actionbase.core.java.state.ImmutableStateValue;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

public class BenchmarkTest {
  // Configuration constants
  private static final int ITERATIONS = 1_000_000;
  private static final int WARMUP_ITERATIONS = ITERATIONS / 10; // 10% warmup

  // Object mappers
  private final ObjectMapper jsonMapper = new ObjectMapper();
  private final ObjectMapper cborMapper = new ObjectMapper(new CBORFactory());
  private final ObjectMapper smileMapper = new ObjectMapper(new SmileFactory());

  // Store benchmark results
  private final Map<String, List<Long>> serializationTimes = new HashMap<>();
  private final Map<String, List<Long>> deserializationTimes = new HashMap<>();
  private final Map<String, Integer> encodedSizes = new HashMap<>();

  public static void main(String[] args) {
    try {
      new BenchmarkTest().runBenchmark();
    } catch (JsonProcessingException e) {
      System.err.println("Error occurred during benchmark execution: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /** Runs the benchmark. */
  public void runBenchmark() throws JsonProcessingException {
    // Create test data
    StructType schema = createSchema();
    EdgeState state = createTestEdgeState(schema);

    // Print data
    printTestData(state);

    // Create encoder
    StateCodec encoder = new StateCodecFactory().create();

    try {
      // Warmup
      warmup(state, encoder, schema);

      // Run benchmark
      runCustomEncoderBenchmark(state, encoder, schema);
      runJacksonBenchmark("JSON_HASH_KEY", state, schema, jsonMapper, true);
      runJacksonBenchmark("JSON", state, schema, jsonMapper, false);
      runJacksonBenchmark("CBOR_HASH_KEY", state, schema, cborMapper, true);
      runJacksonBenchmark("CBOR", state, schema, cborMapper, false);
      runJacksonBenchmark("SMILE_HASH_KEY", state, schema, smileMapper, true);
      runJacksonBenchmark("SMILE", state, schema, smileMapper, false);

      // Print results
      printResults();
    } catch (IOException e) {
      throw new RuntimeException("Serialization failed", e);
    }
  }

  /** Creates schema for testing. */
  private StructType createSchema() {
    return ImmutableStructType.builder()
        .addField("createdAt", DataType.LONG, "")
        .addField("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", DataType.LONG, "")
        .addField("B", DataType.LONG, "")
        .addField("C", DataType.LONG, "")
        .build();
  }

  /** Creates edge state for testing. */
  private EdgeState createTestEdgeState(StructType schema) {
    return ImmutableEdgeState.builder()
        .active(true)
        .version(System.currentTimeMillis())
        .source("source")
        .target("target")
        .createdAt(System.currentTimeMillis())
        .deletedAt(System.currentTimeMillis())
        .putProperties(
            "createdAt",
            ImmutableStateValue.builder()
                .version(System.currentTimeMillis())
                .value(System.currentTimeMillis())
                .build())
        .putProperties(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            ImmutableStateValue.builder()
                .version(System.currentTimeMillis())
                .value(System.currentTimeMillis())
                .build())
        .putProperties(
            "B",
            ImmutableStateValue.builder()
                .version(System.currentTimeMillis())
                .value(System.currentTimeMillis())
                .build())
        .putProperties(
            "C",
            ImmutableStateValue.builder()
                .version(System.currentTimeMillis())
                .value(System.currentTimeMillis())
                .build())
        .build();
  }

  /** Prints test data. */
  private void printTestData(EdgeState state) throws JsonProcessingException {
    System.out.println(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state));
    System.out.println(
        jsonMapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(EncodedEdgeStateValue.fromEdgeState(state)));
  }

  /** Performs warmup. */
  private void warmup(EdgeState state, StateCodec encoder, StructType schema) throws IOException {
    System.out.println("Starting warmup...");

    // Warmup custom encoder
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      byte[] value = encoder.encodeEdgeStateValue(state);
      encoder.decodeToEdgeState(value, schema, state.source(), state.target());
    }

    // Warmup JSON_HASH_KEY
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      byte[] value = jsonMapper.writeValueAsBytes(state);
      jsonMapper.readValue(value, EdgeStateNoSchema.class);
    }

    // Warmup CBOR_HASH_KEY
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      byte[] value = cborMapper.writeValueAsBytes(state);
      cborMapper.readValue(value, EdgeStateNoSchema.class);
    }

    // Warmup SMILE_HASH_KEY
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      byte[] value = smileMapper.writeValueAsBytes(state);
      smileMapper.readValue(value, EdgeStateNoSchema.class);
    }

    System.out.println("Warmup complete\n");
  }

  /** Runs custom encoder benchmark. */
  private void runCustomEncoderBenchmark(EdgeState state, StateCodec encoder, StructType schema)
      throws IOException {
    List<Long> serTimes = new ArrayList<>();
    List<Long> deserTimes = new ArrayList<>();
    byte[] serialized = null;

    for (int i = 0; i < ITERATIONS; i++) {
      // Measure serialization time
      long start = System.nanoTime();
      serialized = encoder.encodeEdgeStateValue(state);
      long serTime = System.nanoTime() - start;
      serTimes.add(serTime);

      // Measure deserialization time
      start = System.nanoTime();
      encoder.decodeToEdgeState(serialized, schema, state.source(), state.target());
      long deserTime = System.nanoTime() - start;
      deserTimes.add(deserTime);
    }

    // Store results
    serializationTimes.put("Custom", serTimes);
    deserializationTimes.put("Custom", deserTimes);
    encodedSizes.put("Custom", serialized.length);
  }

  /**
   * Runs Jackson-based serialization benchmark.
   *
   * @param useHashKey true: use hash key, false: use regular key
   */
  private void runJacksonBenchmark(
      String name, EdgeState state, StructType schema, ObjectMapper mapper, boolean useHashKey)
      throws IOException {
    List<Long> serTimes = new ArrayList<>();
    List<Long> deserTimes = new ArrayList<>();
    byte[] serialized = null;

    for (int i = 0; i < ITERATIONS; i++) {
      // Measure serialization time
      long start = System.nanoTime();
      if (useHashKey) {
        EncodedEdgeStateValue compactState = EncodedEdgeStateValue.fromEdgeState(state);
        serialized = mapper.writeValueAsBytes(compactState);
      } else {
        serialized = mapper.writeValueAsBytes(state);
      }
      long serTime = System.nanoTime() - start;
      serTimes.add(serTime);

      // Measure deserialization time
      start = System.nanoTime();
      if (useHashKey) {
        EncodedEdgeStateValue deserializedCompactEdgeState =
            mapper.readValue(serialized, EncodedEdgeStateValue.class);
        deserializedCompactEdgeState.toEdgeState(schema, state.source(), state.target());
      } else {
        mapper.readValue(serialized, EdgeStateNoSchema.class).toEdgeState(schema);
      }
      long deserTime = System.nanoTime() - start;
      deserTimes.add(deserTime);
    }

    // Store results
    serializationTimes.put(name, serTimes);
    deserializationTimes.put(name, deserTimes);
    encodedSizes.put(name, serialized.length);
  }

  /** Prints benchmark results. */
  private void printResults() {
    System.out.println("\n=== Benchmark Results (" + ITERATIONS + " iterations) ===");

    // Print size results
    System.out.println("\nData Size:");
    int baselineSize = encodedSizes.get("Custom");
    System.out.printf("%-12s: %6d bytes (baseline)%n", "Custom", baselineSize);

    // Print other formats sorted, excluding Custom
    encodedSizes.entrySet().stream()
        .filter(entry -> !entry.getKey().equals("Custom"))
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              int size = entry.getValue();
              double percentChange = (double) (size - baselineSize) / baselineSize * 100;
              System.out.printf(
                  "%-12s: %6d bytes (%+.2f%%)%n", entry.getKey(), size, percentChange);
            });

    // Visualize size comparison
    System.out.println("\n[Data Size Comparison (vs Custom)]");
    printVisualComparison(encodedSizes);

    // Print serialization time results
    System.out.println("\nSerialization Time (microseconds):");
    Map<String, Double> serAvgs = printTimingStats(serializationTimes);

    // Visualize serialization time comparison
    System.out.println("\n[Serialization Time Comparison (vs Custom)]");
    printVisualComparison(serAvgs);

    // Print deserialization time results
    System.out.println("\nDeserialization Time (microseconds):");
    Map<String, Double> deserAvgs = printTimingStats(deserializationTimes);

    // Visualize deserialization time comparison
    System.out.println("\n[Deserialization Time Comparison (vs Custom)]");
    printVisualComparison(deserAvgs);

    // Summary information
    System.out.println("\n=== Summary ===");
    printSummary(serAvgs, deserAvgs, encodedSizes);
  }

  /** Prints visual comparison results. */
  private <T extends Number> void printVisualComparison(Map<String, T> metrics) {
    final int BAR_LENGTH = 50; // Maximum bar length
    String baselineKey = "Custom";
    double baselineValue = metrics.get(baselineKey).doubleValue();

    // Print baseline (Custom) bar
    System.out.printf("%-12s: %s (100%%)%n", baselineKey, getBar(BAR_LENGTH / 2, '#'));

    // Print other format bars
    metrics.entrySet().stream()
        .filter(entry -> !entry.getKey().equals(baselineKey))
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              double value = entry.getValue().doubleValue();
              double ratio = value / baselineValue;
              int barLength = (int) (BAR_LENGTH / 2 * ratio);
              char barChar = ratio > 1.0 ? '!' : '#';

              System.out.printf(
                  "%-12s: %s (%+.2f%%)%n",
                  entry.getKey(), getBar(barLength, barChar), (ratio - 1.0) * 100);
            });
  }

  /** Creates a bar with specified length and character. */
  private String getBar(int length, char c) {
    if (length <= 0) return "";
    char[] bar = new char[length];
    Arrays.fill(bar, c);
    return new String(bar);
  }

  /** Prints benchmark summary information. */
  private void printSummary(
      Map<String, Double> serAvgs, Map<String, Double> deserAvgs, Map<String, Integer> sizes) {
    DecimalFormat df = new DecimalFormat("+#,##0.00;-#,##0.00");

    double baselineSer = serAvgs.get("Custom");
    double baselineDeser = deserAvgs.get("Custom");
    int baselineSize = sizes.get("Custom");

    // Print header
    System.out.println(
        "Format        Size Change%   Serialization Time Change%   Deserialization Time Change%   Total Change%");
    System.out.println("---------------------------------------------------------------------");

    // Custom is baseline, so print separately
    System.out.printf(
        "%-12s  %10s      %10s       %10s      %10s%n",
        "Custom", "baseline", "baseline", "baseline", "baseline");

    // Print other formats
    sizes.keySet().stream()
        .filter(format -> !format.equals("Custom"))
        .sorted()
        .forEach(
            format -> {
              int size = sizes.get(format);
              double sizePct = (double) (size - baselineSize) / baselineSize * 100;

              double serTime = serAvgs.get(format);
              double serPct = (serTime - baselineSer) / baselineSer * 100;

              double deserTime = deserAvgs.get(format);
              double deserPct = (deserTime - baselineDeser) / baselineDeser * 100;

              // Total change rate (weights can be applied to each element)
              double totalPct = (sizePct + serPct + deserPct) / 3;

              System.out.printf(
                  "%-12s  %10s%%     %10s%%      %10s%%     %10s%%%n",
                  format,
                  df.format(sizePct),
                  df.format(serPct),
                  df.format(deserPct),
                  df.format(totalPct));
            });
  }

  /**
   * Prints timing statistics.
   *
   * @return Map containing average time for each format
   */
  private Map<String, Double> printTimingStats(Map<String, List<Long>> timings) {
    // Map to store average time for each format
    Map<String, Double> averages = new HashMap<>();

    // Calculate Baseline (Custom) statistics
    LongSummaryStatistics baselineStats =
        timings.get("Custom").stream()
            .mapToLong(t -> t / 1000) // Convert nanoseconds to microseconds
            .summaryStatistics();
    double baselineAvg = baselineStats.getAverage();
    averages.put("Custom", baselineAvg);

    // Print Baseline
    System.out.printf(
        "%-12s: avg=%.2f, min=%d, max=%d (baseline)%n",
        "Custom", baselineAvg, baselineStats.getMin(), baselineStats.getMax());

    // Calculate and print statistics for remaining formats, sorted
    timings.entrySet().stream()
        .filter(entry -> !entry.getKey().equals("Custom"))
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              String format = entry.getKey();
              List<Long> times = entry.getValue();

              LongSummaryStatistics stats =
                  times.stream()
                      .mapToLong(t -> t / 1000) // Convert nanoseconds to microseconds
                      .summaryStatistics();

              double avgTime = stats.getAverage();
              averages.put(format, avgTime);

              double avgPercent = (avgTime - baselineAvg) / baselineAvg * 100;

              System.out.printf(
                  "%-12s: avg=%.2f (%+.2f%%), min=%d, max=%d%n",
                  format, avgTime, avgPercent, stats.getMin(), stats.getMax());
            });

    return averages;
  }
}
