package org.vision.core.db;

import static java.lang.Long.max;
import static org.vision.core.config.Parameter.ChainConstant.BLOCK_PRODUCED_INTERVAL;
import static org.vision.core.config.Parameter.ChainConstant.VS_PRECISION;

import lombok.extern.slf4j.Slf4j;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.core.store.AccountStore;
import org.vision.core.store.DynamicPropertiesStore;
import org.vision.common.parameter.CommonParameter;
import org.vision.core.config.Parameter.AdaptiveResourceLimitConstants;
import org.vision.core.exception.AccountResourceInsufficientException;
import org.vision.core.exception.ContractValidateException;
import org.vision.protos.Protocol.Account.AccountResource;

@Slf4j(topic = "DB")
public class EntropyProcessor extends ResourceProcessor {

  public EntropyProcessor(DynamicPropertiesStore dynamicPropertiesStore, AccountStore accountStore) {
    super(dynamicPropertiesStore, accountStore);
  }

  public static long getHeadSlot(DynamicPropertiesStore dynamicPropertiesStore) {
    return (dynamicPropertiesStore.getLatestBlockHeaderTimestamp() -
        Long.parseLong(CommonParameter.getInstance()
            .getGenesisBlock().getTimestamp()))
        / BLOCK_PRODUCED_INTERVAL;
  }

  @Override
  public void updateUsage(AccountCapsule accountCapsule) {
    long now = getHeadSlot();
    updateUsage(accountCapsule, now);
  }

  private void updateUsage(AccountCapsule accountCapsule, long now) {
    AccountResource accountResource = accountCapsule.getAccountResource();

    long oldEnergyUsage = accountResource.getEntropyUsage();
    long latestConsumeTime = accountResource.getLatestConsumeTimeForEntropy();

    accountCapsule.setEntropyUsage(increase(oldEnergyUsage, 0, latestConsumeTime, now));
  }

  public void updateTotalEnergyAverageUsage() {
    long now = getHeadSlot();
    long blockEnergyUsage = dynamicPropertiesStore.getBlockEntropyUsage();
    long totalEnergyAverageUsage = dynamicPropertiesStore
        .getTotalEntropyAverageUsage();
    long totalEnergyAverageTime = dynamicPropertiesStore.getTotalEntropyAverageTime();

    long newPublicEnergyAverageUsage = increase(totalEnergyAverageUsage, blockEnergyUsage,
        totalEnergyAverageTime, now, averageWindowSize);

    dynamicPropertiesStore.saveTotalEntropyAverageUsage(newPublicEnergyAverageUsage);
    dynamicPropertiesStore.saveTotalEntropyAverageTime(now);
  }

  public void updateAdaptiveTotalEnergyLimit() {
    long totalEnergyAverageUsage = dynamicPropertiesStore
        .getTotalEntropyAverageUsage();
    long targetTotalEnergyLimit = dynamicPropertiesStore.getTotalEntropyTargetLimit();
    long totalEnergyCurrentLimit = dynamicPropertiesStore
        .getTotalEntropyCurrentLimit();
    long totalEnergyLimit = dynamicPropertiesStore.getTotalEntropyLimit();

    long result;
    if (totalEnergyAverageUsage > targetTotalEnergyLimit) {
      result = totalEnergyCurrentLimit * AdaptiveResourceLimitConstants.CONTRACT_RATE_NUMERATOR
          / AdaptiveResourceLimitConstants.CONTRACT_RATE_DENOMINATOR;
      // logger.info(totalEnergyAverageUsage + ">" + targetTotalEnergyLimit + "\n" + result);
    } else {
      result = totalEnergyCurrentLimit * AdaptiveResourceLimitConstants.EXPAND_RATE_NUMERATOR
          / AdaptiveResourceLimitConstants.EXPAND_RATE_DENOMINATOR;
      // logger.info(totalEnergyAverageUsage + "<" + targetTotalEnergyLimit + "\n" + result);
    }

    result = Math.min(
        Math.max(result, totalEnergyLimit),
        totalEnergyLimit * dynamicPropertiesStore.getAdaptiveResourceLimitMultiplier()
    );

    dynamicPropertiesStore.saveTotalEntropyCurrentLimit(result);
    logger.debug(
        "adjust totalEnergyCurrentLimit, old[" + totalEnergyCurrentLimit + "], new[" + result
            + "]");
  }

  @Override
  public void consume(TransactionCapsule trx,
                      TransactionTrace trace)
      throws ContractValidateException, AccountResourceInsufficientException {
    throw new RuntimeException("Not support");
  }

  public boolean useEnergy(AccountCapsule accountCapsule, long energy, long now) {

    long energyUsage = accountCapsule.getEntropyUsage();
    long latestConsumeTime = accountCapsule.getAccountResource().getLatestConsumeTimeForEntropy();
    long energyLimit = calculateGlobalEnergyLimit(accountCapsule);

    long newEnergyUsage = increase(energyUsage, 0, latestConsumeTime, now);

    if (energy > (energyLimit - newEnergyUsage)) {
      return false;
    }

    latestConsumeTime = now;
    long latestOperationTime = dynamicPropertiesStore.getLatestBlockHeaderTimestamp();
    newEnergyUsage = increase(newEnergyUsage, energy, latestConsumeTime, now);
    accountCapsule.setEntropyUsage(newEnergyUsage);
    accountCapsule.setLatestOperationTime(latestOperationTime);
    accountCapsule.setLatestConsumeTimeForEntropy(latestConsumeTime);

    accountStore.put(accountCapsule.createDbKey(), accountCapsule);

    if (dynamicPropertiesStore.getAllowAdaptiveEntropy() == 1) {
      long blockEnergyUsage = dynamicPropertiesStore.getBlockEntropyUsage() + energy;
      dynamicPropertiesStore.saveBlockEntropyUsage(blockEnergyUsage);
    }

    return true;
  }

  public long calculateGlobalEnergyLimit(AccountCapsule accountCapsule) {
    long frozeBalance = accountCapsule.getAllFrozenBalanceForEntropy();
    if (frozeBalance < VS_PRECISION) {
      return 0;
    }

    long energyWeight = frozeBalance / VS_PRECISION;
    long totalEnergyLimit = dynamicPropertiesStore.getTotalEntropyCurrentLimit();
    long totalEnergyWeight = dynamicPropertiesStore.getTotalEntropyWeight();

    assert totalEnergyWeight > 0;

    return (long) (energyWeight * ((double) totalEnergyLimit / totalEnergyWeight));
  }

  public long getAccountLeftEnergyFromFreeze(AccountCapsule accountCapsule) {
    long now = getHeadSlot();
    long energyUsage = accountCapsule.getEntropyUsage();
    long latestConsumeTime = accountCapsule.getAccountResource().getLatestConsumeTimeForEntropy();
    long energyLimit = calculateGlobalEnergyLimit(accountCapsule);

    long newEnergyUsage = increase(energyUsage, 0, latestConsumeTime, now);

    return max(energyLimit - newEnergyUsage, 0); // us
  }

  private long getHeadSlot() {
    return getHeadSlot(dynamicPropertiesStore);
  }


}


