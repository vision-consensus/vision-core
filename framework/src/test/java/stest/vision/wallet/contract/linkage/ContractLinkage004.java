package stest.vision.wallet.contract.linkage;

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
import org.vision.protos.Protocol.TransactionInfo;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter.CommonConstant;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractLinkage004 {

  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey003);
  String contractName;
  String code;
  String abi;
  Long currentFee;
  Account info;
  Long beforeBalance;
  Long beforePhotonLimit;
  Long beforeFreePhotonLimit;
  Long beforeFreePhotonUsed;
  Long beforePhotonUsed;
  Long beforeEntropyLimit;
  Long beforeEntropyUsed;
  Long afterBalance;
  Long afterPhotonLimit;
  Long afterFreePhotonLimit;
  Long afterFreePhotonUsed;
  Long afterPhotonUsed;
  Long afterEntropyLimit;
  Long afterEntropyUsed;
  Long entropyUsed;
  Long photonUsed;
  Long entropyFee;
  Long fee;
  Long entropyUsageTotal;
  Long photonFee;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] linkage004Address = ecKey1.getAddress();
  String linkage004Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */
  @BeforeClass(enabled = true)
  public void beforeClass() {
    PublicMethed.printAddress(linkage004Key);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);
  }

  @Test(enabled = true)
  public void test1GetTransactionInfoById() {
    ecKey1 = new ECKey(Utils.getRandom());
    linkage004Address = ecKey1.getAddress();
    linkage004Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

    Assert.assertTrue(PublicMethed.sendcoin(linkage004Address, 2000000000000L, fromAddress,
        testKey003, blockingStubFull));
    Assert.assertTrue(PublicMethed.freezeBalance(linkage004Address, 10000000L,
        3, linkage004Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(linkage004Address,
        blockingStubFull);
    info = PublicMethed.queryAccount(linkage004Address, blockingStubFull);
    beforeBalance = info.getBalance();
    beforeEntropyLimit = resourceInfo.getEntropyLimit();
    beforeEntropyUsed = resourceInfo.getEntropyUsed();
    beforeFreePhotonLimit = resourceInfo.getFreePhotonLimit();
    beforePhotonLimit = resourceInfo.getPhotonLimit();
    beforePhotonUsed = resourceInfo.getPhotonUsed();
    beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyLimit:" + beforeEntropyLimit);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforeFreePhotonLimit:" + beforeFreePhotonLimit);
    logger.info("beforePhotonLimit:" + beforePhotonLimit);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);

    String filePath = "./src/test/resources/soliditycode/contractLinkage004.sol";
    String contractName = "divideIHaveArgsReturnStorage";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    //use freezeBalanceGetNet,Balance .No freezeBalanceGetentropy
    String txid = PublicMethed.deployContractAndGetTransactionInfoById(contractName, abi, code,
        "", maxFeeLimit, 0L, 50, null, linkage004Key, linkage004Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull1);
    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    entropyUsageTotal = infoById.get().getReceipt().getEntropyUsageTotal();
    fee = infoById.get().getFee();
    currentFee = fee;
    entropyFee = infoById.get().getReceipt().getEntropyFee();
    photonUsed = infoById.get().getReceipt().getPhotonUsage();
    entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    photonFee = infoById.get().getReceipt().getPhotonFee();
    logger.info("entropyUsageTotal:" + entropyUsageTotal);
    logger.info("fee:" + fee);
    logger.info("entropyFee:" + entropyFee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);

    Account infoafter = PublicMethed.queryAccount(linkage004Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(linkage004Address,
        blockingStubFull1);
    afterBalance = infoafter.getBalance();
    afterEntropyLimit = resourceInfoafter.getEntropyLimit();
    afterEntropyUsed = resourceInfoafter.getEntropyUsed();
    afterFreePhotonLimit = resourceInfoafter.getFreePhotonLimit();
    afterPhotonLimit = resourceInfoafter.getPhotonLimit();
    afterPhotonUsed = resourceInfoafter.getPhotonUsed();
    afterFreePhotonUsed = resourceInfoafter.getFreePhotonUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEntropyLimit:" + afterEntropyLimit);
    logger.info("afterEntropyUsed:" + afterEntropyUsed);
    logger.info("afterFreePhotonLimit:" + afterFreePhotonLimit);
    logger.info("afterPhotonLimit:" + afterPhotonLimit);
    logger.info("afterPhotonUsed:" + afterPhotonUsed);
    logger.info("afterFreePhotonUsed:" + afterFreePhotonUsed);
    logger.info("---------------:");
    Assert.assertTrue(infoById.isPresent());
    Assert.assertTrue((beforeBalance - fee) == afterBalance);
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(afterEntropyUsed == 0);
    Assert.assertTrue(afterFreePhotonUsed > 0);
  }

  @Test(enabled = true)
  public void test2FeeLimitIsTooSmall() {
    //When the fee limit is only short with 1 vdt,failed.use freezeBalanceGetNet.
    maxFeeLimit = currentFee - 1L;
    AccountResourceMessage resourceInfo1 = PublicMethed.getAccountResource(linkage004Address,
        blockingStubFull);
    Account info1 = PublicMethed.queryAccount(linkage004Address, blockingStubFull);
    Long beforeBalance1 = info1.getBalance();
    Long beforeEntropyLimit1 = resourceInfo1.getEntropyLimit();
    Long beforeEntropyUsed1 = resourceInfo1.getEntropyUsed();
    Long beforeFreePhotonLimit1 = resourceInfo1.getFreePhotonLimit();
    Long beforePhotonLimit1 = resourceInfo1.getPhotonLimit();
    Long beforePhotonUsed1 = resourceInfo1.getPhotonUsed();
    Long beforeFreePhotonUsed1 = resourceInfo1.getFreePhotonUsed();
    logger.info("beforeBalance1:" + beforeBalance1);
    logger.info("beforeEntropyLimit1:" + beforeEntropyLimit1);
    logger.info("beforeEntropyUsed1:" + beforeEntropyUsed1);
    logger.info("beforeFreePhotonLimit1:" + beforeFreePhotonLimit1);
    logger.info("beforePhotonLimit1:" + beforePhotonLimit1);
    logger.info("beforePhotonUsed1:" + beforePhotonUsed1);
    logger.info("beforeFreePhotonUsed1:" + beforeFreePhotonUsed1);

    String filePath = "./src/test/resources/soliditycode/contractLinkage004.sol";
    String contractName = "divideIHaveArgsReturnStorage";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    String txid = PublicMethed.deployContractAndGetTransactionInfoById(contractName, abi, code,
        "", maxFeeLimit, 0L, 50, null, linkage004Key, linkage004Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull1);

    Optional<TransactionInfo> infoById1 = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Long entropyUsageTotal1 = infoById1.get().getReceipt().getEntropyUsageTotal();
    Long fee1 = infoById1.get().getFee();
    Long entropyFee1 = infoById1.get().getReceipt().getEntropyFee();
    Long photonUsed1 = infoById1.get().getReceipt().getPhotonUsage();
    Long entropyUsed1 = infoById1.get().getReceipt().getEntropyUsage();
    Long photonFee1 = infoById1.get().getReceipt().getPhotonFee();
    logger.info("entropyUsageTotal1:" + entropyUsageTotal1);
    logger.info("fee1:" + fee1);
    logger.info("entropyFee1:" + entropyFee1);
    logger.info("photonUsed1:" + photonUsed1);
    logger.info("entropyUsed1:" + entropyUsed1);
    logger.info("photonFee1:" + photonFee1);

    Account infoafter1 = PublicMethed.queryAccount(linkage004Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter1 = PublicMethed.getAccountResource(linkage004Address,
        blockingStubFull1);
    Long afterBalance1 = infoafter1.getBalance();
    Long afterEntropyLimit1 = resourceInfoafter1.getEntropyLimit();
    Long afterEntropyUsed1 = resourceInfoafter1.getEntropyUsed();
    Long afterFreePhotonLimit1 = resourceInfoafter1.getFreePhotonLimit();
    Long afterPhotonLimit1 = resourceInfoafter1.getPhotonLimit();
    Long afterPhotonUsed1 = resourceInfoafter1.getPhotonUsed();
    Long afterFreePhotonUsed1 = resourceInfoafter1.getFreePhotonUsed();
    logger.info("afterBalance1:" + afterBalance1);
    logger.info("afterEntropyLimit1:" + afterEntropyLimit1);
    logger.info("afterEntropyUsed1:" + afterEntropyUsed1);
    logger.info("afterFreePhotonLimit1:" + afterFreePhotonLimit1);
    logger.info("afterPhotonLimit1:" + afterPhotonLimit1);
    logger.info("afterPhotonUsed1:" + afterPhotonUsed1);
    logger.info("afterFreePhotonUsed1:" + afterFreePhotonUsed1);

    Assert.assertTrue((beforeBalance1 - fee1) == afterBalance1);
    Assert.assertTrue(infoById1.get().getResultValue() == 1);
    Assert.assertTrue(entropyUsageTotal1 > 0);
    Assert.assertTrue(afterEntropyUsed1 == 0);
    Assert.assertTrue(beforePhotonUsed1 < afterPhotonUsed1);

    //When the fee limit is just ok.use entropyFee,freezeBalanceGetNet,balance change.
    maxFeeLimit = currentFee;
    AccountResourceMessage resourceInfo2 = PublicMethed.getAccountResource(linkage004Address,
        blockingStubFull);
    Account info2 = PublicMethed.queryAccount(linkage004Address, blockingStubFull);
    Long beforeBalance2 = info2.getBalance();
    Long beforeEntropyLimit2 = resourceInfo2.getEntropyLimit();
    Long beforeEntropyUsed2 = resourceInfo2.getEntropyUsed();
    Long beforeFreePhotonLimit2 = resourceInfo2.getFreePhotonLimit();
    Long beforePhotonLimit2 = resourceInfo2.getPhotonLimit();
    Long beforePhotonUsed2 = resourceInfo2.getPhotonUsed();
    Long beforeFreePhotonUsed2 = resourceInfo2.getFreePhotonUsed();
    logger.info("beforeBalance2:" + beforeBalance2);
    logger.info("beforeEntropyLimit2:" + beforeEntropyLimit2);
    logger.info("beforeEntropyUsed2:" + beforeEntropyUsed2);
    logger.info("beforeFreePhotonLimit2:" + beforeFreePhotonLimit2);
    logger.info("beforePhotonLimit2:" + beforePhotonLimit2);
    logger.info("beforePhotonUsed2:" + beforePhotonUsed2);
    logger.info("beforeFreePhotonUsed2:" + beforeFreePhotonUsed2);
    txid = PublicMethed.deployContractAndGetTransactionInfoById(contractName, abi, code,
        "", maxFeeLimit, 0L, 50, null, linkage004Key, linkage004Address, blockingStubFull);
    //logger.info("testFeeLimitIsTooSmall, the txid is " + txid);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById2 = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Long entropyUsageTotal2 = infoById2.get().getReceipt().getEntropyUsageTotal();
    Long fee2 = infoById2.get().getFee();
    Long entropyFee2 = infoById2.get().getReceipt().getEntropyFee();
    Long photonUsed2 = infoById2.get().getReceipt().getPhotonUsage();
    Long entropyUsed2 = infoById2.get().getReceipt().getEntropyUsage();
    Long photonFee2 = infoById2.get().getReceipt().getPhotonFee();
    logger.info("entropyUsageTotal2:" + entropyUsageTotal2);
    logger.info("fee2:" + fee2);
    logger.info("entropyFee2:" + entropyFee2);
    logger.info("photonUsed2:" + photonUsed2);
    logger.info("entropyUsed2:" + entropyUsed2);
    logger.info("photonFee2:" + photonFee2);
    Account infoafter2 = PublicMethed.queryAccount(linkage004Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter2 = PublicMethed.getAccountResource(linkage004Address,
        blockingStubFull1);
    Long afterBalance2 = infoafter2.getBalance();
    Long afterEntropyLimit2 = resourceInfoafter2.getEntropyLimit();
    Long afterEntropyUsed2 = resourceInfoafter2.getEntropyUsed();
    Long afterFreePhotonLimit2 = resourceInfoafter2.getFreePhotonLimit();
    Long afterPhotonLimit2 = resourceInfoafter2.getPhotonLimit();
    Long afterPhotonUsed2 = resourceInfoafter2.getPhotonUsed();
    Long afterFreePhotonUsed2 = resourceInfoafter2.getFreePhotonUsed();
    logger.info("afterBalance2:" + afterBalance2);
    logger.info("afterEntropyLimit2:" + afterEntropyLimit2);
    logger.info("afterEntropyUsed2:" + afterEntropyUsed2);
    logger.info("afterFreePhotonLimit2:" + afterFreePhotonLimit2);
    logger.info("afterPhotonLimit2:" + afterPhotonLimit2);
    logger.info("afterPhotonUsed2:" + afterPhotonUsed2);
    logger.info("afterFreePhotonUsed2:" + afterFreePhotonUsed2);

    Assert.assertTrue(infoById2.get().getResultValue() == 0);
    Assert.assertTrue(infoById2.get().getReceipt().getEntropyUsageTotal() > 0);
    Assert.assertTrue((beforeBalance2 - fee2) == afterBalance2);
    Assert.assertTrue((beforePhotonUsed2 + photonUsed2) >= afterPhotonUsed2);

    currentFee = fee2;
  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }


}


