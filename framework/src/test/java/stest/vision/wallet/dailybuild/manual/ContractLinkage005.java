package stest.vision.wallet.dailybuild.manual;

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
public class ContractLinkage005 {

  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey003);
  String contractName;
  String code;
  String abi;
  Long zeroForCycleCost;
  Long firstForCycleCost;
  Long secondForCycleCost;
  Long thirdForCycleCost;
  Long forthForCycleCost;
  Long fifthForCycleCost;
  Long zeroForCycleTimes = 498L;
  Long firstForCycleTimes = 500L;
  Long secondForCycleTimes = 502L;
  Long thirdForCycleTimes = 504L;
  Long forthForCycleTimes = 506L;
  Long fifthForCycleTimes = 508L;
  byte[] contractAddress;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] linkage005Address = ecKey1.getAddress();
  String linkage005Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
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
    PublicMethed.printAddress(linkage005Key);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);
  }

  @Test(enabled = true, description = "Every same trigger use same entropy and net")
  public void testEntropyCostDetail() {
    PublicMethed.waitProduceNextBlock(blockingStubFull1);
    Assert.assertTrue(PublicMethed.sendcoin(linkage005Address, 5000000000000L, fromAddress,
        testKey003, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed.freezeBalance(linkage005Address, 250000000000L,
        0, linkage005Key, blockingStubFull));
    Assert.assertTrue(PublicMethed.freezeBalanceGetEntropy(linkage005Address, 250000000000L,
        0, 1, linkage005Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(linkage005Address,
        blockingStubFull);
    Account info;
    info = PublicMethed.queryAccount(linkage005Address, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyLimit = resourceInfo.getEntropyLimit();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforeFreeNetLimit = resourceInfo.getFreePhotonLimit();
    Long beforeNetLimit = resourceInfo.getPhotonLimit();
    Long beforeNetUsed = resourceInfo.getPhotonUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreePhotonUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyLimit:" + beforeEntropyLimit);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforeFreeNetLimit:" + beforeFreeNetLimit);
    logger.info("beforeNetLimit:" + beforeNetLimit);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);

    String filePath = "./src/test/resources/soliditycode/contractLinkage005.sol";
    String contractName = "timeoutTest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    String txid = PublicMethed.deployContractAndGetTransactionInfoById(contractName, abi, code,
        "", maxFeeLimit, 0L, 100, null, linkage005Key,
        linkage005Address, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("Deploy entropytotal is " + infoById.get().getReceipt().getEntropyUsageTotal());

    Account infoafter = PublicMethed.queryAccount(linkage005Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(linkage005Address,
        blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterEntropyLimit = resourceInfoafter.getEntropyLimit();
    Long afterEntropyUsed = resourceInfoafter.getEntropyUsed();
    Long afterFreeNetLimit = resourceInfoafter.getFreePhotonLimit();
    Long afterNetLimit = resourceInfoafter.getPhotonLimit();
    Long afterNetUsed = resourceInfoafter.getPhotonUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreePhotonUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEntropyLimit:" + afterEntropyLimit);
    logger.info("afterEntropyUsed:" + afterEntropyUsed);
    logger.info("afterFreeNetLimit:" + afterFreeNetLimit);
    logger.info("afterNetLimit:" + afterNetLimit);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);
    logger.info("---------------:");
    long fee = infoById.get().getFee();

    Assert.assertTrue(beforeBalance - fee == afterBalance);
    //Assert.assertTrue(afterEntropyUsed > 0);
    //Assert.assertTrue(afterFreeNetUsed > 0);
    firstForCycleTimes = 1000L;
    secondForCycleTimes = 1002L;
    thirdForCycleTimes = 1004L;

    AccountResourceMessage resourceInfo1 = PublicMethed.getAccountResource(linkage005Address,
        blockingStubFull);
    Account info1 = PublicMethed.queryAccount(linkage005Address, blockingStubFull);
    Long beforeBalance1 = info1.getBalance();
    Long beforeEntropyLimit1 = resourceInfo1.getEntropyLimit();
    Long beforeEntropyUsed1 = resourceInfo1.getEntropyUsed();
    Long beforeFreeNetLimit1 = resourceInfo1.getFreePhotonLimit();
    Long beforeNetLimit1 = resourceInfo1.getPhotonLimit();
    Long beforeNetUsed1 = resourceInfo1.getPhotonUsed();
    Long beforeFreeNetUsed1 = resourceInfo1.getFreePhotonUsed();
    logger.info("beforeBalance1:" + beforeBalance1);
    logger.info("beforeEntropyLimit1:" + beforeEntropyLimit1);
    logger.info("beforeEntropyUsed1:" + beforeEntropyUsed1);
    logger.info("beforeFreeNetLimit1:" + beforeFreeNetLimit1);
    logger.info("beforeNetLimit1:" + beforeNetLimit1);
    logger.info("beforeNetUsed1:" + beforeNetUsed1);
    logger.info("beforeFreeNetUsed1:" + beforeFreeNetUsed1);
    byte[] contractAddress = infoById.get().getContractAddress().toByteArray();
    txid = PublicMethed.triggerContract(contractAddress,
        "testUseCpu(uint256)", firstForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull1);
    Account infoafter1 = PublicMethed.queryAccount(linkage005Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter1 = PublicMethed.getAccountResource(linkage005Address,
        blockingStubFull1);
    Long afterBalance1 = infoafter1.getBalance();
    Long afterEntropyLimit1 = resourceInfoafter1.getEntropyLimit();
    Long afterEntropyUsed1 = resourceInfoafter1.getEntropyUsed();
    Long afterFreeNetLimit1 = resourceInfoafter1.getFreePhotonLimit();
    Long afterNetLimit1 = resourceInfoafter1.getPhotonLimit();
    Long afterNetUsed1 = resourceInfoafter1.getPhotonUsed();
    Long afterFreeNetUsed1 = resourceInfoafter1.getFreePhotonUsed();
    logger.info("afterBalance1:" + afterBalance1);
    logger.info("afterEntropyLimit1:" + afterEntropyLimit1);
    logger.info("afterEntropyUsed1:" + afterEntropyUsed1);
    logger.info("afterFreeNetLimit1:" + afterFreeNetLimit1);
    logger.info("afterNetLimit1:" + afterNetLimit1);
    logger.info("afterNetUsed1:" + afterNetUsed1);
    logger.info("afterFreeNetUsed1:" + afterFreeNetUsed1);
    logger.info("---------------:");
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    fee = infoById.get().getFee();
    firstForCycleCost = infoById.get().getReceipt().getEntropyUsageTotal();
    Assert.assertTrue((beforeBalance1 - fee) == afterBalance1);
    Assert.assertTrue(afterEntropyUsed1 > beforeEntropyUsed1);
    Assert.assertTrue(afterNetUsed1 > beforeNetUsed1);
    //use EntropyUsed and NetUsed.balance not change

    String txid6 = PublicMethed.triggerContract(contractAddress,
        "testUseCpu(uint256)", secondForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);
    final String txid7 = PublicMethed.triggerContract(contractAddress,
        "testUseCpu(uint256)", thirdForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    infoById = PublicMethed.getTransactionInfoById(txid6, blockingStubFull);
    secondForCycleCost = infoById.get().getReceipt().getEntropyUsageTotal();

    infoById = PublicMethed.getTransactionInfoById(txid7, blockingStubFull);
    thirdForCycleCost = infoById.get().getReceipt().getEntropyUsageTotal();

    Assert.assertTrue(thirdForCycleCost - secondForCycleCost
        == secondForCycleCost - firstForCycleCost);

    zeroForCycleTimes = 498L;
    firstForCycleTimes = 500L;
    secondForCycleTimes = 502L;
    thirdForCycleTimes = 504L;
    forthForCycleTimes = 506L;
    fifthForCycleTimes = 508L;
    AccountResourceMessage resourceInfo4 = PublicMethed.getAccountResource(linkage005Address,
        blockingStubFull);
    Account info4 = PublicMethed.queryAccount(linkage005Address, blockingStubFull);
    Long beforeBalance4 = info4.getBalance();
    Long beforeEntropyLimit4 = resourceInfo4.getEntropyLimit();
    Long beforeEntropyUsed4 = resourceInfo4.getEntropyUsed();
    Long beforeFreeNetLimit4 = resourceInfo4.getFreePhotonLimit();
    Long beforeNetLimit4 = resourceInfo4.getPhotonLimit();
    Long beforeNetUsed4 = resourceInfo4.getPhotonUsed();
    Long beforeFreeNetUsed4 = resourceInfo4.getFreePhotonUsed();
    logger.info("beforeBalance4:" + beforeBalance4);
    logger.info("beforeEntropyLimit4:" + beforeEntropyLimit4);
    logger.info("beforeEntropyUsed4:" + beforeEntropyUsed4);
    logger.info("beforeFreeNetLimit4:" + beforeFreeNetLimit4);
    logger.info("beforeNetLimit4:" + beforeNetLimit4);
    logger.info("beforeNetUsed4:" + beforeNetUsed4);
    logger.info("beforeFreeNetUsed4:" + beforeFreeNetUsed4);
    txid = PublicMethed.triggerContract(contractAddress,
        "testUseStorage(uint256)", zeroForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    fee = infoById.get().getFee();
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull1);
    Account infoafter4 = PublicMethed.queryAccount(linkage005Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter4 = PublicMethed.getAccountResource(linkage005Address,
        blockingStubFull1);
    Long afterBalance4 = infoafter4.getBalance();
    Long afterEntropyLimit4 = resourceInfoafter4.getEntropyLimit();
    Long afterEntropyUsed4 = resourceInfoafter4.getEntropyUsed();
    Long afterFreeNetLimit4 = resourceInfoafter4.getFreePhotonLimit();
    Long afterNetLimit4 = resourceInfoafter4.getPhotonLimit();
    Long afterNetUsed4 = resourceInfoafter4.getPhotonUsed();
    Long afterFreeNetUsed4 = resourceInfoafter4.getFreePhotonUsed();
    logger.info("afterBalance4:" + afterBalance4);
    logger.info("afterEntropyLimit4:" + afterEntropyLimit4);
    logger.info("afterEntropyUsed4:" + afterEntropyUsed4);
    logger.info("afterFreeNetLimit4:" + afterFreeNetLimit4);
    logger.info("afterNetLimit4:" + afterNetLimit4);
    logger.info("afterNetUsed4:" + afterNetUsed4);
    logger.info("afterFreeNetUsed4:" + afterFreeNetUsed4);
    logger.info("---------------:");
    Assert.assertTrue(beforeBalance4 - fee == afterBalance4);
    Assert.assertTrue(afterEntropyUsed4 > beforeEntropyUsed4);
    Assert.assertTrue(afterNetUsed4 > beforeNetUsed4);

    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    zeroForCycleCost = infoById.get().getReceipt().getEntropyUsageTotal();

    String txid1 = PublicMethed.triggerContract(contractAddress,
        "testUseStorage(uint256)", firstForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

    final String txid2 = PublicMethed.triggerContract(contractAddress,
        "testUseStorage(uint256)", secondForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

    final String txid3 = PublicMethed.triggerContract(contractAddress,
        "testUseStorage(uint256)", thirdForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

    final String txid4 = PublicMethed.triggerContract(contractAddress,
        "testUseStorage(uint256)", forthForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

    final String txid5 = PublicMethed.triggerContract(contractAddress,
        "testUseStorage(uint256)", fifthForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    infoById = PublicMethed.getTransactionInfoById(txid1, blockingStubFull);
    firstForCycleCost = infoById.get().getReceipt().getEntropyUsageTotal();

    infoById = PublicMethed.getTransactionInfoById(txid2, blockingStubFull);
    secondForCycleCost = infoById.get().getReceipt().getEntropyUsageTotal();

    infoById = PublicMethed.getTransactionInfoById(txid3, blockingStubFull);
    thirdForCycleCost = infoById.get().getReceipt().getEntropyUsageTotal();

    infoById = PublicMethed.getTransactionInfoById(txid4, blockingStubFull);
    forthForCycleCost = infoById.get().getReceipt().getEntropyUsageTotal();

    infoById = PublicMethed.getTransactionInfoById(txid5, blockingStubFull);
    fifthForCycleCost = infoById.get().getReceipt().getEntropyUsageTotal();

    Assert.assertTrue(thirdForCycleCost - secondForCycleCost
        == secondForCycleCost - firstForCycleCost);
    Assert.assertTrue(fifthForCycleCost - forthForCycleCost
        == forthForCycleCost - thirdForCycleCost);


  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed.unFreezeBalance(linkage005Address, linkage005Key, 1,
        linkage005Address, blockingStubFull);
    PublicMethed.unFreezeBalance(linkage005Address, linkage005Key, 0,
        linkage005Address, blockingStubFull);
    PublicMethed.freedResource(linkage005Address, linkage005Key, fromAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelFull1 != null) {
      channelFull1.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }


}


