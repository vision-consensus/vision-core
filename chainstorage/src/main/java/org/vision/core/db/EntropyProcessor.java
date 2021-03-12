package org.vision.core.db;

import static java.lang.Long.max;
import static org.vision.core.config.Parameter.ChainConstant.BLOCK_PRODUCED_INTERVAL;
import static org.vision.core.config.Parameter.ChainConstant.VS_PRECISION;

import lombok.extern.slf4j.Slf4j;
import org.vision.common.parameter.CommonParameter;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.core.config.Parameter.AdaptiveResourceLimitConstants;
import org.vision.core.exception.AccountResourceInsufficientException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.store.AccountStore;
import org.vision.core.store.DynamicPropertiesStore;
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

    long oldEntropyUsage = accountResource.getEntropyUsage();
    long latestConsumeTime = accountResource.getLatestConsumeTimeForEntropy();

    accountCapsule.setEntropyUsage(increase(oldEntropyUsage, 0, latestConsumeTime, now));
  }

  public void updateTotalEntropyAverageUsage() {
    long now = getHeadSlot();
    long blockEntropyUsage = dynamicPropertiesStore.getBlockEntropyUsage();
    long totalEntropyAverageUsage = dynamicPropertiesStore
        .getTotalEntropyAverageUsage();
    long totalEntropyAverageTime = dynamicPropertiesStore.getTotalEntropyAverageTime();

    long newPublicEntropyAverageUsage = increase(totalEntropyAverageUsage, blockEntropyUsage,
        totalEntropyAverageTime, now, averageWindowSize);

    dynamicPropertiesStore.saveTotalEntropyAverageUsage(newPublicEntropyAverageUsage);
    dynamicPropertiesStore.saveTotalEntropyAverageTime(now);
  }

  public void updateAdaptiveTotalEntropyLimit() {
    long totalEntropyAverageUsage = dynamicPropertiesStore
        .getTotalEntropyAverageUsage();
    long targetTotalEntropyLimit = dynamicPropertiesStore.getTotalEntropyTargetLimit();
    long totalEntropyCurrentLimit = dynamicPropertiesStore
        .getTotalEntropyCurrentLimit();
    long totalEntropyLimit = dynamicPropertiesStore.getTotalEntropyLimit();

    long result;
    if (totalEntropyAverageUsage > targetTotalEntropyLimit) {
      result = totalEntropyCurrentLimit * AdaptiveResourceLimitConstants.CONTRACT_RATE_NUMERATOR
          / AdaptiveResourceLimitConstants.CONTRACT_RATE_DENOMINATOR;
      // logger.info(totalEntropyAverageUsage + ">" + targetTotalEntropyLimit + "\n" + result);
    } else {
      result = totalEntropyCurrentLimit * AdaptiveResourceLimitConstants.EXPAND_RATE_NUMERATOR
          / AdaptiveResourceLimitConstants.EXPAND_RATE_DENOMINATOR;
      // logger.info(totalEntropyAverageUsage + "<" + targetTotalEntropyLimit + "\n" + result);
    }

    result = Math.min(
        Math.max(result, totalEntropyLimit),
        totalEntropyLimit * dynamicPropertiesStore.getAdaptiveResourceLimitMultiplier()
    );

    dynamicPropertiesStore.saveTotalEntropyCurrentLimit(result);
    logger.debug(
        "adjust totalEntropyCurrentLimit, old[" + totalEntropyCurrentLimit + "], new[" + result
            + "]");
  }

  @Override
  public void consume(TransactionCapsule trx,
                      TransactionTrace trace)
      throws ContractValidateException, AccountResourceInsufficientException {
    throw new RuntimeException("Not support");
  }

  public boolean useEntropy(AccountCapsule accountCapsule, long entropy, long now) {

    long entropyUsage = accountCapsule.getEntropyUsage();
    long latestConsumeTime = accountCapsule.getAccountResource().getLatestConsumeTimeForEntropy();
    long entropyLimit = calculateGlobalEntropyLimit(accountCapsule);

    long newEntropyUsage = increase(entropyUsage, 0, latestConsumeTime, now);

    if (entropy > (entropyLimit - newEntropyUsage)) {
      return false;
    }

    latestConsumeTime = now;
    long latestOperationTime = dynamicPropertiesStore.getLatestBlockHeaderTimestamp();
    newEntropyUsage = increase(newEntropyUsage, entropy, latestConsumeTime, now);
    accountCapsule.setEntropyUsage(newEntropyUsage);
    accountCapsule.setLatestOperationTime(latestOperationTime);
    accountCapsule.setLatestConsumeTimeForEntropy(latestConsumeTime);

    accountStore.put(accountCapsule.createDbKey(), accountCapsule);

    if (dynamicPropertiesStore.getAllowAdaptiveEntropy() == 1) {
      long blockEntropyUsage = dynamicPropertiesStore.getBlockEntropyUsage() + entropy;
      dynamicPropertiesStore.saveBlockEntropyUsage(blockEntropyUsage);
    }

    return true;
  }

  public long calculateGlobalEntropyLimit(AccountCapsule accountCapsule) {
    long frozeBalance = accountCapsule.getAllFrozenBalanceForEntropy();
    if (frozeBalance < VS_PRECISION) {
      return 0;
    }

    long entropyWeight = frozeBalance / VS_PRECISION;
    long totalEntropyLimit = dynamicPropertiesStore.getTotalEntropyCurrentLimit();
    long totalEntropyWeight = dynamicPropertiesStore.getTotalEntropyWeight();

    assert totalEntropyWeight > 0;

    return (long) (entropyWeight * ((double) totalEntropyLimit / totalEntropyWeight));
  }

  public long getAccountLeftEntropyFromFreeze(AccountCapsule accountCapsule) {
    long now = getHeadSlot();
    long entropyUsage = accountCapsule.getEntropyUsage();
    long latestConsumeTime = accountCapsule.getAccountResource().getLatestConsumeTimeForEntropy();
    long entropyLimit = calculateGlobalEntropyLimit(accountCapsule);

    long newEntropyUsage = increase(entropyUsage, 0, latestConsumeTime, now);

    return max(entropyLimit - newEntropyUsage, 0); // us
  }

  private long getHeadSlot() {
    return getHeadSlot(dynamicPropertiesStore);
  }


}


