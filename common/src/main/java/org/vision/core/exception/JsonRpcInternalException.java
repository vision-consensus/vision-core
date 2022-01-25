package org.vision.core.exception;

public class JsonRpcInternalException extends VisionException {

  public JsonRpcInternalException() {
    super();
  }

  public JsonRpcInternalException(String message) {
    super(message);
  }

  public JsonRpcInternalException(String message, Throwable cause) {
    super(message, cause);
  }
}