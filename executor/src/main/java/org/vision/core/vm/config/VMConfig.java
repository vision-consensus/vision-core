/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */
package org.vision.core.vm.config;


import static org.vision.common.parameter.CommonParameter.ENERGY_LIMIT_HARD_FORK;

import lombok.Setter;

/**
 * For developer only
 */
public class VMConfig {

  public static final int MAX_FEE_LIMIT = 1_000_000_000; //1000 VS

  private static boolean vmTraceCompressed = false;

  @Setter
  private static boolean vmTrace = false;

  private static boolean ALLOW_VVM_TRANSFER_VRC10 = true;

  private static boolean ALLOW_VVM_CONSTANTINOPLE = false;

  private static boolean ALLOW_MULTI_SIGN = false;

  private static boolean ALLOW_VVM_SOLIDITY_059 = false;

  private static boolean ALLOW_SHIELDED_VRC20_TRANSACTION = false;

  private static boolean ALLOW_VVM_ISTANBUL = false;

  private static boolean ALLOW_VVM_STAKE = false;

  private static boolean ALLOW_VVM_ASSET_ISSUE = false;

  private VMConfig() {
  }

  public static VMConfig getInstance() {
    return SystemPropertiesInstance.INSTANCE;
  }

  public static boolean vmTrace() {
    return vmTrace;
  }

  public static boolean vmTraceCompressed() {
    return vmTraceCompressed;
  }

  public static void initVmHardFork(boolean pass) {
    ENERGY_LIMIT_HARD_FORK = pass;
  }

  public static void initAllowMultiSign(long allow) {
    ALLOW_MULTI_SIGN = allow == 1;
  }

  public static void initAllowVvmTransferVrc10(long allow) {
    ALLOW_VVM_TRANSFER_VRC10 = allow == 1;
  }

  public static void initAllowVvmConstantinople(long allow) {
    ALLOW_VVM_CONSTANTINOPLE = allow == 1;
  }

  public static void initAllowVvmSolidity059(long allow) {
    ALLOW_VVM_SOLIDITY_059 = allow == 1;
  }

  public static void initAllowShieldedVRC20Transaction(long allow) {
    ALLOW_SHIELDED_VRC20_TRANSACTION = allow == 1;
  }

  public static void initAllowVvmIstanbul(long allow) {
    ALLOW_VVM_ISTANBUL = allow == 1;
  }

  public static void initAllowVvmStake(long allow) {
    ALLOW_VVM_STAKE = allow == 1;
  }

  public static void initAllowVvmAssetIssue(long allow) {
    ALLOW_VVM_ASSET_ISSUE = allow == 1;
  }

  public static boolean getEnergyLimitHardFork() {
    return ENERGY_LIMIT_HARD_FORK;
  }

  public static boolean allowVvmTransferVrc10() {
    return ALLOW_VVM_TRANSFER_VRC10;
  }

  public static boolean allowVvmConstantinople() {
    return ALLOW_VVM_CONSTANTINOPLE;
  }

  public static boolean allowMultiSign() {
    return ALLOW_MULTI_SIGN;
  }

  public static boolean allowVvmSolidity059() {
    return ALLOW_VVM_SOLIDITY_059;
  }

  public static boolean allowShieldedVRC20Transaction() {
    return ALLOW_SHIELDED_VRC20_TRANSACTION;
  }

  public static boolean allowVvmIstanbul() {return ALLOW_VVM_ISTANBUL; }

  public static boolean allowVvmStake() {
    return ALLOW_VVM_STAKE;
  }

  public static boolean allowVvmAssetIssue() {
    return ALLOW_VVM_ASSET_ISSUE;
  }

  private static class SystemPropertiesInstance {

    private static final VMConfig INSTANCE = new VMConfig();
  }
}
