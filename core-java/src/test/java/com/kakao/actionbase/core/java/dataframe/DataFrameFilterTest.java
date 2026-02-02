package com.kakao.actionbase.core.java.dataframe;

import static org.junit.jupiter.api.Assertions.*;

import com.kakao.actionbase.core.java.dataframe.row.ImmutableArrayRow;
import com.kakao.actionbase.core.java.dataframe.row.Row;
import com.kakao.actionbase.core.java.types.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DataFrameFilter Test")
class DataFrameFilterTest {

  private DataFrame testDataFrame;

  @BeforeEach
  void setUp() {
    // Define schema
    StructType schema =
        StructType.builder()
            .addField("id", DataType.LONG)
            .addField("name", DataType.STRING)
            .addField("age", DataType.LONG)
            .addField("score", DataType.DOUBLE)
            .addField("category", DataType.STRING)
            .build();

    // Create test data
    List<Row> data = new ArrayList<>();
    data.add(
        ImmutableArrayRow.builder().data(1L, "Alice", 25L, 85.5, "student").schema(schema).build());
    data.add(
        ImmutableArrayRow.builder().data(2L, "Bob", 30L, 92.0, "employee").schema(schema).build());
    data.add(
        ImmutableArrayRow.builder()
            .data(3L, "Charlie", 22L, 78.3, "student")
            .schema(schema)
            .build());
    data.add(
        ImmutableArrayRow.builder()
            .data(4L, "David", 35L, 95.7, "employee")
            .schema(schema)
            .build());
    data.add(
        ImmutableArrayRow.builder().data(5L, "Eve", 28L, 88.2, "employee").schema(schema).build());
    data.add(ImmutableArrayRow.builder().data(6L, "Frank", 40L, 91.5, null).schema(schema).build());

    testDataFrame = ImmutableDataFrame.of(data, schema, data.size(), data.size(), null, false);
  }

  @Nested
  @DisplayName("where method test")
  class WhereMethodTest {

    @Test
    @DisplayName("Filtering with Eq condition should work correctly")
    void shouldFilterWithEqPredicate() {
      // Given
      WherePredicate predicate = WherePredicate.eq("category", "student");

      // When
      DataFrame filtered = testDataFrame.where(predicate);

      // Then
      assertEquals(2, filtered.count(), "There should be 2 people in the student category");
      assertEquals("Alice", filtered.data().get(0).get(1), "First student should be Alice");
      assertEquals("Charlie", filtered.data().get(1).get(1), "Second student should be Charlie");
    }

    @Test
    @DisplayName("Filtering with In condition should work correctly")
    void shouldFilterWithInPredicate() {
      // Given
      WherePredicate predicate = WherePredicate.in("id", Arrays.asList(1L, 3L, 5L));

      // When
      DataFrame filtered = testDataFrame.where(predicate);

      // Then
      assertEquals(3, filtered.count(), "There should be 3 people with IDs 1, 3, 5");
      List<String> names = new ArrayList<>();
      for (Row row : filtered.data()) {
        names.add((String) row.get(1));
      }
      assertTrue(
          names.containsAll(Arrays.asList("Alice", "Charlie", "Eve")),
          "Filtered results should include Alice, Charlie, Eve");
    }

    @Test
    @DisplayName("Filtering with Gt condition should work correctly")
    void shouldFilterWithGtPredicate() {
      // Given
      WherePredicate predicate = WherePredicate.gt("age", 30L);

      // When
      DataFrame filtered = testDataFrame.where(predicate);

      // Then
      assertEquals(2, filtered.count(), "There should be 2 people over 30 years old");
      for (Row row : filtered.data()) {
        long age = (long) row.get(2);
        assertTrue(age > 30, "All results should be over 30 years old");
      }
    }

    @Test
    @DisplayName("Filtering with Gte condition should work correctly")
    void shouldFilterWithGtePredicate() {
      // Given
      WherePredicate predicate = WherePredicate.gte("age", 30L);

      // When
      DataFrame filtered = testDataFrame.where(predicate);

      // Then
      assertEquals(3, filtered.count(), "There should be 3 people 30 years old or older");
      for (Row row : filtered.data()) {
        long age = (long) row.get(2);
        assertTrue(age >= 30, "All results should be 30 years old or older");
      }
    }

    @Test
    @DisplayName("Filtering with Lt condition should work correctly")
    void shouldFilterWithLtPredicate() {
      // Given
      WherePredicate predicate = WherePredicate.lt("score", 85.0);

      // When
      DataFrame filtered = testDataFrame.where(predicate);

      // Then
      assertEquals(1, filtered.count(), "There should be 1 person with score less than 85.0");
      for (Row row : filtered.data()) {
        double score = (double) row.get(3);
        assertTrue(score < 85.0, "All results should have score less than 85.0");
      }
    }

    @Test
    @DisplayName("Filtering with Lte condition should work correctly")
    void shouldFilterWithLtePredicate() {
      // Given
      WherePredicate predicate = WherePredicate.lte("score", 85.5);

      // When
      DataFrame filtered = testDataFrame.where(predicate);

      // Then
      assertEquals(2, filtered.count(), "There should be 2 people with score 85.5 or less");
      for (Row row : filtered.data()) {
        double score = (double) row.get(3);
        assertTrue(score <= 85.5, "All results should have score 85.5 or less");
      }
    }

    @Test
    @DisplayName("Filtering with Between condition should work correctly")
    void shouldFilterWithBetweenPredicate() {
      // Given
      WherePredicate predicate = WherePredicate.between("age", 25L, 35L);

      // When
      DataFrame filtered = testDataFrame.where(predicate);

      // Then
      assertEquals(4, filtered.count(), "There should be 4 people between 25 and 35 years old");
      for (Row row : filtered.data()) {
        long age = (long) row.get(2);
        assertTrue(age >= 25 && age <= 35, "All results should be between 25 and 35 years old");
      }
    }

    @Test
    @DisplayName("Filtering with IsNull condition should work correctly")
    void shouldFilterWithIsNullPredicate() {
      // Given
      WherePredicate predicate = WherePredicate.isNull("category");

      // When
      DataFrame filtered = testDataFrame.where(predicate);

      // Then
      assertEquals(1, filtered.count(), "There should be 1 person with null category");
      assertNull(filtered.data().get(0).get(4), "Category value should be null");
      assertEquals(
          "Frank", filtered.data().get(0).get(1), "Person with null category should be Frank");
    }

    @Test
    @DisplayName("Should return original DataFrame for null input")
    void shouldReturnOriginalDataFrameForNullInput() {
      // When
      DataFrame result1 = DataFrameFilter.where(null, WherePredicate.eq("id", 1L));
      DataFrame result2 = DataFrameFilter.where(testDataFrame, null);

      // Then
      assertNull(result1, "Should return null for null DataFrame input");
      assertSame(
          testDataFrame, result2, "Should return original DataFrame for null predicate input");
    }
  }
}
