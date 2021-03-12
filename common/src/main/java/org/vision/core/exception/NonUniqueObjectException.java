package org.vision.core.exception;

public class NonUniqueObjectException extends VisionException {

  public NonUniqueObjectException() {
    super();
  }

  public NonUniqueObjectException(String s) {
    super(s);
  }

  public NonUniqueObjectException(String message, Throwable cause) {
    super(message, cause);
  }

  public NonUniqueObjectException(Throwable cause) {
    super("", cause);
  }
}
