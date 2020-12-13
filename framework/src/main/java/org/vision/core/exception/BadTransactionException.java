package org.vision.core.exception;

public class BadTransactionException extends VisionException {

  public BadTransactionException() {
    super();
  }

  public BadTransactionException(String message) {
    super(message);
  }

  public BadTransactionException(String message, Throwable cause) {
    super(message, cause);
  }
}
