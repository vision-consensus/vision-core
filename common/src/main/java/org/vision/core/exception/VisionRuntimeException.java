package org.vision.core.exception;

public class VisionRuntimeException extends RuntimeException {

  public VisionRuntimeException() {
    super();
  }

  public VisionRuntimeException(String message) {
    super(message);
  }

  public VisionRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  public VisionRuntimeException(Throwable cause) {
    super(cause);
  }

  protected VisionRuntimeException(String message, Throwable cause,
                                   boolean enableSuppression,
                                   boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }


}
