package com.kakao.actionbase.core.java.exception;

/**
 * Base class for all exceptions in the Actionbase project. This class is at the top of the
 * exception hierarchy within the project, and all custom exceptions must extend this class.
 */
public class ActionbaseUnsupportedTypeException extends ActionbaseException {

  private static final long serialVersionUID = 1L;

  private final String errorCode;
  private final String databaseContext;

  /** Default constructor */
  public ActionbaseUnsupportedTypeException() {
    this("An unknown error occurred.", "ERR_UNKNOWN", "");
  }

  /**
   * Creates an exception with a message.
   *
   * @param message Exception message
   */
  public ActionbaseUnsupportedTypeException(String message) {
    this(message, "ERR_UNKNOWN", "");
  }

  /**
   * Creates an exception with a message and cause exception.
   *
   * @param message Exception message
   * @param cause Cause exception
   */
  public ActionbaseUnsupportedTypeException(String message, Throwable cause) {
    this(message, "ERR_UNKNOWN", "", cause);
  }

  /**
   * Creates an exception with a message and error code.
   *
   * @param message Exception message
   * @param errorCode Error code
   */
  public ActionbaseUnsupportedTypeException(String message, String errorCode) {
    this(message, errorCode, "");
  }

  /**
   * Creates an exception with a message, error code, and service context.
   *
   * @param message Exception message
   * @param errorCode Error code
   * @param databaseContext Service context information
   */
  public ActionbaseUnsupportedTypeException(
      String message, String errorCode, String databaseContext) {
    super(message);
    this.errorCode = errorCode;
    this.databaseContext = databaseContext;
  }

  /**
   * Creates an exception with a message, error code, service context, and cause exception.
   *
   * @param message Exception message
   * @param errorCode Error code
   * @param databaseContext Service context information
   * @param cause Cause exception
   */
  public ActionbaseUnsupportedTypeException(
      String message, String errorCode, String databaseContext, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
    this.databaseContext = databaseContext;
  }

  /**
   * Returns the error code.
   *
   * @return Error code
   */
  public String getErrorCode() {
    return errorCode;
  }

  /**
   * Returns the service context information.
   *
   * @return Service context
   */
  public String getDatabaseContext() {
    return databaseContext;
  }

  @Override
  public String toString() {
    return String.format(
        "%s[errorCode=%s, databaseContext=%s]: %s",
        getClass().getSimpleName(), errorCode, databaseContext, getMessage());
  }
}
