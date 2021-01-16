package org.vision.core.vm;

public class VMConstant {

  public static final int CONTRACT_NAME_LENGTH = 32;
  public static final int MIN_TOKEN_ID = 1_000_000;
  // Numbers
  public static final int ONE_HUNDRED = 100;
  public static final int ONE_THOUSAND = 1000;
  public static final long VDT_PER_ENTROPY = 100; // 1 us = 100 VDT = 100 * 10^-6 VS
  public static final long ENTROPY_LIMIT_IN_CONSTANT_TX = 3_000_000L; // ref: 1 us = 1 entropy


  private VMConstant() {
  }
}
