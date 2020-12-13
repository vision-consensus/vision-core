package org.vision.common.runtime;

import lombok.extern.slf4j.Slf4j;
import org.vision.core.capsule.ReceiptCapsule;

@Slf4j
public class VVMTestResult {

  private Runtime runtime;
  private ReceiptCapsule receipt;
  private byte[] contractAddress;

  public VVMTestResult(Runtime runtime, ReceiptCapsule receipt, byte[] contractAddress) {
    this.runtime = runtime;
    this.receipt = receipt;
    this.contractAddress = contractAddress;
  }

  public byte[] getContractAddress() {
    return contractAddress;
  }

  public VVMTestResult setContractAddress(byte[] contractAddress) {
    this.contractAddress = contractAddress;
    return this;
  }

  public Runtime getRuntime() {
    return runtime;
  }

  public VVMTestResult setRuntime(Runtime runtime) {
    this.runtime = runtime;
    return this;
  }

  public ReceiptCapsule getReceipt() {
    return receipt;
  }

  public VVMTestResult setReceipt(ReceiptCapsule receipt) {
    this.receipt = receipt;
    return this;
  }

}
