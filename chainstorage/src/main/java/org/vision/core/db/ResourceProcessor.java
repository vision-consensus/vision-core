package org.vision.core.db;

import static org.vision.core.config.Parameter.ChainConstant.BLOCK_PRODUCED_INTERVAL;

import org.vision.common.utils.Commons;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.core.store.AccountStore;
import org.vision.core.store.DynamicPropertiesStore;
import org.vision.core.config.Parameter.AdaptiveResourceLimitConstants;
import org.vision.core.config.Parameter.ChainConstant;
import org.vision.core.exception.AccountResourceInsufficientException;
import org.vision.core.exception.BalanceInsufficientException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.exception.TooBigTransactionResultException;

abstract class ResourceProcessor {

  protected DynamicPropertiesStore dynamicPropertiesStore;
  protected AccountStore accountStore;
  protected long precision;
  protected long windowSize;
  protected long averageWindowSize;

  public ResourceProcessor(DynamicPropertiesStore dynamicPropertiesStore,
      AccountStore accountStore) {
    this.dynamicPropertiesStore = dynamicPropertiesStore;
    this.accountStore = accountStore;
    this.precision = ChainConstant.PRECISION;
    this.windowSize = ChainConstant.WINDOW_SIZE_MS / BLOCK_PRODUCED_INTERVAL;
    this.averageWindowSize =
        AdaptiveResourceLimitConstants.PERIODS_MS / BLOCK_PRODUCED_INTERVAL;
  }

  abstract void updateUsage(AccountCapsule accountCapsule);

  abstract void consume(TransactionCapsule trx, TransactionTrace trace)
      throws ContractValidateException, AccountResourceInsufficientException, TooBigTransactionResultException;

  protected long increase(long lastUsage, long usage, long lastTime, long now) {
    return increase(lastUsage, usage, lastTime, now, windowSize);
  }

  protected long increase(long lastUsage, long usage, long lastTime, long now, long windowSize) {
    long averageLastUsage = divideCeil(lastUsage * precision, windowSize);
    long averageUsage = divideCeil(usage * precision, windowSize);

    if (lastTime != now) {
      assert now > lastTime;
      if (lastTime + windowSize > now) {
        long delta = now - lastTime;
        double decay = (windowSize - delta) / (double) windowSize;
        averageLastUsage = Math.round(averageLastUsage * decay);
      } else {
        averageLastUsage = 0;
      }
    }
    averageLastUsage += averageUsage;
    return getUsage(averageLastUsage, windowSize);
  }

  private long divideCeil(long numerator, long denominator) {
    return (numerator / denominator) + ((numerator % denominator) > 0 ? 1 : 0);
  }

  private long getUsage(long usage, long windowSize) {
    return usage * windowSize / precision;
  }

  protected boolean consumeFee(AccountCapsule accountCapsule, long fee) {
    try {
      long latestOperationTime = dynamicPropertiesStore.getLatestBlockHeaderTimestamp();
      accountCapsule.setLatestOperationTime(latestOperationTime);
      Commons.adjustBalance(accountStore, accountCapsule, -fee);
      Commons.adjustBalance(accountStore, accountStore.getSingularity().createDbKey(), +fee);
      return true;
    } catch (BalanceInsufficientException e) {
      return false;
    }
  }
}
