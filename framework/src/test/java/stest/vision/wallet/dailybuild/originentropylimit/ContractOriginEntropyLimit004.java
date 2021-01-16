package stest.vision.wallet.dailybuild.originentropylimit;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.vision.api.GrpcAPI.AccountResourceMessage;
import org.vision.api.WalletGrpc;
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Utils;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Account;
import org.vision.protos.Protocol.Transaction;
import org.vision.protos.Protocol.TransactionInfo;
import org.vision.protos.contract.SmartContractOuterClass.SmartContract;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractOriginEntropyLimit004 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  byte[] contractAddress = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] dev001Address = ecKey1.getAddress();
  String dev001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] user001Address = ecKey2.getAddress();
  String user001Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(Parameter.CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */
  @BeforeClass(enabled = true)
  public void beforeClass() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);
  }

  private long getAvailableFrozenEntropy(byte[] accountAddress) {
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(accountAddress,
        blockingStubFull);
    long entropyLimit = resourceInfo.getEntropyLimit();
    long entropyUsed = resourceInfo.getEntropyUsed();
    return entropyLimit - entropyUsed;
  }

  private long getUserAvailableEntropy(byte[] userAddress) {
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(userAddress,
        blockingStubFull);
    Account info = PublicMethed.queryAccount(userAddress, blockingStubFull);
    long balance = info.getBalance();
    long entropyLimit = resourceInfo.getEntropyLimit();
    long userAvaliableFrozenEntropy = getAvailableFrozenEntropy(userAddress);
    return balance / 100 + userAvaliableFrozenEntropy;
  }

  private long getFeeLimit(String txid) {
    Optional<Transaction> trsById = PublicMethed.getTransactionById(txid, blockingStubFull);
    return trsById.get().getRawData().getFeeLimit();
  }

  private long getUserMax(byte[] userAddress, long feelimit) {
    logger.info("User feeLimit: " + feelimit / 100);
    logger.info("User UserAvaliableEntropy: " + getUserAvailableEntropy(userAddress));
    return Math.min(feelimit / 100, getUserAvailableEntropy(userAddress));
  }

  private long getOriginalEntropyLimit(byte[] contractAddress) {
    SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    return smartContract.getOriginEntropyLimit();
  }

  private long getConsumeUserResourcePercent(byte[] contractAddress) {
    SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    return smartContract.getConsumeUserResourcePercent();
  }

  private long getDevMax(byte[] devAddress, byte[] userAddress, long feeLimit,
      byte[] contractAddress) {
    long devMax = Math.min(getAvailableFrozenEntropy(devAddress),
        getOriginalEntropyLimit(contractAddress));
    long p = getConsumeUserResourcePercent(contractAddress);
    if (p != 0) {
      logger.info("p: " + p);
      devMax = Math.min(devMax, getUserMax(userAddress, feeLimit) * (100 - p) / p);
      logger.info("Dev byUserPercent: " + getUserMax(userAddress, feeLimit) * (100 - p) / p);
    }
    logger.info("Dev AvaliableFrozenEntropy: " + getAvailableFrozenEntropy(devAddress));
    logger.info("Dev OriginalEntropyLimit: " + getOriginalEntropyLimit(contractAddress));
    return devMax;
  }

  @Test(enabled = true, description = "Contract use Origin_entropy_limit")
  public void testOriginEntropyLimit() {
    Assert.assertTrue(PublicMethed.sendcoin(dev001Address, 1000000L, fromAddress,
        testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethed.sendcoin(user001Address, 1000000L, fromAddress,
        testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    // A2B1

    //dev balance and Entropy
    long devTargetBalance = 10_000_000;
    long devTargetEntropy = 70000;

    // deploy contract parameters
    final long deployFeeLimit = maxFeeLimit;
    final long consumeUserResourcePercent = 0;
    final long originEntropyLimit = 1000;

    //dev balance and Entropy
    final long devTriggerTargetBalance = 0;
    final long devTriggerTargetEntropy = 592;

    // user balance and Entropy
    final long userTargetBalance = 0;
    final long userTargetEntropy = 2000L;

    // trigger contract parameter, maxFeeLimit 10000000
    final long triggerFeeLimit = maxFeeLimit;
    final boolean expectRet = true;

    // count dev entropy, balance
    long devFreezeBalanceVdt = PublicMethed.getFreezeBalanceCount(dev001Address, dev001Key,
        devTargetEntropy, blockingStubFull);

    long devNeedBalance = devTargetBalance + devFreezeBalanceVdt;

    logger.info("need balance:" + devNeedBalance);

    // get balance
    Assert.assertTrue(PublicMethed.sendcoin(dev001Address, devNeedBalance, fromAddress,
        testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    // get entropy
    Assert.assertTrue(PublicMethed.freezeBalanceGetEntropy(dev001Address, devFreezeBalanceVdt,
        0, 1, dev001Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    AccountResourceMessage accountResource = PublicMethed.getAccountResource(dev001Address,
        blockingStubFull);
    long devEntropyLimitBefore = accountResource.getEntropyLimit();
    long devEntropyUsageBefore = accountResource.getEntropyUsed();
    long devBalanceBefore = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();

    logger.info("before deploy, dev entropy limit is " + Long.toString(devEntropyLimitBefore));
    logger.info("before deploy, dev entropy usage is " + Long.toString(devEntropyUsageBefore));
    logger.info("before deploy, dev balance is " + Long.toString(devBalanceBefore));

    String filePath = "src/test/resources/soliditycode/contractOriginEntropyLimit004.sol";
    String contractName = "findArgsContractTest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    final String deployTxid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "",
            deployFeeLimit, 0L, consumeUserResourcePercent, originEntropyLimit, "0",
            0, null, dev001Key, dev001Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    long devEntropyLimitAfter = accountResource.getEntropyLimit();
    long devEntropyUsageAfter = accountResource.getEntropyUsed();
    long devBalanceAfter = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();

    logger.info("after deploy, dev entropy limit is " + Long.toString(devEntropyLimitAfter));
    logger.info("after deploy, dev entropy usage is " + Long.toString(devEntropyUsageAfter));
    logger.info("after deploy, dev balance is " + Long.toString(devBalanceAfter));

    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(deployTxid, blockingStubFull);

    ByteString contractAddressString = infoById.get().getContractAddress();
    contractAddress = contractAddressString.toByteArray();
    SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);

    Assert.assertTrue(smartContract.getAbi() != null);

    Assert.assertTrue(devEntropyLimitAfter > 0);
    Assert.assertTrue(devEntropyUsageAfter > 0);
    Assert.assertEquals(devBalanceBefore, devBalanceAfter);

    // count dev entropy, balance
    devFreezeBalanceVdt = PublicMethed.getFreezeBalanceCount(dev001Address, dev001Key,
        devTriggerTargetEntropy, blockingStubFull);

    devNeedBalance = devTriggerTargetBalance + devFreezeBalanceVdt;
    logger.info("dev need  balance:" + devNeedBalance);

    // count user entropy, balance
    long userFreezeBalanceVdt = PublicMethed.getFreezeBalanceCount(user001Address, user001Key,
        userTargetEntropy, blockingStubFull);

    long userNeedBalance = userTargetBalance + userFreezeBalanceVdt;

    logger.info("User need  balance:" + userNeedBalance);

    // get balance
    Assert.assertTrue(PublicMethed.sendcoin(dev001Address, devNeedBalance, fromAddress,
        testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethed.sendcoin(user001Address, userNeedBalance, fromAddress,
        testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    // get entropy
    Assert.assertTrue(PublicMethed.freezeBalanceGetEntropy(dev001Address, devFreezeBalanceVdt,
        0, 1, dev001Key, blockingStubFull));
    Assert.assertTrue(PublicMethed.freezeBalanceGetEntropy(user001Address, userFreezeBalanceVdt,
        0, 1, user001Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    devEntropyLimitBefore = accountResource.getEntropyLimit();
    devEntropyUsageBefore = accountResource.getEntropyUsed();
    devBalanceBefore = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();

    logger.info("before trigger, dev devEntropyLimitBefore is "
        + Long.toString(devEntropyLimitBefore));
    logger.info("before trigger, dev devEntropyUsageBefore is "
        + Long.toString(devEntropyUsageBefore));
    logger.info("before trigger, dev devBalanceBefore is " + Long.toString(devBalanceBefore));

    accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
    long userEntropyLimitBefore = accountResource.getEntropyLimit();
    long userEntropyUsageBefore = accountResource.getEntropyUsed();
    long userBalanceBefore = PublicMethed.queryAccount(
        user001Address, blockingStubFull).getBalance();

    logger.info("before trigger, user userEntropyLimitBefore is "
        + Long.toString(userEntropyLimitBefore));
    logger.info("before trigger, user userEntropyUsageBefore is "
        + Long.toString(userEntropyUsageBefore));
    logger.info("before trigger, user userBalanceBefore is " + Long.toString(userBalanceBefore));

    logger.info("==================================");
    long userMax = getUserMax(user001Address, triggerFeeLimit);
    long devMax = getDevMax(dev001Address, user001Address, triggerFeeLimit, contractAddress);

    logger.info("userMax: " + userMax);
    logger.info("devMax: " + devMax);
    logger.info("==================================");

    String param = "\"" + 0 + "\"";
    final String triggerTxid = PublicMethed
        .triggerContract(contractAddress, "findArgsByIndexTest(uint256)",
            param, false, 0, triggerFeeLimit,
            user001Address, user001Key, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    devEntropyLimitAfter = accountResource.getEntropyLimit();
    devEntropyUsageAfter = accountResource.getEntropyUsed();
    devBalanceAfter = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();

    logger.info("after trigger, dev devEntropyLimitAfter is " + Long.toString(devEntropyLimitAfter));
    logger.info("after trigger, dev devEntropyUsageAfter is " + Long.toString(devEntropyUsageAfter));
    logger.info("after trigger, dev devBalanceAfter is " + Long.toString(devBalanceAfter));

    accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
    long userEntropyLimitAfter = accountResource.getEntropyLimit();
    long userEntropyUsageAfter = accountResource.getEntropyUsed();
    long userBalanceAfter = PublicMethed.queryAccount(user001Address,
        blockingStubFull).getBalance();

    logger.info("after trigger, user userEntropyLimitAfter is "
        + Long.toString(userEntropyLimitAfter));
    logger.info("after trigger, user userEntropyUsageAfter is "
        + Long.toString(userEntropyUsageAfter));
    logger.info("after trigger, user userBalanceAfter is " + Long.toString(userBalanceAfter));

    infoById = PublicMethed.getTransactionInfoById(triggerTxid, blockingStubFull);
    boolean isSuccess = true;
    if (triggerTxid == null || infoById.get().getResultValue() != 0) {
      logger.info("transaction failed with message: " + infoById.get().getResMessage());
      isSuccess = false;
    }

    long fee = infoById.get().getFee();
    long entropyFee = infoById.get().getReceipt().getEntropyFee();
    long entropyUsage = infoById.get().getReceipt().getEntropyUsage();
    long originEntropyUsage = infoById.get().getReceipt().getOriginEntropyUsage();
    long entropyTotalUsage = infoById.get().getReceipt().getEntropyUsageTotal();
    long netUsage = infoById.get().getReceipt().getNetUsage();
    long netFee = infoById.get().getReceipt().getNetFee();

    logger.info("fee: " + fee);
    logger.info("entropyFee: " + entropyFee);
    logger.info("entropyUsage: " + entropyUsage);
    logger.info("originEntropyUsage: " + originEntropyUsage);
    logger.info("entropyTotalUsage: " + entropyTotalUsage);
    logger.info("netUsage: " + netUsage);
    logger.info("netFee: " + netFee);

    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    long consumeUserPercent = smartContract.getConsumeUserResourcePercent();
    logger.info("ConsumeURPercent: " + consumeUserPercent);

    long devExpectCost = entropyTotalUsage * (100 - consumeUserPercent) / 100;
    long userExpectCost = entropyTotalUsage - devExpectCost;
    final long totalCost = devExpectCost + userExpectCost;

    logger.info("devExpectCost: " + devExpectCost);
    logger.info("userExpectCost: " + userExpectCost);

    Assert.assertTrue(devEntropyLimitAfter > 0);
    Assert.assertEquals(devBalanceBefore, devBalanceAfter);

    // dev original is the dev max expense A2B1
    Assert.assertEquals(getOriginalEntropyLimit(contractAddress), devMax);

    // DEV is enough to pay
    Assert.assertEquals(originEntropyUsage, devExpectCost);
    //    Assert.assertEquals(devEntropyUsageAfter,devExpectCost + devEntropyUsageBefore);
    // User Entropy is enough to pay");
    Assert.assertEquals(entropyUsage, userExpectCost);
    Assert.assertEquals(userBalanceBefore, userBalanceAfter);
    Assert.assertEquals(userEntropyUsageAfter, userEntropyUsageBefore);
    Assert.assertEquals(userBalanceBefore, userBalanceAfter);
    Assert.assertEquals(totalCost, entropyTotalUsage);

    if (expectRet) {
      Assert.assertTrue(isSuccess);
    } else {
      Assert.assertFalse(isSuccess);
    }
  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed.unFreezeBalance(user001Address, user001Key, 1, user001Address, blockingStubFull);
    PublicMethed.unFreezeBalance(dev001Address, dev001Key, 1, dev001Address, blockingStubFull);
    PublicMethed.freedResource(user001Address, user001Key, fromAddress, blockingStubFull);
    PublicMethed.freedResource(dev001Address, dev001Key, fromAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelFull1 != null) {
      channelFull1.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


