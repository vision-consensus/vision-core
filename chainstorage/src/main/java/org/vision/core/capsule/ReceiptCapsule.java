package org.vision.core.capsule;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.Commons;
import org.vision.common.utils.ForkController;
import org.vision.common.utils.Sha256Hash;
import org.vision.common.utils.StringUtil;
import org.vision.core.Constant;
import org.vision.core.config.Parameter.ForkBlockVersionEnum;
import org.vision.core.db.EntropyProcessor;
import org.vision.core.exception.BalanceInsufficientException;
import org.vision.core.store.AccountStore;
import org.vision.core.store.DynamicPropertiesStore;
import org.vision.protos.Protocol.ResourceReceipt;
import org.vision.protos.Protocol.Transaction.Result.contractResult;

public class ReceiptCapsule {

  private ResourceReceipt receipt;
  @Getter
  @Setter
  private long multiSignFee;

  private Sha256Hash receiptAddress;

  public ReceiptCapsule(ResourceReceipt data, Sha256Hash receiptAddress) {
    this.receipt = data;
    this.receiptAddress = receiptAddress;
  }

  public ReceiptCapsule(Sha256Hash receiptAddress) {
    this.receipt = ResourceReceipt.newBuilder().build();
    this.receiptAddress = receiptAddress;
  }

  public static ResourceReceipt copyReceipt(ReceiptCapsule origin) {
    return origin.getReceipt().toBuilder().build();
  }

  public static boolean checkForEntropyLimit(DynamicPropertiesStore ds) {
    long blockNum = ds.getLatestBlockHeaderNumber();
    return blockNum >= CommonParameter.getInstance()
        .getBlockNumForEntropyLimit();
  }

  public ResourceReceipt getReceipt() {
    return this.receipt;
  }

  public void setReceipt(ResourceReceipt receipt) {
    this.receipt = receipt;
  }

  public Sha256Hash getReceiptAddress() {
    return this.receiptAddress;
  }

  public void addPhotonFee(long photonFee) {
    this.receipt = this.receipt.toBuilder().setPhotonFee(getPhotonFee() + photonFee).build();
  }

  public long getEntropyUsage() {
    return this.receipt.getEntropyUsage();
  }

  public void setEntropyUsage(long entropyUsage) {
    this.receipt = this.receipt.toBuilder().setEntropyUsage(entropyUsage).build();
  }

  public long getEntropyFee() {
    return this.receipt.getEntropyFee();
  }

  public void setEntropyFee(long entropyFee) {
    this.receipt = this.receipt.toBuilder().setEntropyFee(entropyFee).build();
  }

  public long getOriginEntropyUsage() {
    return this.receipt.getOriginEntropyUsage();
  }

  public void setOriginEntropyUsage(long entropyUsage) {
    this.receipt = this.receipt.toBuilder().setOriginEntropyUsage(entropyUsage).build();
  }

  public long getEntropyUsageTotal() {
    return this.receipt.getEntropyUsageTotal();
  }

  public void setEntropyUsageTotal(long entropyUsage) {
    this.receipt = this.receipt.toBuilder().setEntropyUsageTotal(entropyUsage).build();
  }

  public long getPhotonUsage() {
    return this.receipt.getPhotonUsage();
  }

  public void setPhotonUsage(long photonUsage) {
    this.receipt = this.receipt.toBuilder().setPhotonUsage(photonUsage).build();
  }

  public long getPhotonFee() {
    return this.receipt.getPhotonFee();
  }

  public void setPhotonFee(long photonFee) {
    this.receipt = this.receipt.toBuilder().setPhotonFee(photonFee).build();
  }

  /**
   * payEntropyBill pay receipt entropy bill by entropy processor.
   */
  public void payEntropyBill(DynamicPropertiesStore dynamicPropertiesStore,
                             AccountStore accountStore, ForkController forkController, AccountCapsule origin,
                             AccountCapsule caller,
                             long percent, long originEntropyLimit, EntropyProcessor entropyProcessor, long now)
      throws BalanceInsufficientException {
    if (receipt.getEntropyUsageTotal() <= 0) {
      return;
    }

    if (Objects.isNull(origin) && dynamicPropertiesStore.getAllowVvmConstantinople() == 1) {
      payEntropyBill(dynamicPropertiesStore, accountStore, forkController, caller,
          receipt.getEntropyUsageTotal(), receipt.getResult(), entropyProcessor, now);
      return;
    }

    if (caller.getAddress().equals(origin.getAddress())) {
      payEntropyBill(dynamicPropertiesStore, accountStore, forkController, caller,
          receipt.getEntropyUsageTotal(), receipt.getResult(), entropyProcessor, now);
    } else {
      long originUsage = Math.multiplyExact(receipt.getEntropyUsageTotal(), percent) / 100;
      originUsage = getOriginUsage(dynamicPropertiesStore, origin, originEntropyLimit,
              entropyProcessor,
          originUsage);

      long callerUsage = receipt.getEntropyUsageTotal() - originUsage;
      entropyProcessor.useEntropy(origin, originUsage, now);
      this.setOriginEntropyUsage(originUsage);
      payEntropyBill(dynamicPropertiesStore, accountStore, forkController,
          caller, callerUsage, receipt.getResult(), entropyProcessor, now);
    }
  }

  private long getOriginUsage(DynamicPropertiesStore dynamicPropertiesStore, AccountCapsule origin,
                              long originEntropyLimit,
                              EntropyProcessor entropyProcessor, long originUsage) {

    if (checkForEntropyLimit(dynamicPropertiesStore)) {
      return Math.min(originUsage,
          Math.min(entropyProcessor.getAccountLeftEntropyFromFreeze(origin), originEntropyLimit));
    }
    return Math.min(originUsage, entropyProcessor.getAccountLeftEntropyFromFreeze(origin));
  }

  private void payEntropyBill(
      DynamicPropertiesStore dynamicPropertiesStore, AccountStore accountStore,
      ForkController forkController,
      AccountCapsule account,
      long usage,
      contractResult contractResult,
      EntropyProcessor entropyProcessor,
      long now) throws BalanceInsufficientException {
    long accountEntropyLeft = entropyProcessor.getAccountLeftEntropyFromFreeze(account);
    if (accountEntropyLeft >= usage) {
      entropyProcessor.useEntropy(account, usage, now);
      this.setEntropyUsage(usage);
    } else {
      entropyProcessor.useEntropy(account, accountEntropyLeft, now);

      if (dynamicPropertiesStore.getAllowAdaptiveEntropy() == 1) {
        long blockEntropyUsage =
            dynamicPropertiesStore.getBlockEntropyUsage() + (usage - accountEntropyLeft);
        dynamicPropertiesStore.saveBlockEntropyUsage(blockEntropyUsage);
      }

      long vdtPerEntropy = Constant.VDT_PER_ENTROPY;
      long dynamicEntropyFee = dynamicPropertiesStore.getEntropyFee();
      if (dynamicEntropyFee > 0) {
        vdtPerEntropy = dynamicEntropyFee;
      }
      long entropyFee =
          (usage - accountEntropyLeft) * vdtPerEntropy;
      this.setEntropyUsage(accountEntropyLeft);
      this.setEntropyFee(entropyFee);
      long balance = account.getBalance();
      if (balance < entropyFee) {
        throw new BalanceInsufficientException(
            StringUtil.createReadableString(account.createDbKey()) + " insufficient balance");
      }
      account.setBalance(balance - entropyFee);

      if (dynamicPropertiesStore.supportTransactionFeePool() &&
              !contractResult.equals(contractResult.OUT_OF_TIME)) {
        dynamicPropertiesStore.addTransactionFeePool(entropyFee);
      } else if (dynamicPropertiesStore.supportBlackHoleOptimization()) {
        dynamicPropertiesStore.burnVs(entropyFee);
      } else {
        //send to blackHole
        Commons.adjustBalance(accountStore, accountStore.getSingularity(),
                entropyFee);
      }

    }

    accountStore.put(account.getAddress().toByteArray(), account);
  }

  public contractResult getResult() {
    return this.receipt.getResult();
  }

  public void setResult(contractResult success) {
    this.receipt = receipt.toBuilder().setResult(success).build();
  }
}
