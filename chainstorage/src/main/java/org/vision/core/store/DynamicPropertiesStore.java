package org.vision.core.store;

import com.google.protobuf.ByteString;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Sha256Hash;
import org.vision.core.capsule.BytesCapsule;
import org.vision.core.config.Parameter;
import org.vision.core.config.Parameter.ChainConstant;
import org.vision.core.db.VisionStoreWithRevoking;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.vision.core.config.Parameter.ChainConstant.FIRST_ECONOMY_CYCLE;

@Slf4j(topic = "DB")
@Component
public class DynamicPropertiesStore extends VisionStoreWithRevoking<BytesCapsule> {

  private static final byte[] LATEST_BLOCK_HEADER_TIMESTAMP = "latest_block_header_timestamp"
      .getBytes();
  private static final byte[] LATEST_BLOCK_HEADER_NUMBER = "latest_block_header_number".getBytes();
  private static final byte[] LATEST_BLOCK_HEADER_HASH = "latest_block_header_hash".getBytes();
  private static final byte[] STATE_FLAG = "state_flag"
      .getBytes(); // 1 : is maintenance, 0 : is not maintenance
  private static final byte[] LATEST_SOLIDIFIED_BLOCK_NUM = "LATEST_SOLIDIFIED_BLOCK_NUM"
      .getBytes();

  private static final byte[] LATEST_PROPOSAL_NUM = "LATEST_PROPOSAL_NUM".getBytes();

  private static final byte[] LATEST_EXCHANGE_NUM = "LATEST_EXCHANGE_NUM".getBytes();

  private static final byte[] BLOCK_FILLED_SLOTS = "BLOCK_FILLED_SLOTS".getBytes();

  private static final byte[] BLOCK_FILLED_SLOTS_INDEX = "BLOCK_FILLED_SLOTS_INDEX".getBytes();

  private static final byte[] NEXT_MAINTENANCE_TIME = "NEXT_MAINTENANCE_TIME".getBytes();

  private static final byte[] MAX_FROZEN_TIME = "MAX_FROZEN_TIME".getBytes();

  private static final byte[] MIN_FROZEN_TIME = "MIN_FROZEN_TIME".getBytes();

  private static final byte[] MAX_FROZEN_SUPPLY_NUMBER = "MAX_FROZEN_SUPPLY_NUMBER".getBytes();

  private static final byte[] MAX_FROZEN_SUPPLY_TIME = "MAX_FROZEN_SUPPLY_TIME".getBytes();

  private static final byte[] MIN_FROZEN_SUPPLY_TIME = "MIN_FROZEN_SUPPLY_TIME".getBytes();

  private static final byte[] WITNESS_ALLOWANCE_FROZEN_TIME = "WITNESS_ALLOWANCE_FROZEN_TIME"
      .getBytes();

  private static final byte[] MAINTENANCE_TIME_INTERVAL = "MAINTENANCE_TIME_INTERVAL".getBytes();

  private static final byte[] ACCOUNT_UPGRADE_COST = "ACCOUNT_UPGRADE_COST".getBytes();

  private static final byte[] WITNESS_PAY_PER_BLOCK = "WITNESS_PAY_PER_BLOCK".getBytes();

  private static final byte[] WITNESS_123_PAY_PER_BLOCK = "WITNESS_123_PAY_PER_BLOCK".getBytes();

  private static final byte[] WITNESS_STANDBY_ALLOWANCE = "WITNESS_STANDBY_ALLOWANCE".getBytes();
  private static final byte[] ENTROPY_FEE = "ENTROPY_FEE".getBytes();
  private static final byte[] MAX_CPU_TIME_OF_ONE_TX = "MAX_CPU_TIME_OF_ONE_TX".getBytes();
  //abandon
  private static final byte[] CREATE_ACCOUNT_FEE = "CREATE_ACCOUNT_FEE".getBytes();
  private static final byte[] CREATE_NEW_ACCOUNT_FEE_IN_SYSTEM_CONTRACT
      = "CREATE_NEW_ACCOUNT_FEE_IN_SYSTEM_CONTRACT".getBytes();
  private static final byte[] CREATE_NEW_ACCOUNT_PHOTON_RATE =
      "CREATE_NEW_ACCOUNT_PHOTON_RATE"
          .getBytes();
  private static final byte[] TRANSACTION_FEE = "TRANSACTION_FEE".getBytes(); // 1 byte
  private static final byte[] ASSET_ISSUE_FEE = "ASSET_ISSUE_FEE".getBytes();
  private static final byte[] UPDATE_ACCOUNT_PERMISSION_FEE = "UPDATE_ACCOUNT_PERMISSION_FEE"
      .getBytes();
  private static final byte[] MULTI_SIGN_FEE = "MULTI_SIGN_FEE"
      .getBytes();
  private static final byte[] SHIELDED_TRANSACTION_FEE = "SHIELDED_TRANSACTION_FEE".getBytes();
  private static final byte[] SHIELDED_TRANSACTION_CREATE_ACCOUNT_FEE =
      "SHIELDED_TRANSACTION_CREATE_ACCOUNT_FEE".getBytes();
  //This value should be not negative
  private static final byte[] TOTAL_SHIELDED_POOL_VALUE = "TOTAL_SHIELDED_POOL_VALUE".getBytes();
  private static final byte[] EXCHANGE_CREATE_FEE = "EXCHANGE_CREATE_FEE".getBytes();
  private static final byte[] EXCHANGE_BALANCE_LIMIT = "EXCHANGE_BALANCE_LIMIT".getBytes();
  private static final byte[] TOTAL_TRANSACTION_COST = "TOTAL_TRANSACTION_COST".getBytes();
  private static final byte[] TOTAL_CREATE_ACCOUNT_COST = "TOTAL_CREATE_ACCOUNT_COST".getBytes();
  private static final byte[] TOTAL_CREATE_WITNESS_COST = "TOTAL_CREATE_WITNESS_FEE".getBytes();
  private static final byte[] TOTAL_STORAGE_POOL = "TOTAL_STORAGE_POOL".getBytes();
  private static final byte[] TOTAL_STORAGE_TAX = "TOTAL_STORAGE_TAX".getBytes();
  private static final byte[] TOTAL_STORAGE_RESERVED = "TOTAL_STORAGE_RESERVED".getBytes();
  private static final byte[] STORAGE_EXCHANGE_TAX_RATE = "STORAGE_EXCHANGE_TAX_RATE".getBytes();
  private static final String FORK_CONTROLLER = "FORK_CONTROLLER";
  private static final String FORK_PREFIX = "FORK_VERSION_";
  //This value is only allowed to be 0, 1, -1
  private static final byte[] REMOVE_THE_POWER_OF_THE_GR = "REMOVE_THE_POWER_OF_THE_GR".getBytes();
  //This value is only allowed to be 0, 1, -1
  private static final byte[] ALLOW_DELEGATE_RESOURCE = "ALLOW_DELEGATE_RESOURCE".getBytes();
  //This value is only allowed to be 0, 1, -1
  private static final byte[] ALLOW_ADAPTIVE_ENTROPY = "ALLOW_ADAPTIVE_ENTROPY".getBytes();
  //This value is only allowed to be 0, 1, -1
  private static final byte[] ALLOW_UPDATE_ACCOUNT_NAME = "ALLOW_UPDATE_ACCOUNT_NAME".getBytes();
  //This value is only allowed to be 0, 1, -1
  //Note: there is a space in this key name. This space must not be deleted.
  private static final byte[] ALLOW_SAME_TOKEN_NAME = " ALLOW_SAME_TOKEN_NAME".getBytes();
  //If the parameter is larger than 0, the contract is allowed to be created.
  private static final byte[] ALLOW_CREATION_OF_CONTRACTS = "ALLOW_CREATION_OF_CONTRACTS"
      .getBytes();
  //Used only for multi sign
  private static final byte[] TOTAL_SIGN_NUM = "TOTAL_SIGN_NUM".getBytes();
  //Used only for multi sign, once，value is {0,1}
  private static final byte[] ALLOW_MULTI_SIGN = "ALLOW_MULTI_SIGN".getBytes();
  //token id,Incremental，The initial value is 1000000
  @Getter
  private static final byte[] TOKEN_ID_NUM = "TOKEN_ID_NUM".getBytes();
  //Used only for token updates, once，value is {0,1}
  private static final byte[] TOKEN_UPDATE_DONE = "TOKEN_UPDATE_DONE".getBytes();
  //This value is only allowed to be 0, 1, -1
  private static final byte[] ALLOW_VVM_TRANSFER_VRC10 = "ALLOW_VVM_TRANSFER_VRC10".getBytes();
  //If the parameter is larger than 0, allow ZKsnark Transaction
  private static final byte[] ALLOW_SHIELDED_TRANSACTION = "ALLOW_SHIELDED_TRANSACTION".getBytes();
  private static final byte[] ALLOW_SHIELDED_VRC20_TRANSACTION =
      "ALLOW_SHIELDED_VRC20_TRANSACTION"
          .getBytes();
  private static final byte[] ALLOW_VVM_ISTANBUL = "ALLOW_VVM_ISTANBUL".getBytes();
  private static final byte[] ALLOW_VVM_STAKE = "ALLOW_VVM_STAKE".getBytes();
  private static final byte[] ALLOW_VVM_ASSET_ISSUE = "ALLOW_VVM_ASSET_ISSUE".getBytes();
  private static final byte[] ALLOW_VVM_CONSTANTINOPLE = "ALLOW_VVM_CONSTANTINOPLE".getBytes();
  private static final byte[] ALLOW_VVM_SOLIDITY_059 = "ALLOW_VVM_SOLIDITY_059".getBytes();
  private static final byte[] FORBID_TRANSFER_TO_CONTRACT = "FORBID_TRANSFER_TO_CONTRACT"
      .getBytes();
  //Used only for protobuf data filter , once，value is 0,1
  private static final byte[] ALLOW_PROTO_FILTER_NUM = "ALLOW_PROTO_FILTER_NUM"
      .getBytes();
  private static final byte[] AVAILABLE_CONTRACT_TYPE = "AVAILABLE_CONTRACT_TYPE".getBytes();
  private static final byte[] ACTIVE_DEFAULT_OPERATIONS = "ACTIVE_DEFAULT_OPERATIONS".getBytes();
  //Used only for account state root, once，value is {0,1} allow is 1
  private static final byte[] ALLOW_ACCOUNT_STATE_ROOT = "ALLOW_ACCOUNT_STATE_ROOT".getBytes();
  private static final byte[] CURRENT_CYCLE_NUMBER = "CURRENT_CYCLE_NUMBER".getBytes();
  private static final byte[] CHANGE_DELEGATION = "CHANGE_DELEGATION".getBytes();
  private static final byte[] ALLOW_PBFT = "ALLOW_PBFT".getBytes();
//  private static final byte[] CURRENT_CYCLE_TIMESTAMP = "CURRENT_CYCLE_TIMESTAMP".getBytes();  to be delete v4.1

  private static final byte[] ALLOW_MARKET_TRANSACTION = "ALLOW_MARKET_TRANSACTION".getBytes();
  private static final byte[] MARKET_SELL_FEE = "MARKET_SELL_FEE".getBytes();
  private static final byte[] MARKET_CANCEL_FEE = "MARKET_CANCEL_FEE".getBytes();
  private static final byte[] MARKET_QUANTITY_LIMIT = "MARKET_QUANTITY_LIMIT".getBytes();


  private static final byte[] ALLOW_TRANSACTION_FEE_POOL = "ALLOW_TRANSACTION_FEE_POOL".getBytes();
  private static final byte[] TRANSACTION_FEE_POOL = "TRANSACTION_FEE_POOL".getBytes();

  private static final byte[] MAX_FEE_LIMIT = "MAX_FEE_LIMIT".getBytes();
  private static final byte[] BURN_VS_AMOUNT = "BURN_VS_AMOUNT".getBytes();
  private static final byte[] ALLOW_BLACKHOLE_OPTIMIZATION = "ALLOW_BLACKHOLE_OPTIMIZATION".getBytes();

  private static final byte[] VOTE_FREEZE_STAGE_LEVEL1 = "VOTE_FREEZE_STAGE_LEVEL1".getBytes();
  private static final byte[] VOTE_FREEZE_STAGE_LEVEL2 = "VOTE_FREEZE_STAGE_LEVEL2".getBytes();
  private static final byte[] VOTE_FREEZE_STAGE_LEVEL3 = "VOTE_FREEZE_STAGE_LEVEL3".getBytes();
  private static final byte[] VOTE_FREEZE_PERCENT_LEVEL1 = "VOTE_FREEZE_PERCENT_LEVEL1".getBytes();
  private static final byte[] VOTE_FREEZE_PERCENT_LEVEL2 = "VOTE_FREEZE_PERCENT_LEVEL2".getBytes();
  private static final byte[] VOTE_FREEZE_PERCENT_LEVEL3 = "VOTE_FREEZE_PERCENT_LEVEL3".getBytes();

  private static final byte[] SR_FREEZE_LOWEST = "SR_FREEZE_LOWEST".getBytes();
  private static final byte[] SR_FREEZE_LOWEST_PERCENT = "SR_FREEZE_LOWEST_PERCENT".getBytes();

  private static final byte[] SPREAD_MINT_PAY_PER_BLOCK = "SPREAD_MINT_PAY_PER_BLOCK".getBytes();

  private static final byte[] ECONOMY_CYCLE = "ECONOMY_CYCLE".getBytes();
  private static final byte[] EFFECT_ECONOMY_CYCLE = "EFFECT_ECONOMY_CYCLE".getBytes();
  private static final byte[] SPREAD_MINT_LEVEL = "SPREAD_LEVEL".getBytes();
  private static final byte[] SPREAD_MINT_LEVEL_PROP = "SPREAD_LEVEL_PROP".getBytes();
  private static final byte[] ALLOW_SPREAD_MINT_LEVEL_PROP = "ALLOW_SPREAD_LEVEL_PROP".getBytes();

  @Autowired
  private DynamicPropertiesStore(@Value("properties") String dbName) {
    super(dbName);

    try {
      this.getTotalSignNum();
    } catch (IllegalArgumentException e) {
      this.saveTotalSignNum(5);
    }

    try {
      this.getAllowMultiSign();
    } catch (IllegalArgumentException e) {
      this.saveAllowMultiSign(CommonParameter.getInstance()
          .getAllowMultiSign());
    }

    try {
      this.getLatestBlockHeaderTimestamp();
    } catch (IllegalArgumentException e) {
      this.saveLatestBlockHeaderTimestamp(0);
    }

    try {
      this.getLatestBlockHeaderNumber();
    } catch (IllegalArgumentException e) {
      this.saveLatestBlockHeaderNumber(0);
    }

    try {
      this.getLatestBlockHeaderHash();
    } catch (IllegalArgumentException e) {
      this.saveLatestBlockHeaderHash(ByteString.copyFrom(ByteArray.fromHexString("00")));
    }

    try {
      this.getStateFlag();
    } catch (IllegalArgumentException e) {
      this.saveStateFlag(0);
    }

    try {
      this.getLatestSolidifiedBlockNum();
    } catch (IllegalArgumentException e) {
      this.saveLatestSolidifiedBlockNum(0);
    }

    try {
      this.getLatestProposalNum();
    } catch (IllegalArgumentException e) {
      this.saveLatestProposalNum(0);
    }

    try {
      this.getLatestExchangeNum();
    } catch (IllegalArgumentException e) {
      this.saveLatestExchangeNum(0);
    }

    try {
      this.getBlockFilledSlotsIndex();
    } catch (IllegalArgumentException e) {
      this.saveBlockFilledSlotsIndex(0);
    }

    try {
      this.getTokenIdNum();
    } catch (IllegalArgumentException e) {
      this.saveTokenIdNum(1000000L);
    }

    try {
      this.getTokenUpdateDone();
    } catch (IllegalArgumentException e) {
      this.saveTokenUpdateDone(0);
    }

    try {
      this.getMaxFrozenTime();
    } catch (IllegalArgumentException e) {
      this.saveMaxFrozenTime(3);
    }

    try {
      this.getMinFrozenTime();
    } catch (IllegalArgumentException e) {
      this.saveMinFrozenTime(3);
    }

    try {
      this.getMaxFrozenSupplyNumber();
    } catch (IllegalArgumentException e) {
      this.saveMaxFrozenSupplyNumber(10);
    }

    try {
      this.getMaxFrozenSupplyTime();
    } catch (IllegalArgumentException e) {
      this.saveMaxFrozenSupplyTime(3652);
    }

    try {
      this.getMinFrozenSupplyTime();
    } catch (IllegalArgumentException e) {
      this.saveMinFrozenSupplyTime(1);
    }

    try {
      this.getWitnessAllowanceFrozenTime();
    } catch (IllegalArgumentException e) {
      this.saveWitnessAllowanceFrozenTime(1);
    }

    try {
      this.getWitnessPayPerBlock();
    } catch (IllegalArgumentException e) {
      this.saveWitnessPayPerBlock(144000L);//0.144000VS
    }

    try {
      this.getWitnessStandbyAllowance();
    } catch (IllegalArgumentException e) {
      this.saveWitnessStandbyAllowance(115_200_000_000L);//115200 VS
    }

    try {
      this.getMaintenanceTimeInterval();
    } catch (IllegalArgumentException e) {
      this.saveMaintenanceTimeInterval(CommonParameter.getInstance()
          .getMaintenanceTimeInterval()); // 6 hours
    }

    try {
      this.getAccountUpgradeCost();
    } catch (IllegalArgumentException e) {
      this.saveAccountUpgradeCost(2_000_000_000L); // burn vs 9_999_000_000L --> 2_000_000_000L
    }

    try {
      this.getPublicPhotonUsage();
    } catch (IllegalArgumentException e) {
      this.savePublicPhotonUsage(0L);
    }

    try {
      this.getOneDayPhotonLimit();
    } catch (IllegalArgumentException e) {
      this.saveOneDayPhotonLimit(57_600_000_000L);
    }

    try {
      this.getPublicPhotonLimit();
    } catch (IllegalArgumentException e) {
      this.savePublicPhotonLimit(14_400_000_000L);
    }

    try {
      this.getPublicPhotonTime();
    } catch (IllegalArgumentException e) {
      this.savePublicPhotonTime(0L);
    }

    try {
      this.getFreePhotonLimit();
    } catch (IllegalArgumentException e) {
      this.saveFreePhotonLimit(5000L);
    }

    try {
      this.getTotalPhotonWeight();
    } catch (IllegalArgumentException e) {
      this.saveTotalPhotonWeight(0L);
    }

    try {
      this.getTotalPhotonLimit();
    } catch (IllegalArgumentException e) {
      this.saveTotalPhotonLimit(2_000_000_000L); //43_200_000_000L
    }

    try {
      this.getTotalEntropyWeight();
    } catch (IllegalArgumentException e) {
      this.saveTotalEntropyWeight(0L);
    }

    try {
      this.getTotalSRGuaranteeWeight();
    } catch (IllegalArgumentException e) {
      this.saveTotalSRGuaranteeWeight(0L);
    }

    try {
      this.getTotalAssets();
    } catch (IllegalArgumentException e) {
      this.saveTotalAssets(0L);
    }

    try {
      this.getPledgeRate();
    } catch (IllegalArgumentException e) {
      this.savePledgeRate(0L);
    }

    try {
        this.getInflationRate();
    } catch (IllegalArgumentException e) {
        this.saveInflationRate(0L);
    }

    try {
      this.getPledgeRateThreshold();
    } catch (IllegalArgumentException e) {
      this.savePledgeRateThreshold(60L);
    }

    try {
      this.getLowInflationRate();
    } catch (IllegalArgumentException e) {
      this.saveLowInflationRate(689L);
    }

    try {
      this.getHighInflationRate();
    } catch (IllegalArgumentException e) {
      this.saveHighInflationRate(2322L);
    }

    try {
      this.getSpreadFreezePeriodLimit();
    } catch (IllegalArgumentException e) {
      this.saveSpreadFreezePeriodLimit(1L);
    }

    try {
      this.getGalaxyInitialAmount();
    } catch (IllegalArgumentException e) {
      this.saveGalaxyInitialAmount(0L);
    }

    try {
      this.getAvalonInitialAmount();
    } catch (IllegalArgumentException e) {
      this.saveAvalonInitialAmount(0L);
    }

    try {
      this.getLatestEconomyEndCycle();
    } catch (IllegalArgumentException e) {
      this.saveLatestEconomyEndCycle(FIRST_ECONOMY_CYCLE);
    }

    try {
      this.getAllowAdaptiveEntropy();
    } catch (IllegalArgumentException e) {
      this.saveAllowAdaptiveEntropy(CommonParameter.getInstance()
          .getAllowAdaptiveEntropy());
    }

    try {
      this.getAdaptiveResourceLimitTargetRatio();
    } catch (IllegalArgumentException e) {
      this.saveAdaptiveResourceLimitTargetRatio(14400);// 24 * 60 * 10,one minute 1/10 total limit
    }

    try {
      this.getTotalEntropyLimit();
    } catch (IllegalArgumentException e) {
      this.saveTotalEntropyLimit(3_000_000_000L); //50_000_000_000L
    }

    try {
      this.getEntropyFee();
    } catch (IllegalArgumentException e) {
      this.saveEntropyFee(5L);// 100 vdt per entropy
    }

    try {
      this.getMaxCpuTimeOfOneTx();
    } catch (IllegalArgumentException e) {
      this.saveMaxCpuTimeOfOneTx(50L);
    }

    try {
      this.getCreateAccountFee();
    } catch (IllegalArgumentException e) {
      this.saveCreateAccountFee(100_000L); // 0.1VS
    }

    try {
      this.getShieldedTransactionFee();
    } catch (IllegalArgumentException e) {
      this.saveShieldedTransactionFee(100_000L);
    }

    try {
      this.getShieldedTransactionCreateAccountFee();
    } catch (IllegalArgumentException e) {
      this.saveShieldedTransactionCreateAccountFee(1_000_000L);
    }

    try {
      this.getTotalShieldedPoolValue();
    } catch (IllegalArgumentException e) {
      this.saveTotalShieldedPoolValue(0L); // 0
    }

    try {
      this.getCreateNewAccountFeeInSystemContract();
    } catch (IllegalArgumentException e) {
      this.saveCreateNewAccountFeeInSystemContract(0L); //changed by committee later
    }

    try {
      this.getCreateNewAccountPhotonRate();
    } catch (IllegalArgumentException e) {
      this.saveCreateNewAccountPhotonRate(1L); //changed by committee later
    }

    try {
      this.getTransactionFee();
    } catch (IllegalArgumentException e) {
      this.saveTransactionFee(10L); // 10vdt/byte
    }

    try {
      this.getAssetIssueFee();
    } catch (IllegalArgumentException e) {
      this.saveAssetIssueFee(1024000000L);
    }

    try {
      this.getUpdateAccountPermissionFee();
    } catch (IllegalArgumentException e) {
      this.saveUpdateAccountPermissionFee(100000000L);
    }

    try {
      this.getMultiSignFee();
    } catch (IllegalArgumentException e) {
      this.saveMultiSignFee(1000000L);
    }

    try {
      this.getExchangeCreateFee();
    } catch (IllegalArgumentException e) {
      this.saveExchangeCreateFee(1024000000L);
    }

    try {
      this.getExchangeBalanceLimit();
    } catch (IllegalArgumentException e) {
      this.saveExchangeBalanceLimit(1_000_000_000_000_000L);
    }

    try {
      this.getAllowMarketTransaction();
    } catch (IllegalArgumentException e) {
      this.saveAllowMarketTransaction(CommonParameter.getInstance().getAllowMarketTransaction());
    }

    try {
      this.getMarketSellFee();
    } catch (IllegalArgumentException e) {
      this.saveMarketSellFee(0L); // 0L
    }

    try {
      this.getMarketCancelFee();
    } catch (IllegalArgumentException e) {
      this.saveMarketCancelFee(0L);
    }

    try {
      this.getMarketQuantityLimit();
    } catch (IllegalArgumentException e) {
      this.saveMarketQuantityLimit(1_000_000_000_000_000L);
    }


    try {
      this.getAllowTransactionFeePool();
    } catch (IllegalArgumentException e) {
      this.saveAllowTransactionFeePool(CommonParameter.getInstance().getAllowTransactionFeePool());
    }

    try {
      this.getTransactionFeePool();
    } catch (IllegalArgumentException e) {
      this.saveTransactionFeePool(0L);
    }

    try {
      this.getTotalTransactionCost();
    } catch (IllegalArgumentException e) {
      this.saveTotalTransactionCost(0L);
    }

    try {
      this.getTotalCreateWitnessCost();
    } catch (IllegalArgumentException e) {
      this.saveTotalCreateWitnessFee(0L);
    }

    try {
      this.getTotalCreateAccountCost();
    } catch (IllegalArgumentException e) {
      this.saveTotalCreateAccountFee(0L);
    }

    try {
      this.getTotalStoragePool();
    } catch (IllegalArgumentException e) {
      this.saveTotalStoragePool(100_000_000_000_000L);
    }

    try {
      this.getTotalStorageTax();
    } catch (IllegalArgumentException e) {
      this.saveTotalStorageTax(0);
    }

    try {
      this.getTotalStorageReserved();
    } catch (IllegalArgumentException e) {
      this.saveTotalStorageReserved(128L * 1024 * 1024 * 1024); // 137438953472 bytes
    }

    try {
      this.getStorageExchangeTaxRate();
    } catch (IllegalArgumentException e) {
      this.saveStorageExchangeTaxRate(10);
    }

    try {
      this.getRemoveThePowerOfTheGr();
    } catch (IllegalArgumentException e) {
      this.saveRemoveThePowerOfTheGr(0);
    }

    try {
      this.getAllowDelegateResource();
    } catch (IllegalArgumentException e) {
      this.saveAllowDelegateResource(CommonParameter.getInstance()
          .getAllowDelegateResource());
    }

    try {
      this.getAllowVvmTransferVrc10();
    } catch (IllegalArgumentException e) {
      this.saveAllowVvmTransferVrc10(CommonParameter.getInstance()
          .getAllowVvmTransferVrc10());
    }

    try {
      this.getAllowVvmConstantinople();
    } catch (IllegalArgumentException e) {
      this.saveAllowVvmConstantinople(CommonParameter.getInstance()
          .getAllowVvmConstantinople());
    }

    try {
      this.getAllowVvmSolidity059();
    } catch (IllegalArgumentException e) {
      this.saveAllowVvmSolidity059(CommonParameter.getInstance()
          .getAllowVvmSolidity059());
    }

    try {
      this.getForbidTransferToContract();
    } catch (IllegalArgumentException e) {
      this.saveForbidTransferToContract(CommonParameter.getInstance()
          .getForbidTransferToContract());
    }

    try {
      this.getAvailableContractType();
    } catch (IllegalArgumentException e) {
      String contractType = "7fff1fc0037e0000000000000000000000000000000000000000000000000000";
      byte[] bytes = ByteArray.fromHexString(contractType);
      this.saveAvailableContractType(bytes);
    }

    try {
      this.getActiveDefaultOperations();
    } catch (IllegalArgumentException e) {
      String contractType = "7fff1fc0033e0000000000000000000000000000000000000000000000000000";
      byte[] bytes = ByteArray.fromHexString(contractType);
      this.saveActiveDefaultOperations(bytes);
    }

    try {
      this.getAllowSameTokenName();
    } catch (IllegalArgumentException e) {
      this.saveAllowSameTokenName(CommonParameter.getInstance()
          .getAllowSameTokenName());
    }

    try {
      this.getAllowUpdateAccountName();
    } catch (IllegalArgumentException e) {
      this.saveAllowUpdateAccountName(0);
    }

    try {
      this.getAllowCreationOfContracts();
    } catch (IllegalArgumentException e) {
      this.saveAllowCreationOfContracts(CommonParameter.getInstance()
          .getAllowCreationOfContracts());
    }

    try {
      this.getAllowShieldedTransaction();
    } catch (IllegalArgumentException e) {
      this.saveAllowShieldedTransaction(0L);
    }

    try {
      this.getAllowShieldedVRC20Transaction();
    } catch (IllegalArgumentException e) {
      this.saveAllowShieldedVRC20Transaction(
          CommonParameter.getInstance().getAllowShieldedVRC20Transaction());
    }

    try {
      this.getAllowVvmIstanbul();
    } catch (IllegalArgumentException e) {
      this.saveAllowVvmIstanbul(
          CommonParameter.getInstance().getAllowVvmIstanbul());
    }

    try {
      this.getAllowVvmStake();
    } catch (IllegalArgumentException e) {
      this.saveAllowVvmStake(
              CommonParameter.getInstance().getAllowVvmStake());
    }

    try {
      this.getAllowVvmAssetIssue();
    } catch (IllegalArgumentException e) {
      this.saveAllowVvmAssetIssue(
              CommonParameter.getInstance().getAllowVvmAssetIssue());
    }

    try {
      this.getBlockFilledSlots();
    } catch (IllegalArgumentException e) {
      int[] blockFilledSlots = new int[getBlockFilledSlotsNumber()];
      Arrays.fill(blockFilledSlots, 1);
      this.saveBlockFilledSlots(blockFilledSlots);
    }

    try {
      this.getNextMaintenanceTime();
    } catch (IllegalArgumentException e) {
      this.saveNextMaintenanceTime(
          Long.parseLong(CommonParameter.getInstance()
              .getGenesisBlock().getTimestamp()));
    }

    try {
      this.getTotalEntropyCurrentLimit();
    } catch (IllegalArgumentException e) {
      this.saveTotalEntropyCurrentLimit(getTotalEntropyLimit());
    }

    try {
      this.getTotalEntropyTargetLimit();
    } catch (IllegalArgumentException e) {
      this.saveTotalEntropyTargetLimit(getTotalEntropyLimit() / 14400);
    }

    try {
      this.getTotalEntropyAverageUsage();
    } catch (IllegalArgumentException e) {
      this.saveTotalEntropyAverageUsage(0);
    }

    try {
      this.getAdaptiveResourceLimitMultiplier();
    } catch (IllegalArgumentException e) {
      this.saveAdaptiveResourceLimitMultiplier(1000);
    }

    try {
      this.getTotalEntropyAverageTime();
    } catch (IllegalArgumentException e) {
      this.saveTotalEntropyAverageTime(0);
    }

    try {
      this.getBlockEntropyUsage();
    } catch (IllegalArgumentException e) {
      this.saveBlockEntropyUsage(0);
    }

    try {
      this.getAllowAccountStateRoot();
    } catch (IllegalArgumentException e) {
      this.saveAllowAccountStateRoot(CommonParameter.getInstance()
          .getAllowAccountStateRoot());
    }

    try {
      this.getAllowProtoFilterNum();
    } catch (IllegalArgumentException e) {
      this.saveAllowProtoFilterNum(CommonParameter.getInstance()
          .getAllowProtoFilterNum());
    }

    try {
      this.getChangeDelegation();
    } catch (IllegalArgumentException e) {
      this.saveChangeDelegation(CommonParameter.getInstance()
          .getChangedDelegation());
    }

    try {
      this.getAllowPBFT();
    } catch (IllegalArgumentException e) {
      this.saveAllowPBFT(CommonParameter.getInstance().getAllowPBFT());
    }

    try {
      this.getMaxFeeLimit();
    } catch (IllegalArgumentException e) {
      this.saveMaxFeeLimit(1_000_000_000L);
    }

    try {
      this.getBurnVsAmount();
    } catch (IllegalArgumentException e) {
      this.saveBurnVs(0L);
    }
    try {
      this.getAllowBlackHoleOptimization();
    } catch (IllegalArgumentException e) {
      this.saveAllowBlackHoleOptimization(CommonParameter.getInstance().getAllowBlackHoleOptimization());
    }

    try {
      this.getEconomyCycle();
    } catch (IllegalArgumentException e) {
      this.saveEconomyCycle(120L);
    }

    try {
      this.getEffectEconomyCycle();
    } catch (IllegalArgumentException e) {
      this.saveEffectEconomyCycle(120L);
    }

    try {
      this.getVoteFreezeStageLevel1();
    } catch (IllegalArgumentException e) {
      this.saveVoteFreezeStageLevel1(Parameter.ChainConstant.VOTE_FREEZE_STAGE_LEVEL1);
    }
    try {
      this.getVoteFreezeStageLevel2();
    } catch (IllegalArgumentException e) {
      this.saveVoteFreezeStageLevel2(Parameter.ChainConstant.VOTE_FREEZE_STAGE_LEVEL2);
    }
    try {
      this.getVoteFreezeStageLevel3();
    } catch (IllegalArgumentException e) {
      this.saveVoteFreezeStageLevel3(Parameter.ChainConstant.VOTE_FREEZE_STAGE_LEVEL3);
    }
    try {
      this.getVoteFreezePercentLevel1();
    } catch (IllegalArgumentException e) {
      this.saveVoteFreezePercentLevel1(Parameter.ChainConstant.VOTE_FREEZE_PERCENT_LEVEL1);
    }
    try {
      this.getVoteFreezePercentLevel2();
    } catch (IllegalArgumentException e) {
      this.saveVoteFreezePercentLevel2(Parameter.ChainConstant.VOTE_FREEZE_PERCENT_LEVEL2);
    }
    try {
      this.getVoteFreezePercentLevel3();
    } catch (IllegalArgumentException e) {
      this.saveVoteFreezePercentLevel3(Parameter.ChainConstant.VOTE_FREEZE_PERCENT_LEVEL3);
    }

    try {
      this.getSrFreezeLowest();
    } catch (IllegalArgumentException e) {
      this.saveSrFreezeLowest(Parameter.ChainConstant.SR_FREEZE_LOWEST);
    }
    try {
      this.getSrFreezeLowestPercent();
    } catch (IllegalArgumentException e) {
      this.saveSrFreezeLowestPercent(Parameter.ChainConstant.SR_FREEZE_LOWEST_PERCENT);
    }

    try {
      this.getSpreadMintPayPerBlock();
    } catch (IllegalArgumentException e) {
      this.saveSpreadMintPayPerBlock(256000L);//0.256vs
    }

    try {
      this.getAllowSpreadMintLevelProp();
    } catch (IllegalArgumentException e) {
      this.saveAllowSpreadMintLevelProp(1L);
    }

    try {
      this.getSpreadMintLevelProp();
    } catch (IllegalArgumentException e) {
      this.saveSpreadMintLevelProp("80,10,8,2");
    }
  }

  public String intArrayToString(int[] a) {
    StringBuilder sb = new StringBuilder();
    for (int i : a) {
      sb.append(i);
    }
    return sb.toString();
  }

  public int[] stringToIntArray(String s) {
    int length = s.length();
    int[] result = new int[length];
    for (int i = 0; i < length; ++i) {
      result[i] = Integer.parseInt(s.substring(i, i + 1));
    }
    return result;
  }

  public void saveTokenIdNum(long num) {
    this.put(TOKEN_ID_NUM,
        new BytesCapsule(ByteArray.fromLong(num)));
  }

  public long getTokenIdNum() {
    return Optional.ofNullable(getUnchecked(TOKEN_ID_NUM))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOKEN_ID_NUM"));
  }

  public void saveTokenUpdateDone(long num) {
    this.put(TOKEN_UPDATE_DONE,
        new BytesCapsule(ByteArray.fromLong(num)));
  }

  public long getTokenUpdateDone() {
    return Optional.ofNullable(getUnchecked(TOKEN_UPDATE_DONE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOKEN_UPDATE_DONE"));
  }

  public void saveBlockFilledSlotsIndex(int blockFilledSlotsIndex) {
    logger.debug("blockFilledSlotsIndex:" + blockFilledSlotsIndex);
    this.put(BLOCK_FILLED_SLOTS_INDEX,
        new BytesCapsule(ByteArray.fromInt(blockFilledSlotsIndex)));
  }

  public int getBlockFilledSlotsIndex() {
    return Optional.ofNullable(getUnchecked(BLOCK_FILLED_SLOTS_INDEX))
        .map(BytesCapsule::getData)
        .map(ByteArray::toInt)
        .orElseThrow(
            () -> new IllegalArgumentException("not found BLOCK_FILLED_SLOTS_INDEX"));
  }

  public void saveMaxFrozenTime(int maxFrozenTime) {
    logger.debug("MAX_FROZEN_NUMBER:" + maxFrozenTime);
    this.put(MAX_FROZEN_TIME,
        new BytesCapsule(ByteArray.fromInt(maxFrozenTime)));
  }

  public int getMaxFrozenTime() {
    return Optional.ofNullable(getUnchecked(MAX_FROZEN_TIME))
        .map(BytesCapsule::getData)
        .map(ByteArray::toInt)
        .orElseThrow(
            () -> new IllegalArgumentException("not found MAX_FROZEN_TIME"));
  }

  public void saveMinFrozenTime(int minFrozenTime) {
    logger.debug("MIN_FROZEN_NUMBER:" + minFrozenTime);
    this.put(MIN_FROZEN_TIME,
        new BytesCapsule(ByteArray.fromInt(minFrozenTime)));
  }

  public int getMinFrozenTime() {
    return Optional.ofNullable(getUnchecked(MIN_FROZEN_TIME))
        .map(BytesCapsule::getData)
        .map(ByteArray::toInt)
        .orElseThrow(
            () -> new IllegalArgumentException("not found MIN_FROZEN_TIME"));
  }

  public void saveMaxFrozenSupplyNumber(int maxFrozenSupplyNumber) {
    logger.debug("MAX_FROZEN_SUPPLY_NUMBER:" + maxFrozenSupplyNumber);
    this.put(MAX_FROZEN_SUPPLY_NUMBER,
        new BytesCapsule(ByteArray.fromInt(maxFrozenSupplyNumber)));
  }

  public int getMaxFrozenSupplyNumber() {
    return Optional.ofNullable(getUnchecked(MAX_FROZEN_SUPPLY_NUMBER))
        .map(BytesCapsule::getData)
        .map(ByteArray::toInt)
        .orElseThrow(
            () -> new IllegalArgumentException("not found MAX_FROZEN_SUPPLY_NUMBER"));
  }

  public void saveMaxFrozenSupplyTime(int maxFrozenSupplyTime) {
    logger.debug("MAX_FROZEN_SUPPLY_NUMBER:" + maxFrozenSupplyTime);
    this.put(MAX_FROZEN_SUPPLY_TIME,
        new BytesCapsule(ByteArray.fromInt(maxFrozenSupplyTime)));
  }

  public int getMaxFrozenSupplyTime() {
    return Optional.ofNullable(getUnchecked(MAX_FROZEN_SUPPLY_TIME))
        .map(BytesCapsule::getData)
        .map(ByteArray::toInt)
        .orElseThrow(
            () -> new IllegalArgumentException("not found MAX_FROZEN_SUPPLY_TIME"));
  }

  public void saveMinFrozenSupplyTime(int minFrozenSupplyTime) {
    logger.debug("MIN_FROZEN_SUPPLY_NUMBER:" + minFrozenSupplyTime);
    this.put(MIN_FROZEN_SUPPLY_TIME,
        new BytesCapsule(ByteArray.fromInt(minFrozenSupplyTime)));
  }

  public int getMinFrozenSupplyTime() {
    return Optional.ofNullable(getUnchecked(MIN_FROZEN_SUPPLY_TIME))
        .map(BytesCapsule::getData)
        .map(ByteArray::toInt)
        .orElseThrow(
            () -> new IllegalArgumentException("not found MIN_FROZEN_SUPPLY_TIME"));
  }

  public void saveWitnessAllowanceFrozenTime(int witnessAllowanceFrozenTime) {
    logger.debug("WITNESS_ALLOWANCE_FROZEN_TIME:" + witnessAllowanceFrozenTime);
    this.put(WITNESS_ALLOWANCE_FROZEN_TIME,
        new BytesCapsule(ByteArray.fromInt(witnessAllowanceFrozenTime)));
  }

  public int getWitnessAllowanceFrozenTime() {
    return Optional.ofNullable(getUnchecked(WITNESS_ALLOWANCE_FROZEN_TIME))
        .map(BytesCapsule::getData)
        .map(ByteArray::toInt)
        .orElseThrow(
            () -> new IllegalArgumentException("not found WITNESS_ALLOWANCE_FROZEN_TIME"));
  }

  public void saveMaintenanceTimeInterval(long timeInterval) {
    logger.debug("MAINTENANCE_TIME_INTERVAL:" + timeInterval);
    this.put(MAINTENANCE_TIME_INTERVAL,
        new BytesCapsule(ByteArray.fromLong(timeInterval)));
  }

  public long getMaintenanceTimeInterval() {
    return Optional.ofNullable(getUnchecked(MAINTENANCE_TIME_INTERVAL))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found MAINTENANCE_TIME_INTERVAL"));
  }

  public void saveAccountUpgradeCost(long accountUpgradeCost) {
    logger.debug("ACCOUNT_UPGRADE_COST:" + accountUpgradeCost);
    this.put(ACCOUNT_UPGRADE_COST,
        new BytesCapsule(ByteArray.fromLong(accountUpgradeCost)));
  }

  public long getAccountUpgradeCost() {
    return Optional.ofNullable(getUnchecked(ACCOUNT_UPGRADE_COST))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ACCOUNT_UPGRADE_COST"));
  }

  public void saveWitnessPayPerBlock(long pay) {
    logger.debug("WITNESS_PAY_PER_BLOCK:" + pay);
    this.put(WITNESS_PAY_PER_BLOCK,
        new BytesCapsule(ByteArray.fromLong(pay)));
  }

  public long getWitnessPayPerBlock() {
    return Optional.ofNullable(getUnchecked(WITNESS_PAY_PER_BLOCK))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found WITNESS_PAY_PER_BLOCK"));
  }

  public long getWitnessPayPerBlockInflation() {
    long weight = getWitnessPayPerBlock();
    return (long) (weight * (getInflationRate() * 1.0 / 120000 + 1));
  }

  public void saveWitness123PayPerBlock(long pay) {
    logger.debug("WITNESS_100_PAY_PER_BLOCK:" + pay);
    this.put(WITNESS_123_PAY_PER_BLOCK,
        new BytesCapsule(ByteArray.fromLong(pay)));
  }

  public long getWitness123PayPerBlock() {
    return Optional.ofNullable(getUnchecked(WITNESS_123_PAY_PER_BLOCK))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElse(1200000L);
  }

  public long getWitness123PayPerBlockInflation() {
    return (long) (getWitness123PayPerBlock() * (getInflationRate() * 1.0 / 120000 + 1));
  }

  public void saveWitnessStandbyAllowance(long allowance) {
    logger.debug("WITNESS_STANDBY_ALLOWANCE:" + allowance);
    this.put(WITNESS_STANDBY_ALLOWANCE,
        new BytesCapsule(ByteArray.fromLong(allowance)));
  }

  public long getWitnessStandbyAllowance() {
    return Optional.ofNullable(getUnchecked(WITNESS_STANDBY_ALLOWANCE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found WITNESS_STANDBY_ALLOWANCE"));
  }

  public long getWitnessStandbyAllowanceInflation() {
    long weight = 0L;
    try{
      weight = getWitnessStandbyAllowance();
    } catch (Exception e){
      logger.info("no found WITNESS_STANDBY_ALLOWANCE");
    }
    return (long) (weight * (getInflationRate() * 1.0 / 120000 + 1));
  }

  public void saveOneDayPhotonLimit(long oneDayPhotonLimit) {
    this.put(DynamicResourceProperties.ONE_DAY_PHOTON_LIMIT,
        new BytesCapsule(ByteArray.fromLong(oneDayPhotonLimit)));
  }

  public long getOneDayPhotonLimit() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.ONE_DAY_PHOTON_LIMIT))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ONE_DAY_PHOTON_LIMIT"));
  }

  public void savePublicPhotonUsage(long publicPhotonUsage) {
    this.put(DynamicResourceProperties.PUBLIC_PHOTON_USAGE,
        new BytesCapsule(ByteArray.fromLong(publicPhotonUsage)));
  }

  public long getPublicPhotonUsage() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.PUBLIC_PHOTON_USAGE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found PUBLIC_PHOTON_USAGE"));
  }

  public void savePublicPhotonLimit(long publicPhotonLimit) {
    this.put(DynamicResourceProperties.PUBLIC_PHOTON_LIMIT,
        new BytesCapsule(ByteArray.fromLong(publicPhotonLimit)));
  }

  public long getPublicPhotonLimit() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.PUBLIC_PHOTON_LIMIT))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found PUBLIC_PHOTON_LIMIT"));
  }

  public void savePublicPhotonTime(long publicNetTime) {
    this.put(DynamicResourceProperties.PUBLIC_PHOTON_TIME,
        new BytesCapsule(ByteArray.fromLong(publicNetTime)));
  }

  public long getPublicPhotonTime() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.PUBLIC_PHOTON_TIME))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found PUBLIC_PHOTON_TIME"));
  }

  public void saveFreePhotonLimit(long freePhotonLimit) {
    this.put(DynamicResourceProperties.FREE_PHOTON_LIMIT,
        new BytesCapsule(ByteArray.fromLong(freePhotonLimit)));
  }

  public long getFreePhotonLimit() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.FREE_PHOTON_LIMIT))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found FREE_PHOTON_LIMIT"));
  }

  public void saveTotalPhotonWeight(long totalPhotonWeight) {
    this.put(DynamicResourceProperties.TOTAL_PHOTON_WEIGHT,
        new BytesCapsule(ByteArray.fromLong(totalPhotonWeight)));
  }

  public long getTotalPhotonWeight() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.TOTAL_PHOTON_WEIGHT))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOTAL_PHOTON_WEIGHT"));
  }

  public void saveTotalEntropyWeight(long totalEntropyWeight) {
    this.put(DynamicResourceProperties.TOTAL_ENTROPY_WEIGHT,
        new BytesCapsule(ByteArray.fromLong(totalEntropyWeight)));
  }

  public long getTotalEntropyWeight() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.TOTAL_ENTROPY_WEIGHT))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOTAL_ENTROPY_WEIGHT"));
  }

  public void saveTotalSRGuaranteeWeight(long totalSRGuaranteeWeight) {
    this.put(DynamicResourceProperties.TOTAL_SRGUARANTEE_WEIGHT,
            new BytesCapsule(ByteArray.fromLong(totalSRGuaranteeWeight)));
  }

  public long getTotalSRGuaranteeWeight() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.TOTAL_SRGUARANTEE_WEIGHT))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElseThrow(
            () -> new IllegalArgumentException("not found TOTAL_SRGUARANTEE_WEIGHT"));
  }

  public void saveTotalAssets(long totalAssets) {
    this.put(DynamicResourceProperties.TOTAL_ASSETS,
            new BytesCapsule(ByteArray.fromLong(totalAssets)));
  }

  public long getTotalAssets() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.TOTAL_ASSETS))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElse(0L);
  }

  public void savePledgeRate(long pledgeRate) {
    this.put(DynamicResourceProperties.PLEDGE_RATE,
            new BytesCapsule(ByteArray.fromLong(pledgeRate)));
  }

  public long getPledgeRate() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.PLEDGE_RATE))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElse(0L);
  }

  public void saveInflationRate(long inflationRate) {
    this.put(DynamicResourceProperties.INFLATION_RATE,
            new BytesCapsule(ByteArray.fromLong(inflationRate)));
  }

  public long getInflationRate() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.INFLATION_RATE))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElse(0L);
  }

  public void savePledgeRateThreshold(long pledgeRateThreshold) {
    this.put(DynamicResourceProperties.PLEDGE_RATE_THRESHOLD,
            new BytesCapsule(ByteArray.fromLong(pledgeRateThreshold)));
  }

  public long getPledgeRateThreshold() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.PLEDGE_RATE_THRESHOLD))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElse(60L);
  }

  public void saveSpreadFreezePeriodLimit(long freezePeriodLimit) {
    this.put(DynamicResourceProperties.SPREAD_FREEZE_PERIOD_LIMIT,
            new BytesCapsule(ByteArray.fromLong(freezePeriodLimit)));
  }

  public long getSpreadFreezePeriodLimit() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.SPREAD_FREEZE_PERIOD_LIMIT))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElse(1L);
  }

  public void saveAllowMetamaskSendRawTransaction(long allowMetamaskSendRawTransaction) {
    this.put(DynamicResourceProperties.ALLOW_METAMASK_SENDRAWTRANSACTION,
            new BytesCapsule(ByteArray.fromLong(allowMetamaskSendRawTransaction)));
  }

  public long getAllowMetamaskSendRawTransaction() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.ALLOW_METAMASK_SENDRAWTRANSACTION))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElse(1L);
  }

  public void saveLowInflationRate(long lowInflationRate) {
    this.put(DynamicResourceProperties.LOW_INFLATION_RATE,
            new BytesCapsule(ByteArray.fromLong(lowInflationRate)));
  }

  public long getLowInflationRate() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.LOW_INFLATION_RATE))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElse(689L);
  }

  public void saveHighInflationRate(long highInflationRate) {
    this.put(DynamicResourceProperties.HIGH_INFLATION_RATE,
            new BytesCapsule(ByteArray.fromLong(highInflationRate)));
  }

  public long getHighInflationRate() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.HIGH_INFLATION_RATE))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElse(2322L);
  }

  public void saveGalaxyInitialAmount(long galaxyInitialAmount) {
    this.put(DynamicResourceProperties.GALAXY_INITIAL_AMOUNT,
            new BytesCapsule(ByteArray.fromLong(galaxyInitialAmount)));
  }

  public long getGalaxyInitialAmount() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.GALAXY_INITIAL_AMOUNT))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElse(0L);
  }

  public void saveAvalonInitialAmount(long avalonInitialAmount) {
    this.put(DynamicResourceProperties.AVALON_INITIAL_AMOUNT,
            new BytesCapsule(ByteArray.fromLong(avalonInitialAmount)));
  }

  public long getAvalonInitialAmount() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.AVALON_INITIAL_AMOUNT))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElse(0L);
  }

  public void saveLatestEconomyEndCycle(long latestEconomyEndCycle) {
    this.put(DynamicResourceProperties.LATEST_ECONOMY_END_CYCLE,
            new BytesCapsule(ByteArray.fromLong(latestEconomyEndCycle)));
  }

  public long getLatestEconomyEndCycle() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.LATEST_ECONOMY_END_CYCLE))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElse(FIRST_ECONOMY_CYCLE);
  }

  public void saveTotalPhotonLimit(long totalPhotonLimit) {
    this.put(DynamicResourceProperties.TOTAL_PHOTON_LIMIT,
        new BytesCapsule(ByteArray.fromLong(totalPhotonLimit)));
  }

  public long getTotalPhotonLimit() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.TOTAL_PHOTON_LIMIT))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOTAL_PHOTON_LIMIT"));
  }

  @Deprecated
  public void saveTotalEntropyLimit(long totalEntropyLimit) {
    this.put(DynamicResourceProperties.TOTAL_ENTROPY_LIMIT,
        new BytesCapsule(ByteArray.fromLong(totalEntropyLimit)));

    long ratio = getAdaptiveResourceLimitTargetRatio();
    saveTotalEntropyTargetLimit(totalEntropyLimit / ratio);
  }

  public void saveTotalEntropyLimit2(long totalEntropyLimit) {
    this.put(DynamicResourceProperties.TOTAL_ENTROPY_LIMIT,
        new BytesCapsule(ByteArray.fromLong(totalEntropyLimit)));

    long ratio = getAdaptiveResourceLimitTargetRatio();
    saveTotalEntropyTargetLimit(totalEntropyLimit / ratio);
    if (getAllowAdaptiveEntropy() == 0) {
      saveTotalEntropyCurrentLimit(totalEntropyLimit);
    }
  }

  public long getTotalEntropyLimit() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.TOTAL_ENTROPY_LIMIT))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOTAL_ENTROPY_LIMIT"));
  }

  public void saveTotalEntropyCurrentLimit(long totalEntropyCurrentLimit) {
    this.put(DynamicResourceProperties.TOTAL_ENTROPY_CURRENT_LIMIT,
        new BytesCapsule(ByteArray.fromLong(totalEntropyCurrentLimit)));
  }

  public long getTotalEntropyCurrentLimit() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.TOTAL_ENTROPY_CURRENT_LIMIT))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOTAL_ENTROPY_CURRENT_LIMIT"));
  }

  public void saveTotalEntropyTargetLimit(long targetTotalEntropyLimit) {
    this.put(DynamicResourceProperties.TOTAL_ENTROPY_TARGET_LIMIT,
        new BytesCapsule(ByteArray.fromLong(targetTotalEntropyLimit)));
  }

  public long getTotalEntropyTargetLimit() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.TOTAL_ENTROPY_TARGET_LIMIT))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOTAL_ENTROPY_TARGET_LIMIT"));
  }

  public void saveTotalEntropyAverageUsage(long totalEntropyAverageUsage) {
    this.put(DynamicResourceProperties.TOTAL_ENTROPY_AVERAGE_USAGE,
        new BytesCapsule(ByteArray.fromLong(totalEntropyAverageUsage)));
  }

  public long getTotalEntropyAverageUsage() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.TOTAL_ENTROPY_AVERAGE_USAGE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOTAL_ENTROPY_AVERAGE_USAGE"));
  }

  public void saveAdaptiveResourceLimitMultiplier(long adaptiveResourceLimitMultiplier) {
    this.put(DynamicResourceProperties.ADAPTIVE_RESOURCE_LIMIT_MULTIPLIER,
        new BytesCapsule(ByteArray.fromLong(adaptiveResourceLimitMultiplier)));
  }

  public long getAdaptiveResourceLimitMultiplier() {
    return Optional
        .ofNullable(getUnchecked(DynamicResourceProperties.ADAPTIVE_RESOURCE_LIMIT_MULTIPLIER))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ADAPTIVE_RESOURCE_LIMIT_MULTIPLIER"));
  }

  public void saveAdaptiveResourceLimitTargetRatio(long adaptiveResourceLimitTargetRatio) {
    this.put(DynamicResourceProperties.ADAPTIVE_RESOURCE_LIMIT_TARGET_RATIO,
        new BytesCapsule(ByteArray.fromLong(adaptiveResourceLimitTargetRatio)));
  }

  public long getAdaptiveResourceLimitTargetRatio() {
    return Optional
        .ofNullable(getUnchecked(DynamicResourceProperties.ADAPTIVE_RESOURCE_LIMIT_TARGET_RATIO))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ADAPTIVE_RESOURCE_LIMIT_TARGET_RATIO"));
  }

  public void saveTotalEntropyAverageTime(long totalEntropyAverageTime) {
    this.put(DynamicResourceProperties.TOTAL_ENTROPY_AVERAGE_TIME,
        new BytesCapsule(ByteArray.fromLong(totalEntropyAverageTime)));
  }

  public long getTotalEntropyAverageTime() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.TOTAL_ENTROPY_AVERAGE_TIME))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOTAL_PHOTON_AVERAGE_TIME"));
  }

  public void saveBlockEntropyUsage(long blockEntropyUsage) {
    this.put(DynamicResourceProperties.BLOCK_ENTROPY_USAGE,
        new BytesCapsule(ByteArray.fromLong(blockEntropyUsage)));
  }

  public long getBlockEntropyUsage() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.BLOCK_ENTROPY_USAGE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found BLOCK_ENTROPY_USAGE"));
  }

  public void saveEntropyFee(long totalEntropyFee) {
    this.put(ENTROPY_FEE,
        new BytesCapsule(ByteArray.fromLong(totalEntropyFee)));
  }

  public long getEntropyFee() {
    return Optional.ofNullable(getUnchecked(ENTROPY_FEE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ENTROPY_FEE"));
  }

  public void saveMaxCpuTimeOfOneTx(long time) {
    this.put(MAX_CPU_TIME_OF_ONE_TX,
        new BytesCapsule(ByteArray.fromLong(time)));
  }

  public long getMaxCpuTimeOfOneTx() {
    return Optional.ofNullable(getUnchecked(MAX_CPU_TIME_OF_ONE_TX))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found MAX_CPU_TIME_OF_ONE_TX"));
  }

  public void saveCreateAccountFee(long fee) {
    this.put(CREATE_ACCOUNT_FEE,
        new BytesCapsule(ByteArray.fromLong(fee)));
  }

  public long getShieldedTransactionCreateAccountFee() {
    return Optional.ofNullable(getUnchecked(SHIELDED_TRANSACTION_CREATE_ACCOUNT_FEE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException(
                "not found SHIELDED_TRANSACTION_CREATE_ACCOUNT_FEE"));
  }

  public void saveShieldedTransactionCreateAccountFee(long fee) {
    this.put(SHIELDED_TRANSACTION_CREATE_ACCOUNT_FEE,
        new BytesCapsule(ByteArray.fromLong(fee)));
  }

  public long getShieldedTransactionFee() {
    return Optional.ofNullable(getUnchecked(SHIELDED_TRANSACTION_FEE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found SHIELD_TRANSACTION_FEE"));
  }

  public void saveShieldedTransactionFee(long fee) {
    this.put(SHIELDED_TRANSACTION_FEE,
        new BytesCapsule(ByteArray.fromLong(fee)));
  }

  public long getTotalShieldedPoolValue() {
    return Optional.ofNullable(getUnchecked(TOTAL_SHIELDED_POOL_VALUE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOTAL_SHIELDED_POOL_Value"));
  }

  public void saveTotalShieldedPoolValue(long value) {
    this.put(TOTAL_SHIELDED_POOL_VALUE,
        new BytesCapsule(ByteArray.fromLong(value)));
  }

  public long getCreateAccountFee() {
    return Optional.ofNullable(getUnchecked(CREATE_ACCOUNT_FEE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found CREATE_ACCOUNT_FEE"));
  }

  public void saveCreateNewAccountFeeInSystemContract(long fee) {
    this.put(CREATE_NEW_ACCOUNT_FEE_IN_SYSTEM_CONTRACT,
        new BytesCapsule(ByteArray.fromLong(fee)));
  }

  public long getCreateNewAccountFeeInSystemContract() {
    return Optional.ofNullable(getUnchecked(CREATE_NEW_ACCOUNT_FEE_IN_SYSTEM_CONTRACT))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException(
                "not found CREATE_NEW_ACCOUNT_FEE_IN_SYSTEM_CONTRACT"));
  }

  public void saveCreateNewAccountPhotonRate(long rate) {
    this.put(CREATE_NEW_ACCOUNT_PHOTON_RATE,
        new BytesCapsule(ByteArray.fromLong(rate)));
  }

  public long getCreateNewAccountPhotonRate() {
    return Optional.ofNullable(getUnchecked(CREATE_NEW_ACCOUNT_PHOTON_RATE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException(
                "not found CREATE_NsEW_ACCOUNT_PHOTON_RATE2"));
  }

  public void saveTransactionFee(long fee) {
    this.put(TRANSACTION_FEE,
        new BytesCapsule(ByteArray.fromLong(fee)));
  }

  public long getTransactionFee() {
    return Optional.ofNullable(getUnchecked(TRANSACTION_FEE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TRANSACTION_FEE"));
  }

  public void saveAssetIssueFee(long fee) {
    this.put(ASSET_ISSUE_FEE,
        new BytesCapsule(ByteArray.fromLong(fee)));
  }

  public void saveUpdateAccountPermissionFee(long fee) {
    this.put(UPDATE_ACCOUNT_PERMISSION_FEE,
        new BytesCapsule(ByteArray.fromLong(fee)));
  }

  public void saveMultiSignFee(long fee) {
    this.put(MULTI_SIGN_FEE,
        new BytesCapsule(ByteArray.fromLong(fee)));
  }

  public long getAssetIssueFee() {
    return Optional.ofNullable(getUnchecked(ASSET_ISSUE_FEE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ASSET_ISSUE_FEE"));
  }

  public long getUpdateAccountPermissionFee() {
    return Optional.ofNullable(getUnchecked(UPDATE_ACCOUNT_PERMISSION_FEE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found UPDATE_ACCOUNT_PERMISSION_FEE"));
  }

  public long getMultiSignFee() {
    return Optional.ofNullable(getUnchecked(MULTI_SIGN_FEE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found MULTI_SIGN_FEE"));
  }

  public void saveExchangeCreateFee(long fee) {
    this.put(EXCHANGE_CREATE_FEE,
        new BytesCapsule(ByteArray.fromLong(fee)));
  }

  public long getExchangeCreateFee() {
    return Optional.ofNullable(getUnchecked(EXCHANGE_CREATE_FEE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found EXCHANGE_CREATE_FEE"));
  }

  public void saveExchangeBalanceLimit(long limit) {
    this.put(EXCHANGE_BALANCE_LIMIT,
        new BytesCapsule(ByteArray.fromLong(limit)));
  }

  public long getExchangeBalanceLimit() {
    return Optional.ofNullable(getUnchecked(EXCHANGE_BALANCE_LIMIT))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found EXCHANGE_BALANCE_LIMIT"));
  }

  public void saveAllowMarketTransaction(long allowMarketTransaction) {
    this.put(DynamicPropertiesStore.ALLOW_MARKET_TRANSACTION,
        new BytesCapsule(ByteArray.fromLong(allowMarketTransaction)));
  }

  public long getAllowMarketTransaction() {
    return Optional.ofNullable(getUnchecked(ALLOW_MARKET_TRANSACTION))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ALLOW_MARKET_TRANSACTION"));
  }

  public boolean supportAllowMarketTransaction() {
    return getAllowMarketTransaction() == 1L;
  }

  public void saveMarketSellFee(long fee) {
    this.put(MARKET_SELL_FEE,
        new BytesCapsule(ByteArray.fromLong(fee)));
  }

  public long getMarketSellFee() {
    return Optional.ofNullable(getUnchecked(MARKET_SELL_FEE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found MARKET_SELL_FEE"));
  }

  public void saveMarketCancelFee(long fee) {
    this.put(MARKET_CANCEL_FEE,
        new BytesCapsule(ByteArray.fromLong(fee)));
  }

  public long getMarketCancelFee() {
    return Optional.ofNullable(getUnchecked(MARKET_CANCEL_FEE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found MARKET_CANCEL_FEE"));
  }

  public void saveMarketQuantityLimit(long limit) {
    this.put(MARKET_QUANTITY_LIMIT,
        new BytesCapsule(ByteArray.fromLong(limit)));
  }

  public long getMarketQuantityLimit() {
    return Optional.ofNullable(getUnchecked(MARKET_QUANTITY_LIMIT))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found MARKET_QUANTITY_LIMIT"));
  }

  public boolean supportTransactionFeePool() {
    return getAllowTransactionFeePool() == 1L;
  }

  public void saveAllowTransactionFeePool(long value) {
    this.put(ALLOW_TRANSACTION_FEE_POOL,
        new BytesCapsule(ByteArray.fromLong(value)));
  }

  public long getAllowTransactionFeePool() {
    return Optional.ofNullable(getUnchecked(ALLOW_TRANSACTION_FEE_POOL))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElseThrow(
                    () -> new IllegalArgumentException("not found ALLOW_TRANSACTION_FEE_POOL"));
  }
  public void addTransactionFeePool(long amount) {
    if (amount <= 0) {
      return;
    }
    amount += getTransactionFeePool();
    saveTransactionFeePool(amount);
  }

  public void saveTransactionFeePool(long value) {
    this.put(TRANSACTION_FEE_POOL,
            new BytesCapsule(ByteArray.fromLong(value)));
  }

  public long getTransactionFeePool() {
    return Optional.ofNullable(getUnchecked(TRANSACTION_FEE_POOL))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElseThrow(
                    () -> new IllegalArgumentException("not found TRANSACTION_FEE_POOL"));
  }

  public void saveTotalTransactionCost(long value) {
    this.put(TOTAL_TRANSACTION_COST,
        new BytesCapsule(ByteArray.fromLong(value)));
  }

  public long getTotalTransactionCost() {
    return Optional.ofNullable(getUnchecked(TOTAL_TRANSACTION_COST))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOTAL_TRANSACTION_COST"));
  }

  public void saveTotalCreateAccountFee(long value) {
    this.put(TOTAL_CREATE_ACCOUNT_COST,
        new BytesCapsule(ByteArray.fromLong(value)));
  }

  public long getTotalCreateAccountCost() {
    return Optional.ofNullable(getUnchecked(TOTAL_CREATE_ACCOUNT_COST))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOTAL_CREATE_ACCOUNT_COST"));
  }

  public void saveTotalCreateWitnessFee(long value) {
    this.put(TOTAL_CREATE_WITNESS_COST,
        new BytesCapsule(ByteArray.fromLong(value)));
  }

  public long getTotalCreateWitnessCost() {
    return Optional.ofNullable(getUnchecked(TOTAL_CREATE_WITNESS_COST))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOTAL_CREATE_WITNESS_COST"));
  }

  public void saveTotalStoragePool(long trx) {
    this.put(TOTAL_STORAGE_POOL,
        new BytesCapsule(ByteArray.fromLong(trx)));
  }

  public long getTotalStoragePool() {
    return Optional.ofNullable(getUnchecked(TOTAL_STORAGE_POOL))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOTAL_STORAGE_POOL"));
  }

  public void saveTotalStorageTax(long trx) {
    this.put(TOTAL_STORAGE_TAX,
        new BytesCapsule(ByteArray.fromLong(trx)));
  }

  public long getTotalStorageTax() {
    return Optional.ofNullable(getUnchecked(TOTAL_STORAGE_TAX))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOTAL_STORAGE_TAX"));
  }

  public void saveTotalStorageReserved(long bytes) {
    this.put(TOTAL_STORAGE_RESERVED,
        new BytesCapsule(ByteArray.fromLong(bytes)));
  }

  public long getTotalStorageReserved() {
    return Optional.ofNullable(getUnchecked(TOTAL_STORAGE_RESERVED))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOTAL_STORAGE_RESERVED"));
  }

  public void saveStorageExchangeTaxRate(long rate) {
    this.put(STORAGE_EXCHANGE_TAX_RATE,
        new BytesCapsule(ByteArray.fromLong(rate)));
  }

  public long getStorageExchangeTaxRate() {
    return Optional.ofNullable(getUnchecked(STORAGE_EXCHANGE_TAX_RATE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found STORAGE_EXCHANGE_TAX_RATE"));
  }

  public void saveRemoveThePowerOfTheGr(long rate) {
    this.put(REMOVE_THE_POWER_OF_THE_GR,
        new BytesCapsule(ByteArray.fromLong(rate)));
  }

  public long getRemoveThePowerOfTheGr() {
    return Optional.ofNullable(getUnchecked(REMOVE_THE_POWER_OF_THE_GR))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found REMOVE_THE_POWER_OF_THE_GR"));
  }

  public void saveAllowDelegateResource(long value) {
    this.put(ALLOW_DELEGATE_RESOURCE,
        new BytesCapsule(ByteArray.fromLong(value)));
  }

  public long getAllowDelegateResource() {
    return Optional.ofNullable(getUnchecked(ALLOW_DELEGATE_RESOURCE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ALLOW_DELEGATE_RESOURCE"));
  }

  public void saveAllowAdaptiveEntropy(long value) {
    this.put(ALLOW_ADAPTIVE_ENTROPY,
        new BytesCapsule(ByteArray.fromLong(value)));
  }

  public long getAllowAdaptiveEntropy() {
    return Optional.ofNullable(getUnchecked(ALLOW_ADAPTIVE_ENTROPY))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ALLOW_ADAPTIVE_ENTROPY"));
  }

  public void saveAllowVvmTransferVrc10(long value) {
    this.put(ALLOW_VVM_TRANSFER_VRC10,
        new BytesCapsule(ByteArray.fromLong(value)));
  }

  public long getAllowVvmTransferVrc10() {
    return Optional.ofNullable(getUnchecked(ALLOW_VVM_TRANSFER_VRC10))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ALLOW_VVM_TRANSFER_VRC10"));
  }

  public void saveAllowVvmConstantinople(long value) {
    this.put(ALLOW_VVM_CONSTANTINOPLE,
        new BytesCapsule(ByteArray.fromLong(value)));
  }

  public long getAllowVvmConstantinople() {
    return Optional.ofNullable(getUnchecked(ALLOW_VVM_CONSTANTINOPLE))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ALLOW_VVM_CONSTANTINOPLE"));
  }

  public void saveAllowVvmSolidity059(long value) {
    this.put(ALLOW_VVM_SOLIDITY_059,
        new BytesCapsule(ByteArray.fromLong(value)));
  }

  public long getAllowVvmSolidity059() {
    return Optional.ofNullable(getUnchecked(ALLOW_VVM_SOLIDITY_059))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(() -> new IllegalArgumentException("not found ALLOW_VVM_SOLIDITY_059"));
  }

  public void saveForbidTransferToContract(long value) {
    this.put(FORBID_TRANSFER_TO_CONTRACT,
        new BytesCapsule(ByteArray.fromLong(value)));
  }

  public long getForbidTransferToContract() {
    return Optional.ofNullable(getUnchecked(FORBID_TRANSFER_TO_CONTRACT))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(() -> new IllegalArgumentException("not found FORBID_TRANSFER_TO_CONTRACT"));
  }

  public void saveAvailableContractType(byte[] value) {
    this.put(AVAILABLE_CONTRACT_TYPE,
        new BytesCapsule(value));
  }

  public byte[] getAvailableContractType() {
    return Optional.ofNullable(getUnchecked(AVAILABLE_CONTRACT_TYPE))
        .map(BytesCapsule::getData)
        .orElseThrow(
            () -> new IllegalArgumentException("not found AVAILABLE_CONTRACT_TYPE"));
  }

  public void addSystemContractAndSetPermission(int id) {
    byte[] availableContractType = getAvailableContractType();
    availableContractType[id / 8] |= (1 << id % 8);
    saveAvailableContractType(availableContractType);

    byte[] activeDefaultOperations = getActiveDefaultOperations();
    activeDefaultOperations[id / 8] |= (1 << id % 8);
    saveActiveDefaultOperations(activeDefaultOperations);
  }

  public void updateDynamicStoreByConfig() {
    if (CommonParameter.getInstance()
        .getAllowVvmConstantinople() != 0) {
      saveAllowVvmConstantinople(CommonParameter.getInstance()
          .getAllowVvmConstantinople());
      addSystemContractAndSetPermission(48);
    }
    if (CommonParameter.getInstance()
            .getChangedDelegation() != 0) {
      saveChangeDelegation(CommonParameter.getInstance()
              .getChangedDelegation());
      addSystemContractAndSetPermission(49);
    }
    if (CommonParameter.getInstance()
            .getAllowMarketTransaction() != 0) {
      saveChangeDelegation(CommonParameter.getInstance()
              .getAllowMarketTransaction());
      addSystemContractAndSetPermission(52);
      addSystemContractAndSetPermission(53);
    }
  }

  public void saveActiveDefaultOperations(byte[] value) {
    this.put(ACTIVE_DEFAULT_OPERATIONS,
        new BytesCapsule(value));
  }

  public byte[] getActiveDefaultOperations() {
    return Optional.ofNullable(getUnchecked(ACTIVE_DEFAULT_OPERATIONS))
        .map(BytesCapsule::getData)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ACTIVE_DEFAULT_OPERATIONS"));
  }

  public boolean supportDR() {
    return getAllowDelegateResource() == 1L;
  }

  public void saveAllowUpdateAccountName(long rate) {
    this.put(ALLOW_UPDATE_ACCOUNT_NAME,
        new BytesCapsule(ByteArray.fromLong(rate)));
  }

  public long getAllowUpdateAccountName() {
    return Optional.ofNullable(getUnchecked(ALLOW_UPDATE_ACCOUNT_NAME))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ALLOW_UPDATE_ACCOUNT_NAME"));
  }

  public void saveAllowSameTokenName(long rate) {
    this.put(ALLOW_SAME_TOKEN_NAME,
        new BytesCapsule(ByteArray.fromLong(rate)));
  }

  public long getAllowSameTokenName() {
    return Optional.ofNullable(getUnchecked(ALLOW_SAME_TOKEN_NAME))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ALLOW_SAME_TOKEN_NAME"));
  }

  public void saveAllowCreationOfContracts(long allowCreationOfContracts) {
    this.put(ALLOW_CREATION_OF_CONTRACTS,
        new BytesCapsule(ByteArray.fromLong(allowCreationOfContracts)));
  }

  public void saveTotalSignNum(int num) {
    this.put(DynamicPropertiesStore.TOTAL_SIGN_NUM,
        new BytesCapsule(ByteArray.fromInt(num)));
  }

  public int getTotalSignNum() {
    return Optional.ofNullable(getUnchecked(TOTAL_SIGN_NUM))
        .map(BytesCapsule::getData)
        .map(ByteArray::toInt)
        .orElseThrow(
            () -> new IllegalArgumentException("not found TOTAL_SIGN_NUM"));
  }

  public void saveAllowMultiSign(long allowMultiSing) {
    this.put(ALLOW_MULTI_SIGN,
        new BytesCapsule(ByteArray.fromLong(allowMultiSing)));
  }

  public long getAllowMultiSign() {
    return Optional.ofNullable(getUnchecked(ALLOW_MULTI_SIGN))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ALLOW_MULTI_SIGN"));
  }

  public long getAllowCreationOfContracts() {
    return Optional.ofNullable(getUnchecked(ALLOW_CREATION_OF_CONTRACTS))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ALLOW_CREATION_OF_CONTRACTS"));
  }

  public boolean supportVM() {
    return getAllowCreationOfContracts() == 1L;
  }


  public void saveAllowShieldedTransaction(long allowShieldedTransaction) {
    this.put(DynamicPropertiesStore.ALLOW_SHIELDED_TRANSACTION,
        new BytesCapsule(ByteArray.fromLong(allowShieldedTransaction)));
  }

  public long getAllowShieldedTransaction() {
    return Optional.ofNullable(getUnchecked(ALLOW_SHIELDED_TRANSACTION))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ALLOW_SHIELDED_TRANSACTION"));
  }

  public void saveAllowShieldedVRC20Transaction(long allowShieldedVRC20Transaction) {
    this.put(DynamicPropertiesStore.ALLOW_SHIELDED_VRC20_TRANSACTION,
        new BytesCapsule(ByteArray.fromLong(allowShieldedVRC20Transaction)));
  }

  public long getAllowShieldedVRC20Transaction() {
    String msg = "not found ALLOW_SHIELDED_VRC20_TRANSACTION";
    return Optional.ofNullable(getUnchecked(ALLOW_SHIELDED_VRC20_TRANSACTION))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException(msg));
  }

  public void saveAllowVvmIstanbul(long allowVVMIstanbul) {
    this.put(DynamicPropertiesStore.ALLOW_VVM_ISTANBUL,
        new BytesCapsule(ByteArray.fromLong(allowVVMIstanbul)));
  }

  public long getAllowVvmIstanbul() {
    String msg = "not found ALLOW_VVM_ISTANBUL";
    return Optional.ofNullable(getUnchecked(ALLOW_VVM_ISTANBUL))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException(msg));
  }

  public void saveAllowVvmStake(long allowVvmStake) {
    this.put(DynamicPropertiesStore.ALLOW_VVM_STAKE,
            new BytesCapsule(ByteArray.fromLong(allowVvmStake)));
  }

  public void saveAllowVvmAssetIssue(long allowVvmAssetIssue) {
    this.put(DynamicPropertiesStore.ALLOW_VVM_ASSET_ISSUE,
            new BytesCapsule(ByteArray.fromLong(allowVvmAssetIssue)));
  }

  public long getAllowVvmStake() {
    String msg = "not found ALLOW_VVM_STAKE";
    return Optional.ofNullable(getUnchecked(ALLOW_VVM_STAKE))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElseThrow(
                    () -> new IllegalArgumentException(msg));
  }

  public long getAllowVvmAssetIssue() {
    String msg = "not found ALLOW_VVM_ASSETISSUE";
    return Optional.ofNullable(getUnchecked(ALLOW_VVM_ASSET_ISSUE))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElseThrow(
                    () -> new IllegalArgumentException(msg));
  }


  public boolean supportShieldedTransaction() {
    return getAllowShieldedTransaction() == 1L;
  }

  public boolean supportShieldedVRC20Transaction() {
    return getAllowShieldedVRC20Transaction() == 1L;
  }

  public void saveBlockFilledSlots(int[] blockFilledSlots) {
    logger.debug("blockFilledSlots:" + intArrayToString(blockFilledSlots));
    this.put(BLOCK_FILLED_SLOTS,
        new BytesCapsule(ByteArray.fromString(intArrayToString(blockFilledSlots))));
  }

  public int[] getBlockFilledSlots() {
    return Optional.ofNullable(getUnchecked(BLOCK_FILLED_SLOTS))
        .map(BytesCapsule::getData)
        .map(ByteArray::toStr)
        .map(this::stringToIntArray)
        .orElseThrow(
            () -> new IllegalArgumentException(
                "not found latest SOLIDIFIED_BLOCK_NUM timestamp"));
  }

  public int getBlockFilledSlotsNumber() {
    return ChainConstant.BLOCK_FILLED_SLOTS_NUMBER;
  }

  public void applyBlock(boolean fillBlock) {
    int[] blockFilledSlots = getBlockFilledSlots();
    int blockFilledSlotsIndex = getBlockFilledSlotsIndex();
    blockFilledSlots[blockFilledSlotsIndex] = fillBlock ? 1 : 0;
    saveBlockFilledSlotsIndex((blockFilledSlotsIndex + 1) % getBlockFilledSlotsNumber());
    saveBlockFilledSlots(blockFilledSlots);
  }

  public int calculateFilledSlotsCount() {
    int[] blockFilledSlots = getBlockFilledSlots();
    return 100 * IntStream.of(blockFilledSlots).sum() / getBlockFilledSlotsNumber();
  }

  public void saveLatestSolidifiedBlockNum(long number) {
    this.put(LATEST_SOLIDIFIED_BLOCK_NUM, new BytesCapsule(ByteArray.fromLong(number)));
  }

  public long getLatestSolidifiedBlockNum() {
    return Optional.ofNullable(getUnchecked(LATEST_SOLIDIFIED_BLOCK_NUM))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found latest SOLIDIFIED_BLOCK_NUM"));
  }

  public void saveLatestProposalNum(long number) {
    this.put(LATEST_PROPOSAL_NUM, new BytesCapsule(ByteArray.fromLong(number)));
  }

  public long getLatestProposalNum() {
    return Optional.ofNullable(getUnchecked(LATEST_PROPOSAL_NUM))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found latest PROPOSAL_NUM"));
  }

  public void saveLatestExchangeNum(long number) {
    this.put(LATEST_EXCHANGE_NUM, new BytesCapsule(ByteArray.fromLong(number)));
  }

  public long getLatestExchangeNum() {
    return Optional.ofNullable(getUnchecked(LATEST_EXCHANGE_NUM))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found latest EXCHANGE_NUM"));
  }

  /**
   * get timestamp of creating global latest block.
   */
  public long getLatestBlockHeaderTimestamp() {
    return Optional.ofNullable(getUnchecked(LATEST_BLOCK_HEADER_TIMESTAMP))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found latest block header timestamp"));
  }

  /**
   * get number of global latest block.
   */
  public long getLatestBlockHeaderNumber() {
    return Optional.ofNullable(getUnchecked(LATEST_BLOCK_HEADER_NUMBER))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found latest block header number"));
  }

  public int getStateFlag() {
    return Optional.ofNullable(getUnchecked(STATE_FLAG))
        .map(BytesCapsule::getData)
        .map(ByteArray::toInt)
        .orElseThrow(() -> new IllegalArgumentException("not found maintenance flag"));
  }

  /**
   * get id of global latest block.
   */

  public Sha256Hash getLatestBlockHeaderHash() {
    byte[] blockHash = Optional.ofNullable(getUnchecked(LATEST_BLOCK_HEADER_HASH))
        .map(BytesCapsule::getData)
        .orElseThrow(() -> new IllegalArgumentException("not found block hash"));
    return Sha256Hash.wrap(blockHash);
  }

  /**
   * save timestamp of creating global latest block.
   */
  public void saveLatestBlockHeaderTimestamp(long t) {
    logger.info("update latest block header timestamp = {}", t);
    this.put(LATEST_BLOCK_HEADER_TIMESTAMP, new BytesCapsule(ByteArray.fromLong(t)));
  }

  /**
   * save number of global latest block.
   */
  public void saveLatestBlockHeaderNumber(long n) {
    logger.info("update latest block header number = {}", n);
    this.put(LATEST_BLOCK_HEADER_NUMBER, new BytesCapsule(ByteArray.fromLong(n)));
  }

  /**
   * save id of global latest block.
   */
  public void saveLatestBlockHeaderHash(ByteString h) {
    logger.info("update latest block header id = {}", ByteArray.toHexString(h.toByteArray()));
    this.put(LATEST_BLOCK_HEADER_HASH, new BytesCapsule(h.toByteArray()));
  }

  public void saveStateFlag(int n) {
    logger.info("update state flag = {}", n);
    this.put(STATE_FLAG, new BytesCapsule(ByteArray.fromInt(n)));
  }

  public long getNextMaintenanceTime() {
    return Optional.ofNullable(getUnchecked(NEXT_MAINTENANCE_TIME))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found NEXT_MAINTENANCE_TIME"));
  }

  public long getMaintenanceSkipSlots() {
    return Parameter.ChainConstant.MAINTENANCE_SKIP_SLOTS;
  }

  public void saveNextMaintenanceTime(long nextMaintenanceTime) {
    this.put(NEXT_MAINTENANCE_TIME,
        new BytesCapsule(ByteArray.fromLong(nextMaintenanceTime)));
  }

  public void updateNextMaintenanceTime(long blockTime) {
    long maintenanceTimeInterval = getMaintenanceTimeInterval();

    long currentMaintenanceTime = getNextMaintenanceTime();
    long round = (blockTime - currentMaintenanceTime) / maintenanceTimeInterval;
    long nextMaintenanceTime = currentMaintenanceTime + (round + 1) * maintenanceTimeInterval;
    saveNextMaintenanceTime(nextMaintenanceTime);

    logger.info(
        "do update nextMaintenanceTime,currentMaintenanceTime:{}, blockTime:{},"
            + "nextMaintenanceTime:{}",
        new DateTime(currentMaintenanceTime), new DateTime(blockTime),
        new DateTime(nextMaintenanceTime)
    );
  }

  //The unit is vs
  public void addTotalPhotonWeight(long amount) {
    long totalPhotonWeight = getTotalPhotonWeight();
    totalPhotonWeight += amount;
    saveTotalPhotonWeight(totalPhotonWeight);
  }

  //The unit is vs
  public void addTotalEntropyWeight(long amount) {
    long totalEntropyWeight = getTotalEntropyWeight();
    totalEntropyWeight += amount;
    saveTotalEntropyWeight(totalEntropyWeight);
  }

  //The unit is vs
  public void addTotalSRGuaranteeWeight(long amount) {
    long totalnewSRGuarantee = getTotalSRGuaranteeWeight();
    totalnewSRGuarantee += amount;
    saveTotalSRGuaranteeWeight(totalnewSRGuarantee);
  }

  public void addTotalAssets(long amount) {
    long totalAssets = getTotalAssets();
    totalAssets += amount;
    saveTotalAssets(totalAssets);
  }

  public void addTotalCreateAccountCost(long fee) {
    long newValue = getTotalCreateAccountCost() + fee;
    saveTotalCreateAccountFee(newValue);
  }

  public void addTotalCreateWitnessCost(long fee) {
    long newValue = getTotalCreateWitnessCost() + fee;
    saveTotalCreateWitnessFee(newValue);
  }

  public void addTotalTransactionCost(long fee) {
    long newValue = getTotalTransactionCost() + fee;
    saveTotalTransactionCost(newValue);
  }

  public void forked(int version, boolean value) {
    String forkKey = FORK_CONTROLLER + version;
    put(forkKey.getBytes(), new BytesCapsule(Boolean.toString(value).getBytes()));
  }

  public void statsByVersion(int version, byte[] stats) {
    String statsKey = FORK_PREFIX + version;
    put(statsKey.getBytes(), new BytesCapsule(stats));
  }

  public byte[] statsByVersion(int version) {
    String statsKey = FORK_PREFIX + version;
    return revokingDB.getUnchecked(statsKey.getBytes());
  }

  public Boolean getForked(int version) {
    String forkKey = FORK_CONTROLLER + version;
    byte[] value = revokingDB.getUnchecked(forkKey.getBytes());
    return value == null ? null : Boolean.valueOf(new String(value));
  }

  /**
   * get allow protobuf number.
   */
  public long getAllowProtoFilterNum() {
    return Optional.ofNullable(getUnchecked(ALLOW_PROTO_FILTER_NUM))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(() -> new IllegalArgumentException("not found allow protobuf number"));
  }

  /**
   * save allow protobuf  number.
   */
  public void saveAllowProtoFilterNum(long num) {
    logger.info("update allow protobuf number = {}", num);
    this.put(ALLOW_PROTO_FILTER_NUM, new BytesCapsule(ByteArray.fromLong(num)));
  }

  public void saveAllowAccountStateRoot(long allowAccountStateRoot) {
    this.put(ALLOW_ACCOUNT_STATE_ROOT,
        new BytesCapsule(ByteArray.fromLong(allowAccountStateRoot)));
  }

  public long getAllowAccountStateRoot() {
    return Optional.ofNullable(getUnchecked(ALLOW_ACCOUNT_STATE_ROOT))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ALLOW_ACCOUNT_STATE_ROOT"));
  }

  public boolean allowAccountStateRoot() {
    return getAllowAccountStateRoot() == 1;
  }

  public long getCurrentCycleNumber() {
    return Optional.ofNullable(getUnchecked(CURRENT_CYCLE_NUMBER))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElse(0L);
  }

  public void saveCurrentCycleNumber(long number) {
    this.put(CURRENT_CYCLE_NUMBER, new BytesCapsule(ByteArray.fromLong(number)));
  }

  public void saveChangeDelegation(long number) {
    this.put(CHANGE_DELEGATION,
        new BytesCapsule(ByteArray.fromLong(number)));
  }

  public long getChangeDelegation() {
    return Optional.ofNullable(getUnchecked(CHANGE_DELEGATION))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(() -> new IllegalArgumentException("not found CHANGE_DELEGATION"));
  }

  public boolean allowChangeDelegation() {
    return getChangeDelegation() == 1;
  }

  public void saveAllowPBFT(long number) {
    this.put(ALLOW_PBFT,
        new BytesCapsule(ByteArray.fromLong(number)));
  }

  public long getAllowPBFT() {
    return Optional.ofNullable(getUnchecked(ALLOW_PBFT))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(() -> new IllegalArgumentException("not found ALLOW_PBFT"));
  }

  public boolean allowPBFT() {
    return getAllowPBFT() == 1;
  }

  public long getMaxFeeLimit() {
    return Optional.ofNullable(getUnchecked(MAX_FEE_LIMIT))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElseThrow(
                    () -> new IllegalArgumentException("not found MAX_FEE_LIMIT"));
  }

  public void saveMaxFeeLimit(long maxFeeLimit) {
    this.put(MAX_FEE_LIMIT,
            new BytesCapsule(ByteArray.fromLong(maxFeeLimit)));
  }

  public long getBurnVsAmount() {
    return Optional.ofNullable(getUnchecked(BURN_VS_AMOUNT))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(() -> new IllegalArgumentException("not found BURN_VS_AMOUNT"));
  }
  public void burnVs(long amount) {
    if (amount <= 0) {
      return;
    }
    amount += getBurnVsAmount();
    saveBurnVs(amount);
  }
  private void saveBurnVs(long amount) {
    this.put(BURN_VS_AMOUNT, new BytesCapsule(ByteArray.fromLong(amount)));
  }
  public boolean supportBlackHoleOptimization() {
    return getAllowBlackHoleOptimization() == 1L;
  }
  public void saveAllowBlackHoleOptimization(long value) {
    this.put(ALLOW_BLACKHOLE_OPTIMIZATION, new BytesCapsule(ByteArray.fromLong(value)));
  }
  public long getAllowBlackHoleOptimization() {
    return Optional.ofNullable(getUnchecked(ALLOW_BLACKHOLE_OPTIMIZATION))
        .map(BytesCapsule::getData)
        .map(ByteArray::toLong)
        .orElseThrow(
            () -> new IllegalArgumentException("not found ALLOW_BLACKHOLE_OPTIMIZATION"));
  }

  public void addTotalSpreadMintWeight(long amount) {
      long totalSpreadMintWeight = getTotalSpreadMintWeight();
      totalSpreadMintWeight += amount;
      saveTotalSpreadMintWeight(totalSpreadMintWeight);
    }

  public void saveTotalSpreadMintWeight(long totalSpreadMintWeight) {
    this.put(DynamicResourceProperties.TOTAL_SPREAD_MINT_WEIGHT,
            new BytesCapsule(ByteArray.fromLong(totalSpreadMintWeight)));
  }

  public long getTotalSpreadMintWeight() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.TOTAL_SPREAD_MINT_WEIGHT))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElse(0L);
  }

  public long getSpreadMintPayPerBlock() {
    return Optional.ofNullable(getUnchecked(SPREAD_MINT_PAY_PER_BLOCK))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElse(256000L);
  }

  public long getSpreadMintPayPerBlockInflation() {
    long weight = getSpreadMintPayPerBlock();
    return (long) (weight * (getInflationRate() * 1.0 / 120000 + 1));
  }

  public boolean supportSpreadMint() {
    return getAllowSpreadMintLevelProp() == 1L;
  }

  public void saveSpreadMintLevelProp(String value) {
    this.put(SPREAD_MINT_LEVEL_PROP, new BytesCapsule(ByteArray.fromString(value)));
  }

  public String getSpreadMintLevelProp() {
    return Optional.ofNullable(getUnchecked(SPREAD_MINT_LEVEL_PROP))
            .map(BytesCapsule::getData)
            .map(ByteArray::toStr)
            .orElseThrow(
                    () -> new IllegalArgumentException("not found SPREAD_LEVEL_PROP"));
  }

  public void saveAllowSpreadMintLevelProp(long value) {
    this.put(ALLOW_SPREAD_MINT_LEVEL_PROP, new BytesCapsule(ByteArray.fromLong(value)));
  }

  public long getAllowSpreadMintLevelProp() {
    return Optional.ofNullable(getUnchecked(ALLOW_SPREAD_MINT_LEVEL_PROP))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElseThrow(
                    () -> new IllegalArgumentException("not found ALLOW_SPREAD_MINT_LEVEL_PROP"));
  }

  public void saveVoteFreezeStageLevel1(long value) {
    this.put(VOTE_FREEZE_STAGE_LEVEL1, new BytesCapsule(ByteArray.fromLong(value)));
  }
  public long getVoteFreezeStageLevel1() {
    return Optional.ofNullable(getUnchecked(VOTE_FREEZE_STAGE_LEVEL1))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElseThrow(
                    () -> new IllegalArgumentException("not found VOTE_FREEZE_STAGE_FIRST"));
  }

  public void saveVoteFreezeStageLevel2(long value) {
    this.put(VOTE_FREEZE_STAGE_LEVEL2, new BytesCapsule(ByteArray.fromLong(value)));
  }
  public long getVoteFreezeStageLevel2() {
    return Optional.ofNullable(getUnchecked(VOTE_FREEZE_STAGE_LEVEL2))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElseThrow(
                    () -> new IllegalArgumentException("not found VOTE_FREEZE_STAGE_SECOND"));
  }

  public void saveVoteFreezeStageLevel3(long value) {
    this.put(VOTE_FREEZE_STAGE_LEVEL3, new BytesCapsule(ByteArray.fromLong(value)));
  }
  public long getVoteFreezeStageLevel3() {
    return Optional.ofNullable(getUnchecked(VOTE_FREEZE_STAGE_LEVEL3))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElseThrow(
                    () -> new IllegalArgumentException("not found VOTE_FREEZE_STAGE_THREE"));
  }

  public void saveVoteFreezePercentLevel1(int value) {
    this.put(VOTE_FREEZE_PERCENT_LEVEL1, new BytesCapsule(ByteArray.fromInt(value)));
  }
  public int getVoteFreezePercentLevel1() {
    return Optional.ofNullable(getUnchecked(VOTE_FREEZE_PERCENT_LEVEL1))
            .map(BytesCapsule::getData)
            .map(ByteArray::toInt)
            .orElseThrow(
                    () -> new IllegalArgumentException("not found VOTE_FREEZE_PERCENT_FIRST"));
  }

  public void saveVoteFreezePercentLevel2(int value) {
    this.put(VOTE_FREEZE_PERCENT_LEVEL2, new BytesCapsule(ByteArray.fromInt(value)));
  }
  public int getVoteFreezePercentLevel2() {
    return Optional.ofNullable(getUnchecked(VOTE_FREEZE_PERCENT_LEVEL2))
            .map(BytesCapsule::getData)
            .map(ByteArray::toInt)
            .orElseThrow(
                    () -> new IllegalArgumentException("not found VOTE_FREEZE_PERCENT_SECOND"));
  }

  public void saveVoteFreezePercentLevel3(int value) {
    this.put(VOTE_FREEZE_PERCENT_LEVEL3, new BytesCapsule(ByteArray.fromInt(value)));
  }
  public int getVoteFreezePercentLevel3() {
    return Optional.ofNullable(getUnchecked(VOTE_FREEZE_PERCENT_LEVEL3))
            .map(BytesCapsule::getData)
            .map(ByteArray::toInt)
            .orElseThrow(
                    () -> new IllegalArgumentException("not found VOTE_FREEZE_PERCENT_THREE"));
  }

  public void saveSrFreezeLowest(long value) {
    this.put(SR_FREEZE_LOWEST, new BytesCapsule(ByteArray.fromLong(value)));
  }
  public long getSrFreezeLowest() {
    return Optional.ofNullable(getUnchecked(SR_FREEZE_LOWEST))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElseThrow(
                    () -> new IllegalArgumentException("not found SR_FREEZE_LOWEST"));
  }

  public void saveSrFreezeLowestPercent(int value) {
    this.put(SR_FREEZE_LOWEST_PERCENT, new BytesCapsule(ByteArray.fromInt(value)));
  }
  public int getSrFreezeLowestPercent() {
    return Optional.ofNullable(getUnchecked(SR_FREEZE_LOWEST_PERCENT))
            .map(BytesCapsule::getData)
            .map(ByteArray::toInt)
            .orElseThrow(
                    () -> new IllegalArgumentException("not found SR_FREEZE_LOWEST_PERCENT"));
  }

  public void saveSpreadMintPayPerBlock(long value) {
    this.put(SPREAD_MINT_PAY_PER_BLOCK, new BytesCapsule(ByteArray.fromLong(value)));
  }

  public void saveEconomyCycle(long value) {
    this.put(ECONOMY_CYCLE,
            new BytesCapsule(ByteArray.fromLong(value)));
  }

  public long getEconomyCycle() {
    return Optional.ofNullable(getUnchecked(ECONOMY_CYCLE))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElse(120L);
  }

  public void saveEffectEconomyCycle(long effectEconomyCycle) {
    this.put(EFFECT_ECONOMY_CYCLE,
            new BytesCapsule(ByteArray.fromLong(effectEconomyCycle)));
  }

  public long getEffectEconomyCycle() {
    return Optional.ofNullable(getUnchecked(EFFECT_ECONOMY_CYCLE))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElse(120L);
  }

  public void saveAllowEthereumCompatibleTransaction(long allowMetamaskSendRawTransaction) {
    this.put(DynamicResourceProperties.ALLOW_ETHEREUM_COMPATIBLE_TRANSACTION,
            new BytesCapsule(ByteArray.fromLong(allowMetamaskSendRawTransaction)));
  }

  public long getAllowEthereumCompatibleTransaction() {
    return Optional.ofNullable(getUnchecked(DynamicResourceProperties.ALLOW_ETHEREUM_COMPATIBLE_TRANSACTION))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElse(1L);
  }

  private static class DynamicResourceProperties {

    private static final byte[] ONE_DAY_PHOTON_LIMIT = "ONE_DAY_PHOTON_LIMIT".getBytes();
    //public free photon
    private static final byte[] PUBLIC_PHOTON_USAGE = "PUBLIC_PHOTON_USAGE".getBytes();
    //fixed
    private static final byte[] PUBLIC_PHOTON_LIMIT = "PUBLIC_PHOTON_LIMIT".getBytes();
    private static final byte[] PUBLIC_PHOTON_TIME = "PUBLIC_PHOTON_TIME".getBytes();
    private static final byte[] FREE_PHOTON_LIMIT = "FREE_PHOTON_LIMIT".getBytes();
    private static final byte[] TOTAL_PHOTON_WEIGHT = "TOTAL_PHOTON_WEIGHT".getBytes();
    //ONE_DAY_PHOTON_LIMIT - PUBLIC_PHOTON_LIMIT，current TOTAL_PHOTON_LIMIT
    private static final byte[] TOTAL_PHOTON_LIMIT = "TOTAL_PHOTON_LIMIT".getBytes();
    private static final byte[] TOTAL_ENTROPY_TARGET_LIMIT = "TOTAL_ENTROPY_TARGET_LIMIT".getBytes();
    private static final byte[] TOTAL_ENTROPY_CURRENT_LIMIT = "TOTAL_ENTROPY_CURRENT_LIMIT"
        .getBytes();
    private static final byte[] TOTAL_ENTROPY_AVERAGE_USAGE = "TOTAL_ENTROPY_AVERAGE_USAGE"
        .getBytes();
    private static final byte[] TOTAL_ENTROPY_AVERAGE_TIME = "TOTAL_ENTROPY_AVERAGE_TIME".getBytes();
    private static final byte[] TOTAL_ENTROPY_WEIGHT = "TOTAL_ENTROPY_WEIGHT".getBytes();
    private static final byte[] TOTAL_ENTROPY_LIMIT = "TOTAL_ENTROPY_LIMIT".getBytes();
    private static final byte[] BLOCK_ENTROPY_USAGE = "BLOCK_ENTROPY_USAGE".getBytes();

    private static final byte[] TOTAL_SRGUARANTEE_WEIGHT = "TOTAL_SRGUARANTEE_WEIGHT".getBytes();
    private static final byte[] TOTAL_ASSETS = "TOTAL_ASSETS".getBytes();
    private static final byte[] PLEDGE_RATE = "PLEDGE_RATE".getBytes();
    private static final byte[] INFLATION_RATE = "INFLATION_RATE".getBytes();
    private static final byte[] PLEDGE_RATE_THRESHOLD = "PLEDGE_RATE_THRESHOLD".getBytes();
    private static final byte[] SPREAD_FREEZE_PERIOD_LIMIT = "SPREAD_FREEZE_PERIOD_LIMIT".getBytes();
    private static final byte[] ALLOW_METAMASK_SENDRAWTRANSACTION = "ALLOW_METAMASK_SENDRAWTRANSACTION".getBytes();
    private static final byte[] LOW_INFLATION_RATE = "LOW_INFLATION_RATE".getBytes();
    private static final byte[] HIGH_INFLATION_RATE = "HIGH_INFLATION_RATE".getBytes();
    private static final byte[] GALAXY_INITIAL_AMOUNT = "GALAXY_INITIAL_AMOUNT".getBytes();
    private static final byte[] LATEST_ECONOMY_END_CYCLE = "LATEST_ECONOMY_END_CYCLE".getBytes();
    private static final byte[] AVALON_INITIAL_AMOUNT = "AVALON_INITIAL_AMOUNT".getBytes();
    private static final byte[] ADAPTIVE_RESOURCE_LIMIT_MULTIPLIER =
        "ADAPTIVE_RESOURCE_LIMIT_MULTIPLIER"
            .getBytes();
    private static final byte[] ADAPTIVE_RESOURCE_LIMIT_TARGET_RATIO =
        "ADAPTIVE_RESOURCE_LIMIT_TARGET_RATIO"
            .getBytes();
    private static final byte[] TOTAL_SPREAD_MINT_WEIGHT = "TOTAL_SPREAD_MINT_WEIGHT".getBytes();
    public static final byte[] ALLOW_ETHEREUM_COMPATIBLE_TRANSACTION = "ALLOW_ETHEREUM_COMPATIBLE_TRANSACTION".getBytes();
  }

}
