package org.vision.core.capsule;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.vision.common.utils.Commons;
import org.vision.common.utils.ForkController;
import org.vision.common.parameter.CommonParameter;
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

  public static boolean checkForEnergyLimit(DynamicPropertiesStore ds) {
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

  public void addNetFee(long netFee) {
    this.receipt = this.receipt.toBuilder().setNetFee(getNetFee() + netFee).build();
  }

  public long getEntropyUsage() {
    return this.receipt.getEntropyUsage();
  }

  public void setEnergyUsage(long energyUsage) {
    this.receipt = this.receipt.toBuilder().setEntropyUsage(energyUsage).build();
  }

  public long getEntropyFee() {
    return this.receipt.getEntropyFee();
  }

  public void setEnergyFee(long energyFee) {
    this.receipt = this.receipt.toBuilder().setEntropyFee(energyFee).build();
  }

  public long getOriginEntropyUsage() {
    return this.receipt.getOriginEntropyUsage();
  }

  public void setOriginEnergyUsage(long energyUsage) {
    this.receipt = this.receipt.toBuilder().setOriginEntropyUsage(energyUsage).build();
  }

  public long getEntropyUsageTotal() {
    return this.receipt.getEntropyUsageTotal();
  }

  public void setEnergyUsageTotal(long energyUsage) {
    this.receipt = this.receipt.toBuilder().setEntropyUsageTotal(energyUsage).build();
  }

  public long getNetUsage() {
    return this.receipt.getNetUsage();
  }

  public void setNetUsage(long netUsage) {
    this.receipt = this.receipt.toBuilder().setNetUsage(netUsage).build();
  }

  public long getNetFee() {
    return this.receipt.getNetFee();
  }

  public void setNetFee(long netFee) {
    this.receipt = this.receipt.toBuilder().setNetFee(netFee).build();
  }

  /**
   * payEnergyBill pay receipt energy bill by energy processor.
   */
  public void payEnergyBill(DynamicPropertiesStore dynamicPropertiesStore,
                            AccountStore accountStore, ForkController forkController, AccountCapsule origin,
                            AccountCapsule caller,
                            long percent, long originEnergyLimit, EntropyProcessor entropyProcessor, long now)
      throws BalanceInsufficientException {
    if (receipt.getEntropyUsageTotal() <= 0) {
      return;
    }

    if (Objects.isNull(origin) && dynamicPropertiesStore.getAllowVvmConstantinople() == 1) {
      payEnergyBill(dynamicPropertiesStore, accountStore, forkController, caller,
          receipt.getEntropyUsageTotal(), entropyProcessor, now);
      return;
    }

    if (caller.getAddress().equals(origin.getAddress())) {
      payEnergyBill(dynamicPropertiesStore, accountStore, forkController, caller,
          receipt.getEntropyUsageTotal(), entropyProcessor, now);
    } else {
      long originUsage = Math.multiplyExact(receipt.getEntropyUsageTotal(), percent) / 100;
      originUsage = getOriginUsage(dynamicPropertiesStore, origin, originEnergyLimit,
              entropyProcessor,
          originUsage);

      long callerUsage = receipt.getEntropyUsageTotal() - originUsage;
      entropyProcessor.useEnergy(origin, originUsage, now);
      this.setOriginEnergyUsage(originUsage);
      payEnergyBill(dynamicPropertiesStore, accountStore, forkController,
          caller, callerUsage, entropyProcessor, now);
    }
  }

  private long getOriginUsage(DynamicPropertiesStore dynamicPropertiesStore, AccountCapsule origin,
                              long originEnergyLimit,
                              EntropyProcessor entropyProcessor, long originUsage) {

    if (checkForEnergyLimit(dynamicPropertiesStore)) {
      return Math.min(originUsage,
          Math.min(entropyProcessor.getAccountLeftEnergyFromFreeze(origin), originEnergyLimit));
    }
    return Math.min(originUsage, entropyProcessor.getAccountLeftEnergyFromFreeze(origin));
  }

  private void payEnergyBill(
      DynamicPropertiesStore dynamicPropertiesStore, AccountStore accountStore,
      ForkController forkController,
      AccountCapsule account,
      long usage,
      EntropyProcessor entropyProcessor,
      long now) throws BalanceInsufficientException {
    long accountEnergyLeft = entropyProcessor.getAccountLeftEnergyFromFreeze(account);
    if (accountEnergyLeft >= usage) {
      entropyProcessor.useEnergy(account, usage, now);
      this.setEnergyUsage(usage);
    } else {
      entropyProcessor.useEnergy(account, accountEnergyLeft, now);

      if (forkController.pass(ForkBlockVersionEnum.VERSION_3_6_5) &&
          dynamicPropertiesStore.getAllowAdaptiveEntropy() == 1) {
        long blockEnergyUsage =
            dynamicPropertiesStore.getBlockEntropyUsage() + (usage - accountEnergyLeft);
        dynamicPropertiesStore.saveBlockEntropyUsage(blockEnergyUsage);
      }

      long vdtPerEnergy = Constant.VDT_PER_ENTROPY;
      long dynamicEnergyFee = dynamicPropertiesStore.getEntropyFee();
      if (dynamicEnergyFee > 0) {
        vdtPerEnergy = dynamicEnergyFee;
      }
      long energyFee =
          (usage - accountEnergyLeft) * vdtPerEnergy;
      this.setEnergyUsage(accountEnergyLeft);
      this.setEnergyFee(energyFee);
      long balance = account.getBalance();
      if (balance < energyFee) {
        throw new BalanceInsufficientException(
            StringUtil.createReadableString(account.createDbKey()) + " insufficient balance");
      }
      account.setBalance(balance - energyFee);

      //send to blackHole
      Commons.adjustBalance(accountStore, accountStore.getSingularity().getAddress().toByteArray(),
          energyFee);
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
