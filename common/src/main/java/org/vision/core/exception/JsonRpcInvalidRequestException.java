package org.vision.core.exception;

public class JsonRpcInvalidRequestException extends VisionException {

  public JsonRpcInvalidRequestException() {
    super();
  }

  public JsonRpcInvalidRequestException(String message) {
    super(message);
  }

  public JsonRpcInvalidRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}