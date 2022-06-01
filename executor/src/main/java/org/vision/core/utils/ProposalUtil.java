package org.vision.core.utils;

import org.apache.commons.lang3.math.NumberUtils;
import org.vision.common.utils.ForkController;
import org.vision.core.config.Parameter.ForkBlockVersionConsts;
import org.vision.core.config.Parameter.ForkBlockVersionEnum;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.store.DynamicPropertiesStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class ProposalUtil {

  protected static final long LONG_VALUE = 100_000_000_000_000_000L;
  protected static final String BAD_PARAM_ID = "Bad chain parameter id";
  private static final String LONG_VALUE_ERROR =
      "Bad chain parameter value, valid range is [0," + LONG_VALUE + "]";
  private static final String PRE_VALUE_NOT_ONE_ERROR = "This value[";
  private static final String VALUE_NOT_ONE_ERROR = "] is only allowed to be 1";
  private static final long MAX_SUPPLY = 100_000_000_000L;
  private static final String MAX_SUPPLY_ERROR
      = "Bad chain parameter value, valid range is [0, 100_000_000_000L]";

  public static void validator(DynamicPropertiesStore dynamicPropertiesStore,
                               ForkController forkController,
                               long code, long value)
      throws ContractValidateException {
    ProposalType proposalType = ProposalType.getEnum(code);
    switch (proposalType) {
      case MAINTENANCE_TIME_INTERVAL: {
        if (value < 3 * 27 * 1000 || value > 24 * 3600 * 1000) {
          throw new ContractValidateException(
              "Bad chain parameter value, valid range is [3 * 27 * 1000,24 * 3600 * 1000]");
        }
        return;
      }
      case ACCOUNT_UPGRADE_COST:
      case CREATE_ACCOUNT_FEE:
      case TRANSACTION_FEE:
      case ASSET_ISSUE_FEE:
      case WITNESS_PAY_PER_BLOCK:
      case WITNESS_STANDBY_ALLOWANCE:
      case CREATE_NEW_ACCOUNT_FEE_IN_SYSTEM_CONTRACT:
      case CREATE_NEW_ACCOUNT_PHOTON_RATE: {
        if (value < 0 || value > LONG_VALUE) {
          throw new ContractValidateException(LONG_VALUE_ERROR);
        }
        break;
      }
      case ALLOW_CREATION_OF_CONTRACTS: {
        if (value != 1) {
          throw new ContractValidateException(
              PRE_VALUE_NOT_ONE_ERROR + "ALLOW_CREATION_OF_CONTRACTS" + VALUE_NOT_ONE_ERROR);
        }
        break;
      }
      case REMOVE_THE_POWER_OF_THE_GR: {
        if (dynamicPropertiesStore.getRemoveThePowerOfTheGr() == -1) {
          throw new ContractValidateException(
              "This proposal has been executed before and is only allowed to be executed once");
        }

        if (value != 1) {
          throw new ContractValidateException(
              PRE_VALUE_NOT_ONE_ERROR + "REMOVE_THE_POWER_OF_THE_GR" + VALUE_NOT_ONE_ERROR);
        }
        break;
      }
      case ENTROPY_FEE:
      case EXCHANGE_CREATE_FEE:
        break;
      case MAX_CPU_TIME_OF_ONE_TX:
        if (value < 10 || value > 1000) {
          throw new ContractValidateException(
              "Bad chain parameter value, valid range is [10,1000]");
        }
        break;
      case ALLOW_UPDATE_ACCOUNT_NAME: {
        if (value != 1) {
          throw new ContractValidateException(
              PRE_VALUE_NOT_ONE_ERROR + "ALLOW_UPDATE_ACCOUNT_NAME" + VALUE_NOT_ONE_ERROR);
        }
        break;
      }
      case ALLOW_SAME_TOKEN_NAME: {
        if (value != 1) {
          throw new ContractValidateException(
              PRE_VALUE_NOT_ONE_ERROR + "ALLOW_SAME_TOKEN_NAME" + VALUE_NOT_ONE_ERROR);
        }
        break;
      }
      case ALLOW_DELEGATE_RESOURCE: {
        if (value != 1) {
          throw new ContractValidateException(
              PRE_VALUE_NOT_ONE_ERROR + "ALLOW_DELEGATE_RESOURCE" + VALUE_NOT_ONE_ERROR);
        }
        break;
      }
      case TOTAL_ENTROPY_LIMIT: { // deprecated
        if (!forkController.pass(ForkBlockVersionConsts.ENTROPY_LIMIT)) {
          throw new ContractValidateException(BAD_PARAM_ID);
        }
        if (value < 0 || value > LONG_VALUE) {
          throw new ContractValidateException(LONG_VALUE_ERROR);
        }
        break;
      }
      case ALLOW_VVM_TRANSFER_VRC10: {
        if (value != 1) {
          throw new ContractValidateException(
              PRE_VALUE_NOT_ONE_ERROR + "ALLOW_VVM_TRANSFER_VRC10" + VALUE_NOT_ONE_ERROR);
        }
        if (dynamicPropertiesStore.getAllowSameTokenName() == 0) {
          throw new ContractValidateException("[ALLOW_SAME_TOKEN_NAME] proposal must be approved "
              + "before [ALLOW_VVM_TRANSFER_VRC10] can be proposed");
        }
        break;
      }
      case TOTAL_CURRENT_ENTROPY_LIMIT: {
        if (value < 0 || value > LONG_VALUE) {
          throw new ContractValidateException(LONG_VALUE_ERROR);
        }
        break;
      }
      case ALLOW_MULTI_SIGN: {
        if (value != 1) {
          throw new ContractValidateException(
              PRE_VALUE_NOT_ONE_ERROR + "ALLOW_MULTI_SIGN" + VALUE_NOT_ONE_ERROR);
        }
        break;
      }
      case ALLOW_ADAPTIVE_ENTROPY: {
        if (value != 1) {
          throw new ContractValidateException(
              PRE_VALUE_NOT_ONE_ERROR + "ALLOW_ADAPTIVE_ENTROPY" + VALUE_NOT_ONE_ERROR);
        }
        break;
      }
      case UPDATE_ACCOUNT_PERMISSION_FEE: {
        if (value < 0 || value > MAX_SUPPLY) {
          throw new ContractValidateException(MAX_SUPPLY_ERROR);
        }
        break;
      }
      case MULTI_SIGN_FEE: {
        if (value < 0 || value > MAX_SUPPLY) {
          throw new ContractValidateException(MAX_SUPPLY_ERROR);
        }
        break;
      }
      case ALLOW_PROTO_FILTER_NUM: {
        if (value != 1 && value != 0) {
          throw new ContractValidateException(
              "This value[ALLOW_PROTO_FILTER_NUM] is only allowed to be 1 or 0");
        }
        break;
      }
      case ALLOW_ACCOUNT_STATE_ROOT: {
        if (value != 1 && value != 0) {
          throw new ContractValidateException(
              "This value[ALLOW_ACCOUNT_STATE_ROOT] is only allowed to be 1 or 0");
        }
        break;
      }
      case ALLOW_VVM_CONSTANTINOPLE: {
        if (value != 1) {
          throw new ContractValidateException(
              PRE_VALUE_NOT_ONE_ERROR + "ALLOW_VVM_CONSTANTINOPLE" + VALUE_NOT_ONE_ERROR);
        }
        if (dynamicPropertiesStore.getAllowVvmTransferVrc10() == 0) {
          throw new ContractValidateException(
              "[ALLOW_VVM_TRANSFER_VRC10] proposal must be approved "
                  + "before [ALLOW_VVM_CONSTANTINOPLE] can be proposed");
        }
        break;
      }
      case ALLOW_VVM_SOLIDITY_059: {
        if (value != 1) {
          throw new ContractValidateException(
              PRE_VALUE_NOT_ONE_ERROR + "ALLOW_VVM_SOLIDITY_059" + VALUE_NOT_ONE_ERROR);
        }
        if (dynamicPropertiesStore.getAllowCreationOfContracts() == 0) {
          throw new ContractValidateException(
              "[ALLOW_CREATION_OF_CONTRACTS] proposal must be approved "
                  + "before [ALLOW_VVM_SOLIDITY_059] can be proposed");
        }
        break;
      }
      case ADAPTIVE_RESOURCE_LIMIT_TARGET_RATIO: {
        if (value < 1 || value > 1_000) {
          throw new ContractValidateException(
              "Bad chain parameter value, valid range is [1,1_000]");
        }
        break;
      }
      case ADAPTIVE_RESOURCE_LIMIT_MULTIPLIER: {
        if (value < 1 || value > 10_000L) {
          throw new ContractValidateException(
              "Bad chain parameter value, valid range is [1,10_000]");
        }
        break;
      }
      case ALLOW_CHANGE_DELEGATION: {
        if (value != 1 && value != 0) {
          throw new ContractValidateException(
              "This value[ALLOW_CHANGE_DELEGATION] is only allowed to be 1 or 0");
        }
        break;
      }
      case WITNESS_123_PAY_PER_BLOCK: {
        if (value < 0 || value > LONG_VALUE) {
          throw new ContractValidateException(LONG_VALUE_ERROR);
        }
        break;
      }
      case FORBID_TRANSFER_TO_CONTRACT: {
        if (value != 1) {
          throw new ContractValidateException(
              "This value[FORBID_TRANSFER_TO_CONTRACT] is only allowed to be 1");
        }
        if (dynamicPropertiesStore.getAllowCreationOfContracts() == 0) {
          throw new ContractValidateException(
              "[ALLOW_CREATION_OF_CONTRACTS] proposal must be approved "
                  + "before [FORBID_TRANSFER_TO_CONTRACT] can be proposed");
        }
        break;
      }
      case ALLOW_PBFT: {
        if (value != 1) {
          throw new ContractValidateException(
              "This value[ALLOW_PBFT] is only allowed to be 1");
        }
        break;
      }
      case ALLOW_VVM_ISTANBUL: {
        if (value != 1) {
          throw new ContractValidateException(
              "This value[ALLOW_VVM_ISTANBUL] is only allowed to be 1");
        }
        break;
      }
      case ALLOW_SHIELDED_VRC20_TRANSACTION: {
        if (value != 1 && value != 0) {
          throw new ContractValidateException(
              "This value[ALLOW_SHIELDED_VRC20_TRANSACTION] is only allowed to be 1 or 0");
        }
        break;
      }
      case ALLOW_MARKET_TRANSACTION: {
        if (value != 1) {
          throw new ContractValidateException(
              "This value[ALLOW_MARKET_TRANSACTION] is only allowed to be 1");
        }
        break;
      }
      case MARKET_SELL_FEE: {
        if (!dynamicPropertiesStore.supportAllowMarketTransaction()) {
          throw new ContractValidateException(
              "Market Transaction is not activated, can not set Market Sell Fee");
        }
        if (value < 0 || value > 10_000_000_000L) {
          throw new ContractValidateException(
              "Bad MARKET_SELL_FEE parameter value, valid range is [0,10_000_000_000L]");
        }
        break;
      }
      case MARKET_CANCEL_FEE: {
        if (!dynamicPropertiesStore.supportAllowMarketTransaction()) {
          throw new ContractValidateException(
              "Market Transaction is not activated, can not set Market Cancel Fee");
        }
        if (value < 0 || value > 10_000_000_000L) {
          throw new ContractValidateException(
              "Bad MARKET_CANCEL_FEE parameter value, valid range is [0,10_000_000_000L]");
        }
        break;
      }
      case MAX_FEE_LIMIT: {
        if (!forkController.pass(ForkBlockVersionEnum.VERSION_1_0_0)) {
          throw new ContractValidateException("Bad chain parameter id [MAX_FEE_LIMIT]");
        }
        if (value < 0 || value > 10_000_000_000L) {
          throw new ContractValidateException(
                  "Bad MAX_FEE_LIMIT parameter value, valid range is [0,10_000_000_000L]");
        }
        break;
      }
      case ALLOW_TRANSACTION_FEE_POOL: {
        if (!forkController.pass(ForkBlockVersionEnum.VERSION_1_0_0)) {
          throw new ContractValidateException(
                  "Bad chain parameter id [ALLOW_TRANSACTION_FEE_POOL]");
        }
        if (value != 1 && value != 0) {
          throw new ContractValidateException(
                  "This value[ALLOW_TRANSACTION_FEE_POOL] is only allowed to be 1 or 0");
        }
        break;
      }
      case ALLOW_BLACKHOLE_OPTIMIZATION: {
        if (!forkController.pass(ForkBlockVersionEnum.VERSION_1_0_0)) {
          throw new ContractValidateException(
              "Bad chain parameter id [ALLOW_REMOVE_BLACKHOLE]");
        }
        if (value != 1 && value != 0) {
          throw new ContractValidateException(
              "This value[ALLOW_REMOVE_BLACKHOLE] is only allowed to be 1 or 0");
        }
        break;
      }
      case ECONOMY_CYCLE: {
        if (value < 1 || value > 500) {
          throw new ContractValidateException(
                  "Bad chain parameter value, ECONOMY_CYCLE's valid range is [1,500]");
        }
        break;
      }
      case SPREAD_MINT_PAY_PER_BLOCK: {
        if (value < 0 || value > 100_000_000L) {
          throw new ContractValidateException(
              "Bad SPREAD_MINT_PAY_PER_BLOCK parameter value, valid range is [0,100_000_000L]");
        }
        break;
      }
      case ALLOW_SPREAD_MINT_LEVEL_PROP: {
        if (value != 1 && value != 0) {
          throw new ContractValidateException(
                  "This value[ALLOW_SPREAD_MINT_LEVEL_PROP] is only allowed to be 1 or 0");
        }
        break;
      }
      case PLEDGE_RATE_THRESHOLD: {
        if (value < 0 || value > 100L) {
          throw new ContractValidateException(
                  "Bad PLEDGE_RATE_THRESHOLD parameter value, valid range is [0,100L]");
        }
        break;
      }
      case SPREAD_FREEZE_PERIOD_LIMIT: {
        if (value < 1 || value > 30L) {
          throw new ContractValidateException(
                  "Bad SPREAD_FREEZE_PERIOD_LIMIT parameter value, valid range is [1,30L]");
        }
        break;
      }
      case ALLOW_ETHEREUM_COMPATIBLE_TRANSACTION: {
        if (value != 1 && value != 0) {
          throw new ContractValidateException(
                  "This value[ALLOW_ETHEREUM_COMPATIBLE_TRANSACTION] is only allowed to be 1 or 0");
        }
        break;
      }
      case ALLOW_MODIFY_SPREAD_MINT_PARENT: {
        if (value != 1 && value != 0) {
          throw new ContractValidateException(
                  "This value[ALLOW_MODIFY_SPREAD_MINT_PARENT] is only allowed to be 1 or 0");
        }
        break;
      }
      case TOTAL_PHOTON_LIMIT:{
        if (value < 0 || value > 1_000_000_000_000L) {
          throw new ContractValidateException(
                  "Bad TOTAL_PHOTON_LIMIT parameter value, valid range is [0, 1_000_000_000_000L]");
        }
        break;
      }
      case SPECIAL_FREEZE_PERIOD_LIMIT: {
        if (dynamicPropertiesStore.getAllowVPFreezeStageWeight() == 1){
          throw new ContractValidateException("SPECIAL_FREEZE_PERIOD_LIMIT is deprecated");
        }

        if (value < 1 || value > 365L) {
          throw new ContractValidateException(
                  "Bad SPECIAL_FREEZE_PERIOD_LIMIT parameter value, valid range is [1,365L]");
        }
        break;
      }
      case FVGUARANTEE_FREEZE_PERIOD_LIMIT: {
        if (value < 1 || value > 365L) {
          throw new ContractValidateException(
                  "Bad FVGUARANTEE_FREEZE_PERIOD_LIMIT parameter value, valid range is [1,365L]");
        }
        break;
      }
      case ALLOW_UNFREEZE_SPREAD_OR_FVGUARANTEE_CLEAR_VOTE: {
        if (value != 1 && value != 0) {
          throw new ContractValidateException(
                  "This value[ALLOW_UNFREEZE_SPREAD_OR_FVGUARANTEE_CLEAR_VOTE] is only allowed to be 1 or 0");
        }
        break;
      }
      case ALLOW_WITHDRAW_TRANSACTION_INFO_SEPARATE_AMOUNT: {
        if (value != 1) {
          throw new ContractValidateException(
                  "This value[ALLOW_WITHDRAW_TRANSACTION_INFO_SEPARATE_AMOUNT] is only allowed to be 1");
        }
        break;
      }
      case ALLOW_SPREAD_MINT_PARTICIPATE_PLEDGE_RATE: {
        if (value != 1 && value != 0) {
          throw new ContractValidateException(
                  "This value[ALLOW_SPREAD_MINT_PARTICIPATE_PLEDGE_RATE] is only allowed to be 1 or 0");
        }
        break;
      }
      case SM_BURN_OPTIMIZATION: {
        if (value != 1 && value != 0) {
          throw new ContractValidateException(
              "This value[SM_BURN_OPTIMIZATION] is only allowed to be 1 or 0");
        }
        break;
      }
      case REFREEZE_CONSIDERATION_PERIOD: {
        if (value <= 0 || value > 30) {
          throw new ContractValidateException(
              "Bad REFREEZE_CONSIDERATION_PERIOD parameter value, valid range is [1,30L]");
        }
        break;
      }
      case ALLOW_VP_FREEZE_STAGE_WEIGHT: {
        if (value != 1 ) {
          throw new ContractValidateException(
                  "This value[ALLOW_VP_FREEZE_STAGE_WEIGHT] is only allowed to be 1");
        }
        break;
      }
      case SPREAD_REFREEZE_CONSIDERATION_PERIOD: {
        if (value <= 0 || value > 30) {
          throw new ContractValidateException(
              "Bad SPREAD_REFREEZE_CONSIDERATION_PERIOD parameter value, valid range is [1,30L]");
        }
        break;
      }
      default:
        break;
    }
  }

  public static void validatorString(DynamicPropertiesStore dynamicPropertiesStore,
                               ForkController forkController,
                               long code, String value)
          throws ContractValidateException {
    ProposalType proposalType = ProposalType.getEnum(code);
    switch (proposalType) {
      case SPREAD_MINT_LEVEL_PROP: {
        String[] levelProps = value.split(",");
        if(levelProps.length < 1 || levelProps.length > 10){
          throw new ContractValidateException("Bad SPREAD_MINT_LEVEL_PROP parameter value, valid level props length range is [1, 10]");
        }
        int[] props = new int[levelProps.length];
        int sumProps = 0;
        for(int i = 0; i < levelProps.length; i++)
        {
          String tmp = levelProps[i].trim();
          if(!NumberUtils.isNumber(tmp)){
            throw new ContractValidateException("Bad SPREAD_MINT_LEVEL_PROP parameter value, Level Prop must be a Number");
          }
          props[i] = Integer.parseInt(tmp);
          if (props[i] < 0 || props[i] > 100){
            break;
          }
          sumProps += props[i];
        }

        if (sumProps != 100){
          throw new ContractValidateException(
                  "Bad SPREAD_MINT_LEVEL_PROP parameter value, allowed like [80,10,8,2]");
        }
        break;
      }
      case INFLATION_RATE: {
        String[] inflationRates = value.split(",");
        if (inflationRates.length != 2) {
          throw new ContractValidateException(
                  "Bad INFLATION_RATE parameter value, only allowed two positive integers like [value1,value2]");
        }
        int[] rates = new int[inflationRates.length];
        for(int i = 0; i < inflationRates.length; i++)
        {
          String tmp = inflationRates[i].trim();
          if(!NumberUtils.isNumber(tmp)){
            throw new ContractValidateException(
                    "Bad INFLATION_RATE parameter value, rate must be a Number");
          }
          rates[i] = Integer.parseInt(tmp);
          if (rates[i] < 0){
            throw new ContractValidateException(
                    "Bad INFLATION_RATE parameter value, only allowed positive integer");
          }
        }
        if (rates[0] > rates[1]) {
          throw new ContractValidateException(
                  "Bad INFLATION_RATE parameter value, only allowed two positive integers " +
                          "like [value1,value2] and value1 < value2");
        }
        break;
      }
      case VP_FREEZE_STAGE_WEIGHT: {
        String[] stageWeights = value.split(";");
        int stageLen = 5;
        if (stageWeights.length != stageLen) {
          throw new ContractValidateException(
                  "Bad VP_FREEZE_STAGE_WEIGHT parameter value, only allowed five strings like [stage,duration,weight] x 5");
        }

        Integer[] stage = new Integer[stageLen];
        Integer[] duration = new Integer[stageLen];
        Integer[] weight = new Integer[stageLen];
        for (int j = 0; j < stageWeights.length; j++){
          String sw = stageWeights[j];
          String[] stageWeight = sw.split(",");
          if (stageWeight.length != 3) {
            throw new ContractValidateException(
                    "Bad VP_FREEZE_STAGE_WEIGHT parameter value, only allowed three positive integers like [stage,duration,weight]");
          }
          for (String s : stageWeight) {
            String tmp = s.trim();
            if (!NumberUtils.isNumber(tmp)) {
              throw new ContractValidateException(
                      "Bad VP_FREEZE_STAGE_WEIGHT parameter value, stage, duration and weight must be a Number");
            }
          }

          stage[j] = Integer.parseInt(stageWeight[0]);
          if (stage[j] != j + 1){
            throw new ContractValidateException("Bad VP_FREEZE_STAGE_WEIGHT parameter value, stage must be 1,2,3,4,5");
          }
          duration[j] = Integer.parseInt(stageWeight[1]);
          if (duration[j] <= 0) {
            throw new ContractValidateException("Bad VP_FREEZE_STAGE_WEIGHT parameter value, duration must be great than 0");
          }
          weight[j] = Integer.parseInt(stageWeight[2]);
        }

        for (int i = 0; i < duration.length-1; i++) {
          if(duration[i] >= duration[i+1]){
            throw new ContractValidateException(
                    "Bad VP_FREEZE_STAGE_WEIGHT parameter value, duration must be ordered by asc");
          }
        }
        for (int i = 0; i < weight.length-1; i++) {
          if(i==0 && weight[i] != 100){
            throw new ContractValidateException(
                    "Bad VP_FREEZE_STAGE_WEIGHT parameter value, first stage weight must be 100");
          }
          if(weight[i] >= weight[i+1]){
            throw new ContractValidateException(
                    "Bad VP_FREEZE_STAGE_WEIGHT parameter value, weight must be ordered by asc");
          }
        }
        if(weight[stageLen-1] >= 200){
          throw new ContractValidateException(
                  "Bad VP_FREEZE_STAGE_WEIGHT parameter value, last weight must be less than 200");
        }
      }
      default:
        break;
    }
  }

  public enum ProposalType {         // current value, value range
    MAINTENANCE_TIME_INTERVAL(0), // 6 Hours, [3 * 27, 24 * 3600] s
    ACCOUNT_UPGRADE_COST(1), // 9999 VS, [0, 100000000000] VS
    CREATE_ACCOUNT_FEE(2), // 0.1 VS, [0, 100000000000] VS
    TRANSACTION_FEE(3), // 10 Vdt/Byte, [0, 100000000000] VS
    ASSET_ISSUE_FEE(4), // 1024 VS, [0, 100000000000] VS
    WITNESS_PAY_PER_BLOCK(5), // 0.144 VS, [0, 100000000000] VS
    WITNESS_STANDBY_ALLOWANCE(6), // 115200 VS, [0, 100000000000] VS
    CREATE_NEW_ACCOUNT_FEE_IN_SYSTEM_CONTRACT(7), // 0 VS, [0, 100000000000] VS
    CREATE_NEW_ACCOUNT_PHOTON_RATE(8), // 1 Photon/Byte, [0, 100000000000000000] Photon/Byte
    ALLOW_CREATION_OF_CONTRACTS(9), // 1, {0, 1}
    REMOVE_THE_POWER_OF_THE_GR(10),  // 1, {0, 1}
    ENTROPY_FEE(11), // 10 Vdt, [0, 100000000000] VS
    EXCHANGE_CREATE_FEE(12), // 1024 VS, [0, 100000000000] VS
    MAX_CPU_TIME_OF_ONE_TX(13), // 50 ms, [0, 1000] ms
    ALLOW_UPDATE_ACCOUNT_NAME(14), // 0, {0, 1}
    ALLOW_SAME_TOKEN_NAME(15), // 1, {0, 1}
    ALLOW_DELEGATE_RESOURCE(16), // 1, {0, 1}
    TOTAL_ENTROPY_LIMIT(17), // 50,000,000,000, [0, 100000000000000000]
    ALLOW_VVM_TRANSFER_VRC10(18), // 1, {0, 1}
    TOTAL_CURRENT_ENTROPY_LIMIT(19), // 50,000,000,000, [0, 100000000000000000]
    ALLOW_MULTI_SIGN(20), // 1, {0, 1}
    ALLOW_ADAPTIVE_ENTROPY(21), // 1, {0, 1}
    UPDATE_ACCOUNT_PERMISSION_FEE(22), // 100 VS, [0, 100000] VS
    MULTI_SIGN_FEE(23), // 1 VS, [0, 100000] VS
    ALLOW_PROTO_FILTER_NUM(24), // 0, {0, 1}
    ALLOW_ACCOUNT_STATE_ROOT(25), // 1, {0, 1}
    ALLOW_VVM_CONSTANTINOPLE(26), // 1, {0, 1}
    ADAPTIVE_RESOURCE_LIMIT_MULTIPLIER(27), // 1000, [1, 10000]
    ALLOW_CHANGE_DELEGATION(28), // 1, {0, 1}
    WITNESS_123_PAY_PER_BLOCK(29), // 1.2 VS * frozenRate, [0, 100000000000] VS
    ALLOW_VVM_SOLIDITY_059(30), // 1, {0, 1}
    ADAPTIVE_RESOURCE_LIMIT_TARGET_RATIO(31), // 10, [1, 1000]
    FORBID_TRANSFER_TO_CONTRACT(32), // 1, {0, 1}
    ALLOW_SHIELDED_VRC20_TRANSACTION(33), // 1, 39
    ALLOW_PBFT(34),// 1,0
    ALLOW_VVM_ISTANBUL(35),//1, {0,1}
    ALLOW_MARKET_TRANSACTION(36), // {0, 1}
    MARKET_SELL_FEE(37), // 0 [0,10_000_000_000]
    MARKET_CANCEL_FEE(38), // 0 [0,10_000_000_000]
    MAX_FEE_LIMIT(39), // [0, 10_000_000_000]
    ALLOW_TRANSACTION_FEE_POOL(40), // 0, 1
    ALLOW_BLACKHOLE_OPTIMIZATION(41),// 0,1
    SPREAD_MINT_PAY_PER_BLOCK(42),// [0,100_000_000]
    ECONOMY_CYCLE(43), // [1,500]
    ALLOW_SPREAD_MINT_LEVEL_PROP(44),// 0,1
    SPREAD_MINT_LEVEL_PROP(45),// "80,10,8,2"
    INFLATION_RATE(46),//"689,2322"
    PLEDGE_RATE_THRESHOLD(47),// [0, 100L]
    SPREAD_FREEZE_PERIOD_LIMIT(48),// [0, 100L]
    ALLOW_ETHEREUM_COMPATIBLE_TRANSACTION(49),// 0,1
    ALLOW_MODIFY_SPREAD_MINT_PARENT(50),// 0,1
    TOTAL_PHOTON_LIMIT(51),// [0, 1_000_000_000_000L]
    SPECIAL_FREEZE_PERIOD_LIMIT(52),// [0, 365L]
    FVGUARANTEE_FREEZE_PERIOD_LIMIT(53),
    ALLOW_UNFREEZE_SPREAD_OR_FVGUARANTEE_CLEAR_VOTE(54),// 0,1
    ALLOW_WITHDRAW_TRANSACTION_INFO_SEPARATE_AMOUNT(55),// 0,1
    ALLOW_SPREAD_MINT_PARTICIPATE_PLEDGE_RATE(56),// 0,1
    SM_BURN_OPTIMIZATION(57),// 0,1
    ALLOW_VP_FREEZE_STAGE_WEIGHT(58), // 0,1
    VP_FREEZE_STAGE_WEIGHT(59), //1,35,100;2,60,110;3,180,120;4,360,130;5,720,150
    REFREEZE_CONSIDERATION_PERIOD(60),//[1,30]
    SPREAD_REFREEZE_CONSIDERATION_PERIOD(61);

    private long code;

    ProposalType(long code) {
      this.code = code;
    }

    public static boolean contain(long code) {
      for (ProposalType parameters : values()) {
        if (parameters.code == code) {
          return true;
        }
      }
      return false;
    }

    public static ProposalType getEnum(long code) throws ContractValidateException {
      for (ProposalType parameters : values()) {
        if (parameters.code == code) {
          return parameters;
        }
      }
      throw new ContractValidateException("Does not support code : " + code);
    }

    public static ProposalType getEnumOrNull(long code) {
      for (ProposalType parameters : values()) {
        if (parameters.code == code) {
          return parameters;
        }
      }
      return null;
    }

    public long getCode() {
      return code;
    }
  }
}
