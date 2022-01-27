package org.vision.core.exception;

public class JsonRpcTooManyResultException extends VisionException {

  public JsonRpcTooManyResultException() {
    super();
  }

  public JsonRpcTooManyResultException(String message) {
    super(message);
  }

  public JsonRpcTooManyResultException(String message, Throwable cause) {
    super(message, cause);
  }
}