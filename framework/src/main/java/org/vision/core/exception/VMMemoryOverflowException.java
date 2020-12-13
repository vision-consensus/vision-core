package org.vision.core.exception;

public class VMMemoryOverflowException extends VisionException {

  public VMMemoryOverflowException() {
    super("VM memory overflow");
  }

  public VMMemoryOverflowException(String message) {
    super(message);
  }

}
