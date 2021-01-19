package org.vision.core.db;

import static org.vision.core.config.Parameter.ChainConstant.VS_PRECISION;
import static org.vision.protos.Protocol.Transaction.Contract.ContractType.ShieldedTransferContract;
import static org.vision.protos.Protocol.Transaction.Contract.ContractType.TransferAssetContract;

import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Commons;
import org.vision.core.ChainBaseManager;
import org.vision.core.Constant;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.AssetIssueCapsule;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.core.exception.AccountResourceInsufficientException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.exception.TooBigTransactionResultException;
import org.vision.protos.Protocol.Transaction.Contract;
import org.vision.protos.contract.AssetIssueContractOuterClass.TransferAssetContract;
import org.vision.protos.contract.BalanceContract.TransferContract;

@Slf4j(topic = "DB")
public class PhotonProcessor extends ResourceProcessor {

  private ChainBaseManager chainBaseManager;

  public PhotonProcessor(ChainBaseManager chainBaseManager) {
    super(chainBaseManager.getDynamicPropertiesStore(), chainBaseManager.getAccountStore());
    this.chainBaseManager = chainBaseManager;
  }

  @Override
  public void updateUsage(AccountCapsule accountCapsule) {
    long now = chainBaseManager.getHeadSlot();
    updateUsage(accountCapsule, now);
  }

  private void updateUsage(AccountCapsule accountCapsule, long now) {
    long oldNetUsage = accountCapsule.getPhotonUsage();
    long latestConsumeTime = accountCapsule.getLatestConsumeTime();
    accountCapsule.setPhotonUsage(increase(oldNetUsage, 0, latestConsumeTime, now));
    long oldFreeNetUsage = accountCapsule.getFreePhotonUsage();
    long latestConsumeFreeTime = accountCapsule.getLatestConsumeFreeTime();
    accountCapsule.setFreePhotonUsage(increase(oldFreeNetUsage, 0, latestConsumeFreeTime, now));

    if (chainBaseManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
      Map<String, Long> assetMap = accountCapsule.getAssetMap();
      assetMap.forEach((assetName, balance) -> {
        long oldFreeAssetPhotonUsage = accountCapsule.getFreeAssetPhotonUsage(assetName);
        long latestAssetOperationTime = accountCapsule.getLatestAssetOperationTime(assetName);
        accountCapsule.putFreeAssetPhotonUsage(assetName,
            increase(oldFreeAssetPhotonUsage, 0, latestAssetOperationTime, now));
      });
    }
    Map<String, Long> assetMapV2 = accountCapsule.getAssetMapV2();
    assetMapV2.forEach((assetName, balance) -> {
      long oldFreeAssetPhotonUsage = accountCapsule.getFreeAssetPhotonUsageV2(assetName);
      long latestAssetOperationTime = accountCapsule.getLatestAssetOperationTimeV2(assetName);
      accountCapsule.putFreeAssetPhotonUsageV2(assetName,
          increase(oldFreeAssetPhotonUsage, 0, latestAssetOperationTime, now));
    });
  }

  @Override
  public void consume(TransactionCapsule trx, TransactionTrace trace)
      throws ContractValidateException, AccountResourceInsufficientException,
      TooBigTransactionResultException {
    List<Contract> contracts = trx.getInstance().getRawData().getContractList();
    if (trx.getResultSerializedSize() > Constant.MAX_RESULT_SIZE_IN_TX * contracts.size()) {
      throw new TooBigTransactionResultException();
    }

    long bytesSize;

    if (chainBaseManager.getDynamicPropertiesStore().supportVM()) {
      bytesSize = trx.getInstance().toBuilder().clearRet().build().getSerializedSize();
    } else {
      bytesSize = trx.getSerializedSize();
    }

    for (Contract contract : contracts) {
      if (contract.getType() == ShieldedTransferContract) {
        continue;
      }
      if (chainBaseManager.getDynamicPropertiesStore().supportVM()) {
        bytesSize += Constant.MAX_RESULT_SIZE_IN_TX;
      }

      logger.debug("trxId {}, photon cost: {}", trx.getTransactionId(), bytesSize);
      trace.setPhotonBill(bytesSize, 0);
      byte[] address = TransactionCapsule.getOwner(contract);
      AccountCapsule accountCapsule = chainBaseManager.getAccountStore().get(address);
      if (accountCapsule == null) {
        throw new ContractValidateException("account does not exist");
      }
      long now = chainBaseManager.getHeadSlot();

      if (contractCreateNewAccount(contract)) {
        consumeForCreateNewAccount(accountCapsule, bytesSize, now, trace);
        continue;
      }

      if (contract.getType() == TransferAssetContract && useAssetAccountPhoton(contract,
          accountCapsule, now, bytesSize)) {
        continue;
      }

      if (useAccountPhoton(accountCapsule, bytesSize, now)) {
        continue;
      }

      if (useFreePhoton(accountCapsule, bytesSize, now)) {
        continue;
      }

      if (useTransactionFee(accountCapsule, bytesSize, trace)) {
        continue;
      }

      long fee = chainBaseManager.getDynamicPropertiesStore().getTransactionFee() * bytesSize;
      throw new AccountResourceInsufficientException(
          "Account has insufficient photon[" + bytesSize + "] and balance["
              + fee + "] to create new account");
    }
  }

  private boolean useTransactionFee(AccountCapsule accountCapsule, long bytes,
      TransactionTrace trace) {
    long fee = chainBaseManager.getDynamicPropertiesStore().getTransactionFee() * bytes;
    if (consumeFee(accountCapsule, fee)) {
      trace.setPhotonBill(0, fee);
      chainBaseManager.getDynamicPropertiesStore().addTotalTransactionCost(fee);
      return true;
    } else {
      return false;
    }
  }

  private void consumeForCreateNewAccount(AccountCapsule accountCapsule, long bytes,
      long now, TransactionTrace trace)
      throws AccountResourceInsufficientException {
    boolean ret = consumePhotonForCreateNewAccount(accountCapsule, bytes, now);

    if (!ret) {
      ret = consumeFeeForCreateNewAccount(accountCapsule, trace);
      if (!ret) {
        throw new AccountResourceInsufficientException();
      }
    }
  }

  public boolean consumePhotonForCreateNewAccount(AccountCapsule accountCapsule, long bytes,
                                                  long now) {

    long createNewAccountPhotonRatio = chainBaseManager.getDynamicPropertiesStore()
        .getCreateNewAccountPhotonRate();

    long netUsage = accountCapsule.getPhotonUsage();
    long latestConsumeTime = accountCapsule.getLatestConsumeTime();
    long photonLimit = calculateGlobalPhotonLimit(accountCapsule);

    long newNetUsage = increase(netUsage, 0, latestConsumeTime, now);

    if (bytes * createNewAccountPhotonRatio <= (photonLimit - newNetUsage)) {
      latestConsumeTime = now;
      long latestOperationTime = chainBaseManager.getHeadBlockTimeStamp();
      newNetUsage = increase(newNetUsage, bytes * createNewAccountPhotonRatio,
          latestConsumeTime, now);
      accountCapsule.setLatestConsumeTime(latestConsumeTime);
      accountCapsule.setLatestOperationTime(latestOperationTime);
      accountCapsule.setPhotonUsage(newNetUsage);
      chainBaseManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
      return true;
    }
    return false;
  }

  public boolean consumeFeeForCreateNewAccount(AccountCapsule accountCapsule,
      TransactionTrace trace) {
    long fee = chainBaseManager.getDynamicPropertiesStore().getCreateAccountFee();
    if (consumeFee(accountCapsule, fee)) {
      trace.setPhotonBill(0, fee);
      chainBaseManager.getDynamicPropertiesStore().addTotalCreateAccountCost(fee);
      return true;
    } else {
      return false;
    }
  }

  public boolean contractCreateNewAccount(Contract contract) {
    AccountCapsule toAccount;
    switch (contract.getType()) {
      case AccountCreateContract:
        return true;
      case TransferContract:
        TransferContract transferContract;
        try {
          transferContract = contract.getParameter().unpack(TransferContract.class);
        } catch (Exception ex) {
          throw new RuntimeException(ex.getMessage());
        }
        toAccount =
            chainBaseManager.getAccountStore().get(transferContract.getToAddress().toByteArray());
        return toAccount == null;
      case TransferAssetContract:
        TransferAssetContract transferAssetContract;
        try {
          transferAssetContract = contract.getParameter().unpack(TransferAssetContract.class);
        } catch (Exception ex) {
          throw new RuntimeException(ex.getMessage());
        }
        toAccount = chainBaseManager.getAccountStore()
            .get(transferAssetContract.getToAddress().toByteArray());
        return toAccount == null;
      default:
        return false;
    }
  }


  private boolean useAssetAccountPhoton(Contract contract, AccountCapsule accountCapsule, long now,
                                        long bytes)
      throws ContractValidateException {

    ByteString assetName;
    try {
      assetName = contract.getParameter().unpack(TransferAssetContract.class).getAssetName();
    } catch (Exception ex) {
      throw new RuntimeException(ex.getMessage());
    }

    AssetIssueCapsule assetIssueCapsule;
    AssetIssueCapsule assetIssueCapsuleV2;
    assetIssueCapsule = Commons.getAssetIssueStoreFinal(
        chainBaseManager.getDynamicPropertiesStore(),
        chainBaseManager.getAssetIssueStore(), chainBaseManager.getAssetIssueV2Store())
        .get(assetName.toByteArray());
    if (assetIssueCapsule == null) {
      throw new ContractValidateException("asset does not exist");
    }

    String tokenName = ByteArray.toStr(assetName.toByteArray());
    String tokenID = assetIssueCapsule.getId();
    if (assetIssueCapsule.getOwnerAddress() == accountCapsule.getAddress()) {
      return useAccountPhoton(accountCapsule, bytes, now);
    }

    long publicFreeAssetPhotonLimit = assetIssueCapsule.getPublicFreeAssetPhotonLimit();
    long publicFreeAssetPhotonUsage = assetIssueCapsule.getPublicFreeAssetPhotonUsage();
    long publicLatestFreePhotonTime = assetIssueCapsule.getPublicLatestFreePhotonTime();

    long newPublicFreeAssetPhotonUsage = increase(publicFreeAssetPhotonUsage, 0,
        publicLatestFreePhotonTime, now);

    if (bytes > (publicFreeAssetPhotonLimit - newPublicFreeAssetPhotonUsage)) {
      logger.debug("The " + tokenID + " public free photon is not enough");
      return false;
    }

    long freeAssetPhotonLimit = assetIssueCapsule.getFreeAssetPhotonLimit();

    long freeAssetPhotonUsage;
    long latestAssetOperationTime;
    if (chainBaseManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
      freeAssetPhotonUsage = accountCapsule
          .getFreeAssetPhotonUsage(tokenName);
      latestAssetOperationTime = accountCapsule
          .getLatestAssetOperationTime(tokenName);
    } else {
      freeAssetPhotonUsage = accountCapsule.getFreeAssetPhotonUsageV2(tokenID);
      latestAssetOperationTime = accountCapsule.getLatestAssetOperationTimeV2(tokenID);
    }

    long newFreeAssetPhotonUsage = increase(freeAssetPhotonUsage, 0,
        latestAssetOperationTime, now);

    if (bytes > (freeAssetPhotonLimit - newFreeAssetPhotonUsage)) {
      logger.debug("The " + tokenID + " free photon is not enough");
      return false;
    }

    AccountCapsule issuerAccountCapsule = chainBaseManager.getAccountStore()
        .get(assetIssueCapsule.getOwnerAddress().toByteArray());

    long issuerPhotonUsage = issuerAccountCapsule.getPhotonUsage();
    long latestConsumeTime = issuerAccountCapsule.getLatestConsumeTime();
    long issuerPhotonLimit = calculateGlobalPhotonLimit(issuerAccountCapsule);

    long newIssuerPhotonUsage = increase(issuerPhotonUsage, 0, latestConsumeTime, now);

    if (bytes > (issuerPhotonLimit - newIssuerPhotonUsage)) {
      logger.debug("The " + tokenID + " issuer's photon is not enough");
      return false;
    }

    latestConsumeTime = now;
    latestAssetOperationTime = now;
    publicLatestFreePhotonTime = now;
    long latestOperationTime = chainBaseManager.getHeadBlockTimeStamp();

    newIssuerPhotonUsage = increase(newIssuerPhotonUsage, bytes, latestConsumeTime, now);
    newFreeAssetPhotonUsage = increase(newFreeAssetPhotonUsage,
        bytes, latestAssetOperationTime, now);
    newPublicFreeAssetPhotonUsage = increase(newPublicFreeAssetPhotonUsage, bytes,
        publicLatestFreePhotonTime, now);

    issuerAccountCapsule.setPhotonUsage(newIssuerPhotonUsage);
    issuerAccountCapsule.setLatestConsumeTime(latestConsumeTime);

    assetIssueCapsule.setPublicFreeAssetPhotonUsage(newPublicFreeAssetPhotonUsage);
    assetIssueCapsule.setPublicLatestFreePhotonTime(publicLatestFreePhotonTime);

    accountCapsule.setLatestOperationTime(latestOperationTime);
    if (chainBaseManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
      accountCapsule.putLatestAssetOperationTimeMap(tokenName,
          latestAssetOperationTime);
      accountCapsule.putFreeAssetPhotonUsage(tokenName, newFreeAssetPhotonUsage);
      accountCapsule.putLatestAssetOperationTimeMapV2(tokenID,
          latestAssetOperationTime);
      accountCapsule.putFreeAssetPhotonUsageV2(tokenID, newFreeAssetPhotonUsage);

      chainBaseManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);

      assetIssueCapsuleV2 =
          chainBaseManager.getAssetIssueV2Store().get(assetIssueCapsule.createDbV2Key());
      assetIssueCapsuleV2.setPublicFreeAssetPhotonUsage(newPublicFreeAssetPhotonUsage);
      assetIssueCapsuleV2.setPublicLatestFreePhotonTime(publicLatestFreePhotonTime);
      chainBaseManager.getAssetIssueV2Store()
          .put(assetIssueCapsuleV2.createDbV2Key(), assetIssueCapsuleV2);
    } else {
      accountCapsule.putLatestAssetOperationTimeMapV2(tokenID,
          latestAssetOperationTime);
      accountCapsule.putFreeAssetPhotonUsageV2(tokenID, newFreeAssetPhotonUsage);
      chainBaseManager.getAssetIssueV2Store()
          .put(assetIssueCapsule.createDbV2Key(), assetIssueCapsule);
    }

    chainBaseManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    chainBaseManager.getAccountStore().put(issuerAccountCapsule.createDbKey(),
        issuerAccountCapsule);

    return true;

  }

  public long calculateGlobalPhotonLimit(AccountCapsule accountCapsule) {
    long frozeBalance = accountCapsule.getAllFrozenBalanceForPhoton();
    if (frozeBalance < VS_PRECISION) {
      return 0;
    }
    long photonWeight = frozeBalance / VS_PRECISION;
    long totalPhotonLimit = chainBaseManager.getDynamicPropertiesStore().getTotalPhotonLimit();
    long totalPhotonWeight = chainBaseManager.getDynamicPropertiesStore().getTotalPhotonWeight();
    if (totalPhotonWeight == 0) {
      return 0;
    }
    return (long) (photonWeight * ((double) totalPhotonLimit / totalPhotonWeight));
  }

  private boolean useAccountPhoton(AccountCapsule accountCapsule, long bytes, long now) {

    long netUsage = accountCapsule.getPhotonUsage();
    long latestConsumeTime = accountCapsule.getLatestConsumeTime();
    long photonLimit = calculateGlobalPhotonLimit(accountCapsule);

    long newNetUsage = increase(netUsage, 0, latestConsumeTime, now);

    if (bytes > (photonLimit - newNetUsage)) {
      logger.debug("net usage is running out, now use free net usage");
      return false;
    }

    latestConsumeTime = now;
    long latestOperationTime = chainBaseManager.getHeadBlockTimeStamp();
    newNetUsage = increase(newNetUsage, bytes, latestConsumeTime, now);
    accountCapsule.setPhotonUsage(newNetUsage);
    accountCapsule.setLatestOperationTime(latestOperationTime);
    accountCapsule.setLatestConsumeTime(latestConsumeTime);

    chainBaseManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    return true;
  }

  private boolean useFreePhoton(AccountCapsule accountCapsule, long bytes, long now) {

    long freePhotonLimit = chainBaseManager.getDynamicPropertiesStore().getFreePhotonLimit();
    long freePhotonUsage = accountCapsule.getFreePhotonUsage();
    long latestConsumeFreeTime = accountCapsule.getLatestConsumeFreeTime();
    long newFreeNetUsage = increase(freePhotonUsage, 0, latestConsumeFreeTime, now);

    if (bytes > (freePhotonLimit - newFreeNetUsage)) {
      logger.debug("free net usage is running out");
      return false;
    }

    long publicPhotonLimit = chainBaseManager.getDynamicPropertiesStore().getPublicPhotonLimit();
    long publicPhotonUsage = chainBaseManager.getDynamicPropertiesStore().getPublicPhotonUsage();
    long publicPhotonTime = chainBaseManager.getDynamicPropertiesStore().getPublicPhotonTime();

    long newPublicNetUsage = increase(publicPhotonUsage, 0, publicPhotonTime, now);

    if (bytes > (publicPhotonLimit - newPublicNetUsage)) {
      logger.debug("free public net usage is running out");
      return false;
    }

    latestConsumeFreeTime = now;
    long latestOperationTime = chainBaseManager.getHeadBlockTimeStamp();
    publicPhotonTime = now;
    newFreeNetUsage = increase(newFreeNetUsage, bytes, latestConsumeFreeTime, now);
    newPublicNetUsage = increase(newPublicNetUsage, bytes, publicPhotonTime, now);
    accountCapsule.setFreePhotonUsage(newFreeNetUsage);
    accountCapsule.setLatestConsumeFreeTime(latestConsumeFreeTime);
    accountCapsule.setLatestOperationTime(latestOperationTime);

    chainBaseManager.getDynamicPropertiesStore().savePublicNetUsage(newPublicNetUsage);
    chainBaseManager.getDynamicPropertiesStore().savePublicPhotonTime(publicPhotonTime);
    chainBaseManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    return true;

  }

}


