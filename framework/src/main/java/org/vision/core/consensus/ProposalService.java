package org.vision.core.consensus;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.vision.common.utils.ByteArray;
import org.vision.core.capsule.FreezeAccountCapsule;
import org.vision.core.capsule.ProposalCapsule;
import org.vision.core.db.Manager;
import org.vision.core.services.http.Util;
import org.vision.core.store.FreezeAccountStore;
import org.vision.core.utils.ProposalUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Notice:
 * <p>
 * if you want to add a proposal,you just should add a enum ProposalType and add the valid in the
 * validator method, add the process in the process method
 */
@Slf4j
public class ProposalService extends ProposalUtil {

  public static boolean process(Manager manager, ProposalCapsule proposalCapsule) {
    Map<Long, Long> map = proposalCapsule.getInstance().getParametersMap();
    Map<Long, String> mapString = proposalCapsule.getInstance().getStringParametersMap();
    boolean find = true;
    for (Map.Entry<Long, Long> entry : map.entrySet()) {
      ProposalType proposalType = ProposalType.getEnumOrNull(entry.getKey());
      if (proposalType == null) {
        find = false;
        continue;
      }
      switch (proposalType) {
        case MAINTENANCE_TIME_INTERVAL: {
          manager.getDynamicPropertiesStore().saveMaintenanceTimeInterval(entry.getValue());
          break;
        }
        case ACCOUNT_UPGRADE_COST: {
          manager.getDynamicPropertiesStore().saveAccountUpgradeCost(entry.getValue());
          break;
        }
        case CREATE_ACCOUNT_FEE: {
          manager.getDynamicPropertiesStore().saveCreateAccountFee(entry.getValue());
          break;
        }
        case TRANSACTION_FEE: {
          manager.getDynamicPropertiesStore().saveTransactionFee(entry.getValue());
          break;
        }
        case ASSET_ISSUE_FEE: {
          manager.getDynamicPropertiesStore().saveAssetIssueFee(entry.getValue());
          break;
        }
        case WITNESS_PAY_PER_BLOCK: {
          manager.getDynamicPropertiesStore().saveWitnessPayPerBlock(entry.getValue());
          break;
        }
        case WITNESS_STANDBY_ALLOWANCE: {
          manager.getDynamicPropertiesStore().saveWitnessStandbyAllowance(entry.getValue());
          break;
        }
        case CREATE_NEW_ACCOUNT_FEE_IN_SYSTEM_CONTRACT: {
          manager.getDynamicPropertiesStore()
              .saveCreateNewAccountFeeInSystemContract(entry.getValue());
          break;
        }
        case CREATE_NEW_ACCOUNT_PHOTON_RATE: {
          manager.getDynamicPropertiesStore().saveCreateNewAccountPhotonRate(entry.getValue());
          break;
        }
        case ALLOW_CREATION_OF_CONTRACTS: {
          manager.getDynamicPropertiesStore().saveAllowCreationOfContracts(entry.getValue());
          break;
        }
        case REMOVE_THE_POWER_OF_THE_GR: {
          if (manager.getDynamicPropertiesStore().getRemoveThePowerOfTheGr() == 0) {
            manager.getDynamicPropertiesStore().saveRemoveThePowerOfTheGr(entry.getValue());
          }
          break;
        }
        case ENTROPY_FEE: {
          manager.getDynamicPropertiesStore().saveEntropyFee(entry.getValue());
          break;
        }
        case EXCHANGE_CREATE_FEE: {
          manager.getDynamicPropertiesStore().saveExchangeCreateFee(entry.getValue());
          break;
        }
        case MAX_CPU_TIME_OF_ONE_TX: {
          manager.getDynamicPropertiesStore().saveMaxCpuTimeOfOneTx(entry.getValue());
          break;
        }
        case ALLOW_UPDATE_ACCOUNT_NAME: {
          manager.getDynamicPropertiesStore().saveAllowUpdateAccountName(entry.getValue());
          break;
        }
        case ALLOW_SAME_TOKEN_NAME: {
          manager.getDynamicPropertiesStore().saveAllowSameTokenName(entry.getValue());
          break;
        }
        case ALLOW_DELEGATE_RESOURCE: {
          manager.getDynamicPropertiesStore().saveAllowDelegateResource(entry.getValue());
          break;
        }
        case TOTAL_ENTROPY_LIMIT: {
          manager.getDynamicPropertiesStore().saveTotalEntropyLimit(entry.getValue());
          break;
        }
        case ALLOW_VVM_TRANSFER_VRC10: {
          manager.getDynamicPropertiesStore().saveAllowVvmTransferVrc10(entry.getValue());
          break;
        }
        case TOTAL_CURRENT_ENTROPY_LIMIT: {
          manager.getDynamicPropertiesStore().saveTotalEntropyLimit2(entry.getValue());
          break;
        }
        case ALLOW_MULTI_SIGN: {
          if (manager.getDynamicPropertiesStore().getAllowMultiSign() == 0) {
            manager.getDynamicPropertiesStore().saveAllowMultiSign(entry.getValue());
          }
          break;
        }
        case ALLOW_ADAPTIVE_ENTROPY: {
          if (manager.getDynamicPropertiesStore().getAllowAdaptiveEntropy() == 0) {
            manager.getDynamicPropertiesStore().saveAllowAdaptiveEntropy(entry.getValue());

            //24 * 60 * 2 . one minute,1/2 total limit.
            manager.getDynamicPropertiesStore().saveAdaptiveResourceLimitTargetRatio(2880);
            manager.getDynamicPropertiesStore().saveTotalEntropyTargetLimit(
                manager.getDynamicPropertiesStore().getTotalEntropyLimit() / 2880);
            manager.getDynamicPropertiesStore().saveAdaptiveResourceLimitMultiplier(50);
          }
          break;
        }
        case UPDATE_ACCOUNT_PERMISSION_FEE: {
          manager.getDynamicPropertiesStore().saveUpdateAccountPermissionFee(entry.getValue());
          break;
        }
        case MULTI_SIGN_FEE: {
          manager.getDynamicPropertiesStore().saveMultiSignFee(entry.getValue());
          break;
        }
        case ALLOW_PROTO_FILTER_NUM: {
          manager.getDynamicPropertiesStore().saveAllowProtoFilterNum(entry.getValue());
          break;
        }
        case ALLOW_ACCOUNT_STATE_ROOT: {
          manager.getDynamicPropertiesStore().saveAllowAccountStateRoot(entry.getValue());
          break;
        }
        case ALLOW_VVM_CONSTANTINOPLE: {
          manager.getDynamicPropertiesStore().saveAllowVvmConstantinople(entry.getValue());
          manager.getDynamicPropertiesStore().addSystemContractAndSetPermission(48);
          break;
        }
        case ALLOW_VVM_SOLIDITY_059: {
          manager.getDynamicPropertiesStore().saveAllowVvmSolidity059(entry.getValue());
          break;
        }
        case ADAPTIVE_RESOURCE_LIMIT_TARGET_RATIO: {
          long ratio = 24 * 60 * entry.getValue();
          manager.getDynamicPropertiesStore().saveAdaptiveResourceLimitTargetRatio(ratio);
          manager.getDynamicPropertiesStore().saveTotalEntropyTargetLimit(
              manager.getDynamicPropertiesStore().getTotalEntropyLimit() / ratio);
          break;
        }
        case ADAPTIVE_RESOURCE_LIMIT_MULTIPLIER: {
          manager.getDynamicPropertiesStore().saveAdaptiveResourceLimitMultiplier(entry.getValue());
          break;
        }
        case ALLOW_CHANGE_DELEGATION: {
          manager.getDynamicPropertiesStore().saveChangeDelegation(entry.getValue());
          manager.getDynamicPropertiesStore().addSystemContractAndSetPermission(49);
          break;
        }
        case WITNESS_123_PAY_PER_BLOCK: {
          manager.getDynamicPropertiesStore().saveWitness123PayPerBlock(entry.getValue());
          break;
        }
        case FORBID_TRANSFER_TO_CONTRACT: {
          manager.getDynamicPropertiesStore().saveForbidTransferToContract(entry.getValue());
          break;
        }
        case ALLOW_PBFT: {
          manager.getDynamicPropertiesStore().saveAllowPBFT(entry.getValue());
          break;
        }
        case ALLOW_VVM_ISTANBUL: {
          manager.getDynamicPropertiesStore().saveAllowVvmIstanbul(entry.getValue());
          break;
        }
        case ALLOW_SHIELDED_VRC20_TRANSACTION: {
          manager.getDynamicPropertiesStore().saveAllowShieldedVRC20Transaction(entry.getValue());
          break;
        }
        case ALLOW_MARKET_TRANSACTION: {
          if (manager.getDynamicPropertiesStore().getAllowMarketTransaction() == 0) {
            manager.getDynamicPropertiesStore().saveAllowMarketTransaction(entry.getValue());
            manager.getDynamicPropertiesStore().addSystemContractAndSetPermission(52);
            manager.getDynamicPropertiesStore().addSystemContractAndSetPermission(53);
          }
          break;
        }
        case MARKET_SELL_FEE: {
          manager.getDynamicPropertiesStore().saveMarketSellFee(entry.getValue());
          break;
        }
        case MARKET_CANCEL_FEE: {
          manager.getDynamicPropertiesStore().saveMarketCancelFee(entry.getValue());
          break;
        }
        case MAX_FEE_LIMIT: {
          manager.getDynamicPropertiesStore().saveMaxFeeLimit(entry.getValue());
          break;
        }
        case ALLOW_TRANSACTION_FEE_POOL: {
          manager.getDynamicPropertiesStore().saveAllowTransactionFeePool(entry.getValue());
          break;
        }
        case ALLOW_BLACKHOLE_OPTIMIZATION: {
          manager.getDynamicPropertiesStore().saveAllowBlackHoleOptimization(entry.getValue());
          break;
        }
        case ECONOMY_CYCLE: {
          manager.getDynamicPropertiesStore().saveEconomyCycle(entry.getValue());
          break;
        }
        case SPREAD_MINT_PAY_PER_BLOCK: {
          manager.getDynamicPropertiesStore().saveSpreadMintPayPerBlock(entry.getValue());
          break;
        }
        case ALLOW_SPREAD_MINT_LEVEL_PROP: {
          manager.getDynamicPropertiesStore().saveAllowSpreadMintLevelProp(entry.getValue());
          break;
        }
        case PLEDGE_RATE_THRESHOLD: {
          manager.getDynamicPropertiesStore().savePledgeRateThreshold(entry.getValue());
          break;
        }
        case SPREAD_FREEZE_PERIOD_LIMIT: {
          manager.getDynamicPropertiesStore().saveSpreadFreezePeriodLimit(entry.getValue());
          break;
        }
        case ALLOW_ETHEREUM_COMPATIBLE_TRANSACTION: {
          manager.getDynamicPropertiesStore().saveAllowEthereumCompatibleTransaction(entry.getValue());
          break;
        }
        case ALLOW_MODIFY_SPREAD_MINT_PARENT: {
          manager.getDynamicPropertiesStore().saveAllowModifySpreadMintParent(entry.getValue());
          break;
        }
        case TOTAL_PHOTON_LIMIT: {
          manager.getDynamicPropertiesStore().saveTotalPhotonLimit(entry.getValue());
          break;
        }
        case SPECIAL_FREEZE_PERIOD_LIMIT: {
          manager.getDynamicPropertiesStore().saveSpecialFreezePeriodLimit(entry.getValue());
          break;
        }
        case FVGUARANTEE_FREEZE_PERIOD_LIMIT: {
          manager.getDynamicPropertiesStore().saveFvGuaranteeFreezePeriodLimit(entry.getValue());
          break;
        }
        case ALLOW_UNFREEZE_SPREAD_OR_FVGUARANTEE_CLEAR_VOTE: {
          manager.getDynamicPropertiesStore().saveAllowUnfreezeSpreadOrFvGuaranteeClearVote(entry.getValue());
          break;
        }
        case ALLOW_WITHDRAW_TRANSACTION_INFO_SEPARATE_AMOUNT: {
          manager.getDynamicPropertiesStore().saveAllowWithdrawTransactionInfoSeparateAmount(entry.getValue());
          break;
        }
        case ALLOW_SPREAD_MINT_PARTICIPATE_PLEDGE_RATE: {
          manager.getDynamicPropertiesStore().saveAllowSpreadMintParticipatePledgeRate(entry.getValue());
          break;
        }
        case SM_BURN_OPTIMIZATION: {
          manager.getDynamicPropertiesStore().saveSMBurnOptimization(entry.getValue());
          break;
        }
        case ALLOW_VP_FREEZE_STAGE_WEIGHT: {
          manager.getDynamicPropertiesStore().saveAllowVPFreezeStageWeight(entry.getValue());
          break;
        }
        case REFREEZE_CONSIDERATION_PERIOD: {
          manager.getDynamicPropertiesStore().saveRefreezeConsiderationPeriod(entry.getValue());
          break;
        }
        case SPREAD_REFREEZE_CONSIDERATION_PERIOD: {
          manager.getDynamicPropertiesStore().saveSpreadRefreezeConsiderationPeriod(entry.getValue());
          break;
        }
        case ALLOW_ETHEREUM_COMPATIBLE_TRANSACTION_NATIVE_STEP1: {
          manager.getDynamicPropertiesStore().saveAllowEthereumCompatibleTransactionNativeStep1(entry.getValue());
          break;
        }
        case MODIFY_SPREAD_MINT_PARENT_FEE: {
          manager.getDynamicPropertiesStore().saveModifySpreadMintParentFee(entry.getValue());
          break;
        }
        case SEPARATE_PROPOSAL_STRING_PARAMETERS: {
          manager.getDynamicPropertiesStore().saveSeparateProposalStringParameters(entry.getValue());
          break;
        }
        case ALLOW_FREEZE_ACCOUNT: {
          manager.getDynamicPropertiesStore().saveAllowFreezeAccount(entry.getValue());
          break;
        }
        case ALLOW_UNFREEZE_FRAGMENTATION: {
          manager.getDynamicPropertiesStore().saveAllowUnfreezeFragmentation(entry.getValue());
          break;
        }
        case ALLOW_OPTIMIZED_RETURN_VALUE_OF_CHAIN_ID: {
          manager.getDynamicPropertiesStore().saveAllowOptimizedReturnValueOfChainId(entry.getValue());
          break;
        }
        default:
          find = false;
          break;
      }
    }

    for (Map.Entry<Long, String> entry : mapString.entrySet()) {
      ProposalType proposalType = ProposalType.getEnumOrNull(entry.getKey());
      find = true;
      if (proposalType == null) {
        find = false;
        continue;
      }
      switch (proposalType) {
        case SPREAD_MINT_LEVEL_PROP: {
          manager.getDynamicPropertiesStore().saveSpreadMintLevelProp(entry.getValue());
          break;
        }
        case INFLATION_RATE: {
          long lowInflationRate = Long.parseLong(entry.getValue().split(",")[0]);
          long highInflationRate = Long.parseLong(entry.getValue().split(",")[1]);
          manager.getDynamicPropertiesStore().saveLowInflationRate(lowInflationRate);
          manager.getDynamicPropertiesStore().saveHighInflationRate(highInflationRate);
          break;
        }
        case VP_FREEZE_STAGE_WEIGHT: {
          manager.getDynamicPropertiesStore().saveVPFreezeStageWeight(entry.getValue());
          break;
        }
        case FREEZE_ACCOUNT_OWNER: {
          String owner_account = entry.getValue();
          if (owner_account.length() == 34) {
            owner_account = Util.getHexAddress(owner_account);
          }
          manager.getDynamicPropertiesStore().saveFreezeAccountOwner(owner_account);
          break;
        }
        case FREEZE_ACCOUNT_LIST: {
          String[] values = entry.getValue().split(";");
          saveFreezeAccountList(values[0], values[1], manager);
          break;
        }
        default:
          find = false;
          break;
      }
    }

    return find;
  }

  public static void saveFreezeAccountList(String valueType, String accounts, Manager manager) {
    List<ByteString> valueList = new ArrayList<>();
    for (String account: accounts.split(",")) {
      String hexAddress = account.length() == 34 ? Util.getHexAddress(account): account;
      valueList.add(ByteString.copyFrom(ByteArray.fromHexString(hexAddress)));
    }

    FreezeAccountStore freezeAccountStore = manager.getFreezeAccountStore();
    FreezeAccountCapsule freezeAccountCapsule = freezeAccountStore.get(freezeAccountStore.createFreezeAccountDbKey());
    if (freezeAccountCapsule == null) {
      freezeAccountCapsule = new FreezeAccountCapsule(valueList);
      freezeAccountStore.put(freezeAccountCapsule.createFreezeAccountDbKey(), freezeAccountCapsule);
    }else {
      List<ByteString> oldFreezeAddresses = freezeAccountCapsule.getAddressesList();
      switch (valueType) {
        case "0":
          freezeAccountCapsule.setAllAddresses(valueList);
          freezeAccountStore.put(freezeAccountStore.createFreezeAccountDbKey(), freezeAccountCapsule);
          break;
        case "1":
          freezeAccountCapsule.addAllAddress(valueList);
          freezeAccountStore.put(freezeAccountStore.createFreezeAccountDbKey(), freezeAccountCapsule);
          break;
        case "2":
          List<ByteString> newFreezeAccountList = new ArrayList<>();
          for (ByteString address: oldFreezeAddresses) {
            if (valueList.contains(address)){
              continue;
            }
            newFreezeAccountList.add(address);
          }

          if (newFreezeAccountList.isEmpty()) {
            freezeAccountStore.delete(freezeAccountStore.createFreezeAccountDbKey());
          }else {
            freezeAccountCapsule.setAllAddresses(newFreezeAccountList);
            freezeAccountStore.put(freezeAccountStore.createFreezeAccountDbKey(), freezeAccountCapsule);
          }
          break;
        default:
          break;
      }
    }
  }

}
