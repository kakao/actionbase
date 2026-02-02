package com.kakao.actionbase.core.java.compat.v2;

import com.kakao.actionbase.core.java.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.immutables.value.Generated;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Immutable implementation of {@link DdlStatusResult}.
 *
 * <p>Use the builder to create immutable instances: {@code ImmutableDdlResult.builder()}.
 */
@Generated(from = "DdlResult", generator = "Immutables")
@SuppressWarnings({"all"})
@javax.annotation.Generated("org.immutables.processor.ProxyProcessor")
public final class ImmutableDdlStatusResult<Result> implements DdlStatusResult<Result> {
  private final String status;
  private final Result result;
  private final @Nullable String message;

  private ImmutableDdlStatusResult(
      String status, @Nullable Result result, @Nullable String message) {
    this.status = status;
    this.result = result;
    this.message = message;
  }

  /**
   * @param <Result> generic parameter Result
   * @param json A JSON-bindable data structure
   * @return An immutable value type
   * @deprecated Do not use this method directly, it exists only for the <em>Jackson</em>-binding
   *     infrastructure
   */
  @Deprecated
  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  static <Result> ImmutableDdlStatusResult<Result> fromJson(Json<Result> json) {
    Builder<Result> builder = ImmutableDdlStatusResult.<Result>builder();
    if (json.status != null) {
      builder.status(json.status);
    }
    if (json.result != null) {
      builder.result(json.result);
    }
    if (json.message != null) {
      builder.message(json.message);
    }
    return builder.build();
  }

  /**
   * Creates an immutable copy of a {@link DdlStatusResult} value. Uses accessors to get values to
   * initialize the new immutable instance. If an instance is already immutable, it is returned as
   * is.
   *
   * @param <Result> generic parameter Result
   * @param instance The instance to copy
   * @return A copied immutable DdlResult instance
   */
  public static <Result> ImmutableDdlStatusResult<Result> copyOf(DdlStatusResult<Result> instance) {
    if (instance instanceof ImmutableDdlStatusResult<?>) {
      return (ImmutableDdlStatusResult<Result>) instance;
    }
    return ImmutableDdlStatusResult.<Result>builder().from(instance).build();
  }

  /**
   * Creates a builder for {@link ImmutableDdlStatusResult ImmutableDdlResult}.
   *
   * <pre>
   * ImmutableDdlResult.&amp;lt;Result&amp;gt;builder()
   *    .status(String) // required {@link DdlStatusResult#status() status}
   *    .result(Result) // required {@link DdlStatusResult#result() result}
   *    .message(@com.kakao.actionbase.core.java.annotation.Nullable String | null) // nullable {@link DdlStatusResult#message() message}
   *    .build();
   * </pre>
   *
   * @param <Result> generic parameter Result
   * @return A new ImmutableDdlResult builder
   */
  public static <Result> Builder<Result> builder() {
    return new Builder<>();
  }

  /**
   * @return The value of the {@code status} attribute
   */
  @JsonProperty("status")
  @Override
  public String status() {
    return status;
  }

  /**
   * @return The value of the {@code result} attribute
   */
  @JsonProperty("result")
  @Override
  public Result result() {
    return result;
  }

  /**
   * @return The value of the {@code message} attribute
   */
  @JsonProperty("message")
  @Override
  public @Nullable String message() {
    return message;
  }

  /**
   * Copy the current immutable object by setting a value for the {@link DdlStatusResult#status()
   * status} attribute. An equals check used to prevent copying of the same value by returning
   * {@code this}.
   *
   * @param value A new value for status
   * @return A modified copy of the {@code this} object
   */
  public final ImmutableDdlStatusResult<Result> withStatus(String value) {
    String newValue = Objects.requireNonNull(value, "status");
    if (this.status.equals(newValue)) return this;
    return new ImmutableDdlStatusResult<>(newValue, this.result, this.message);
  }

  /**
   * Copy the current immutable object by setting a value for the {@link DdlStatusResult#result()
   * result} attribute. A shallow reference equality check is used to prevent copying of the same
   * value by returning {@code this}.
   *
   * @param value A new value for result
   * @return A modified copy of the {@code this} object
   */
  public final ImmutableDdlStatusResult<Result> withResult(Result value) {
    if (this.result == value) return this;
    Result newValue = value;
    return new ImmutableDdlStatusResult<>(this.status, newValue, this.message);
  }

  /**
   * Copy the current immutable object by setting a value for the {@link DdlStatusResult#message()
   * message} attribute. An equals check used to prevent copying of the same value by returning
   * {@code this}.
   *
   * @param value A new value for message (can be {@code null})
   * @return A modified copy of the {@code this} object
   */
  public final ImmutableDdlStatusResult<Result> withMessage(@Nullable String value) {
    if (Objects.equals(this.message, value)) return this;
    return new ImmutableDdlStatusResult<>(this.status, this.result, value);
  }

  /**
   * This instance is equal to all instances of {@code ImmutableDdlResult} that have equal attribute
   * values.
   *
   * @return {@code true} if {@code this} is equal to {@code another} instance
   */
  @Override
  public boolean equals(Object another) {
    if (this == another) return true;
    return another instanceof ImmutableDdlStatusResult<?>
        && equalTo(0, (ImmutableDdlStatusResult<?>) another);
  }

  private boolean equalTo(int synthetic, ImmutableDdlStatusResult<?> another) {
    return status.equals(another.status)
        && result.equals(another.result)
        && Objects.equals(message, another.message);
  }

  /**
   * Computes a hash code from attributes: {@code status}, {@code result}, {@code message}.
   *
   * @return hashCode value
   */
  @Override
  public int hashCode() {
    int h = 5381;
    h += (h << 5) + status.hashCode();
    h += (h << 5) + result.hashCode();
    h += (h << 5) + Objects.hashCode(message);
    return h;
  }

  /**
   * Prints the immutable value {@code DdlResult} with attribute values.
   *
   * @return A string representation of the value
   */
  @Override
  public String toString() {
    return "DdlResult{" + "status=" + status + ", result=" + result + ", message=" + message + "}";
  }

  /**
   * Utility type used to correctly read immutable object from JSON representation.
   *
   * @deprecated Do not use this type directly, it exists only for the <em>Jackson</em>-binding
   *     infrastructure
   */
  @Generated(from = "DdlResult", generator = "Immutables")
  @Deprecated
  @JsonDeserialize
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
  static final class Json<Result> implements DdlStatusResult<Result> {
    String status;
    Result result;
    @Nullable String message;

    @JsonProperty("status")
    public void setStatus(String status) {
      this.status = status;
    }

    @JsonProperty("result")
    public void setResult(@Nullable Result result) {
      this.result = result;
    }

    @JsonProperty("message")
    public void setMessage(@Nullable String message) {
      this.message = message;
    }

    @Override
    public String status() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Result result() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable String message() {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Builds instances of type {@link ImmutableDdlStatusResult ImmutableDdlResult}. Initialize
   * attributes and then invoke the {@link #build()} method to create an immutable instance.
   *
   * <p><em>{@code Builder} is not thread-safe and generally should not be stored in a field or
   * collection, but instead used immediately to create instances.</em>
   */
  @Generated(from = "DdlResult", generator = "Immutables")
  public static final class Builder<Result> {
    private static final long INIT_BIT_STATUS = 0x1L;
    private long initBits = 0x1L;

    private String status;
    private Result result;
    private @Nullable String message;

    private Builder() {}

    /**
     * Fill a builder with attribute values from the provided {@code DdlResult} instance. Regular
     * attribute values will be replaced with those from the given instance. Absent optional values
     * will not replace present values.
     *
     * @param instance The instance from which to copy values
     * @return {@code this} builder for use in a chained invocation
     */
    public final Builder<Result> from(DdlStatusResult<Result> instance) {
      Objects.requireNonNull(instance, "instance");
      status(instance.status());
      result(instance.result());
      @Nullable String messageValue = instance.message();
      if (messageValue != null) {
        message(messageValue);
      }
      return this;
    }

    /**
     * Initializes the value for the {@link DdlStatusResult#status() status} attribute.
     *
     * @param status The value for status
     * @return {@code this} builder for use in a chained invocation
     */
    @JsonProperty("status")
    public final Builder<Result> status(String status) {
      this.status = Objects.requireNonNull(status, "status");
      initBits &= ~INIT_BIT_STATUS;
      return this;
    }

    /**
     * Initializes the value for the {@link DdlStatusResult#result() result} attribute.
     *
     * @param result The value for result
     * @return {@code this} builder for use in a chained invocation
     */
    @JsonProperty("result")
    public final Builder<Result> result(Result result) {
      this.result = result;
      return this;
    }

    /**
     * Initializes the value for the {@link DdlStatusResult#message() message} attribute.
     *
     * @param message The value for message (can be {@code null})
     * @return {@code this} builder for use in a chained invocation
     */
    @JsonProperty("message")
    public final Builder<Result> message(@Nullable String message) {
      this.message = message;
      return this;
    }

    /**
     * Builds a new {@link ImmutableDdlStatusResult ImmutableDdlResult}.
     *
     * @return An immutable instance of DdlResult
     * @throws IllegalStateException if any required attributes are missing
     */
    public ImmutableDdlStatusResult<Result> build() {
      if (initBits != 0) {
        throw new IllegalStateException(formatRequiredAttributesMessage());
      }
      return new ImmutableDdlStatusResult<>(status, result, message);
    }

    private String formatRequiredAttributesMessage() {
      List<String> attributes = new ArrayList<>();
      if ((initBits & INIT_BIT_STATUS) != 0) attributes.add("status");
      return "Cannot build DdlResult, some of required attributes are not set " + attributes;
    }
  }
}
