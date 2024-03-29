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
import stest.vision.wallet.common.client.utils.Base58;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractLinkage006 {

  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey003);
  String contractName;
  String code;
  String abi;
  byte[] contractAddress;
  String txid;
  Optional<TransactionInfo> infoById;
  String initParmes;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] linkage006Address = ecKey1.getAddress();
  String linkage006Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] linkage006Address2 = ecKey2.getAddress();
  String linkage006Key2 = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
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
    PublicMethed.printAddress(linkage006Key);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);
  }

  @Test(enabled = true, description = "Deploy contract with stack function")
  public void teststackOutByContract() {

    Assert.assertTrue(PublicMethed.sendcoin(linkage006Address, 20000000000L, fromAddress,
        testKey003, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed.freezeBalance(linkage006Address, 1000000L,
        0, linkage006Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed.freezeBalanceGetEntropy(linkage006Address, 1000000L,
        0, 1, linkage006Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(linkage006Address,
        blockingStubFull);
    Account info;
    info = PublicMethed.queryAccount(linkage006Address, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyLimit = resourceInfo.getEntropyLimit();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforeFreePhotonLimit = resourceInfo.getFreePhotonLimit();
    Long beforePhotonLimit = resourceInfo.getPhotonLimit();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyLimit:" + beforeEntropyLimit);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforeFreePhotonLimit:" + beforeFreePhotonLimit);
    logger.info("beforePhotonLimit:" + beforePhotonLimit);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);

    String filePath = "./src/test/resources/soliditycode/contractLinkage006.sol";
    String contractName = "AA";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    //success ,balnace change.use EntropyUsed and PhotonUsed
    txid = PublicMethed.deployContractAndGetTransactionInfoById(contractName, abi, code,
        "", maxFeeLimit, 1000L, 100, null, linkage006Key,
        linkage006Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    logger.info("txid is " + txid);
    contractAddress = infoById.get().getContractAddress().toByteArray();
    Long entropyUsageTotal = infoById.get().getReceipt().getEntropyUsageTotal();
    Long fee = infoById.get().getFee();
    Long entropyFee = infoById.get().getReceipt().getEntropyFee();
    Long photonUsed = infoById.get().getReceipt().getPhotonUsage();
    Long entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    Long photonFee = infoById.get().getReceipt().getPhotonFee();
    logger.info("entropyUsageTotal:" + entropyUsageTotal);
    logger.info("fee:" + fee);
    logger.info("entropyFee:" + entropyFee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);
    Account infoafter = PublicMethed.queryAccount(linkage006Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(linkage006Address,
        blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterEntropyLimit = resourceInfoafter.getEntropyLimit();
    Long afterEntropyUsed = resourceInfoafter.getEntropyUsed();
    Long afterFreePhotonLimit = resourceInfoafter.getFreePhotonLimit();
    Long afterPhotonLimit = resourceInfoafter.getPhotonLimit();
    Long afterPhotonUsed = resourceInfoafter.getPhotonUsed();
    Long afterFreePhotonUsed = resourceInfoafter.getFreePhotonUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterREntropyLimit:" + afterEntropyLimit);
    logger.info("afterEntropyUsed:" + afterEntropyUsed);
    logger.info("afterFreePhotonLimit:" + afterFreePhotonLimit);
    logger.info("afterPhotonLimit:" + afterPhotonLimit);
    logger.info("afterPhotonUsed:" + afterPhotonUsed);
    logger.info("afterFreePhotonUsed:" + afterFreePhotonUsed);

    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue((beforeBalance - fee - 1000L) == afterBalance);
    Assert.assertTrue((beforePhotonUsed + photonUsed) >= afterPhotonUsed);
    Assert.assertTrue((beforeEntropyUsed + entropyUsed) >= afterEntropyUsed);
    PublicMethed.unFreezeBalance(linkage006Address, linkage006Key, 1,
        null, blockingStubFull);
  }

  @Test(enabled = true, description = "Boundary value for contract stack(63 is the largest level)")
  public void teststackOutByContract1() {
    Assert.assertTrue(PublicMethed.sendcoin(linkage006Address2, 20000000000L, fromAddress,
        testKey003, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed.freezeBalance(linkage006Address2, 1000000L,
        0, linkage006Key2, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed.freezeBalanceGetEntropy(linkage006Address2, 1000000L,
        0, 1, linkage006Key2, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    AccountResourceMessage resourceInfo1 = PublicMethed.getAccountResource(linkage006Address2,
        blockingStubFull);
    Account info1 = PublicMethed.queryAccount(linkage006Address2, blockingStubFull);
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

    //success ,balance change.use EntropyUsed and PhotonUsed
    initParmes = "\"" + Base58.encode58Check(fromAddress) + "\",\"63\"";
    txid = PublicMethed.triggerContract(contractAddress,
        "init(address,uint256)", initParmes, false,
        0, 100000000L, linkage006Address2, linkage006Key2, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

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
    Account infoafter1 = PublicMethed.queryAccount(linkage006Address2, blockingStubFull1);
    AccountResourceMessage resourceInfoafter1 = PublicMethed
        .getAccountResource(linkage006Address2,
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
    logger.info("---------------:");
    Assert.assertTrue((beforeBalance1 - fee1) == afterBalance1);
    Assert.assertTrue(afterPhotonUsed1 > beforePhotonUsed1);
    Assert.assertTrue((beforeEntropyUsed1 + entropyUsed1) >= afterEntropyUsed1);

    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);
  }

  @Test(enabled = true, description = "Boundary value for contract stack"
      + "(Trigger 64 level can't success)")
  public void teststackOutByContract2() {
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    initParmes = "\"" + Base58.encode58Check(fromAddress) + "\",\"64\"";
    AccountResourceMessage resourceInfo2 = PublicMethed.getAccountResource(linkage006Address2,
        blockingStubFull);
    Account info2 = PublicMethed.queryAccount(linkage006Address2, blockingStubFull);
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
    //failed ,use EntropyUsed and PhotonUsed
    txid = PublicMethed.triggerContract(contractAddress,
        "init(address,uint256)", initParmes, false,
        1000, 100000000L, linkage006Address2, linkage006Key2, blockingStubFull);
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

    Account infoafter2 = PublicMethed.queryAccount(linkage006Address2, blockingStubFull1);
    AccountResourceMessage resourceInfoafter2 = PublicMethed.getAccountResource(linkage006Address2,
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

    Assert.assertTrue((beforeBalance2 - fee2) == afterBalance2);
    Assert.assertTrue((beforeEntropyUsed2 + entropyUsed2) >= afterEntropyUsed2);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 1);
    PublicMethed.unFreezeBalance(linkage006Address2, linkage006Key2, 1,
        linkage006Address2, blockingStubFull);
  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed.unFreezeBalance(linkage006Address, linkage006Key, 1,
        linkage006Address, blockingStubFull);
    PublicMethed.unFreezeBalance(linkage006Address, linkage006Key, 0,
        linkage006Address, blockingStubFull);
    PublicMethed.unFreezeBalance(linkage006Address2, linkage006Key2, 1,
        linkage006Address2, blockingStubFull);
    PublicMethed.unFreezeBalance(linkage006Address2, linkage006Key2, 0,
        linkage006Address2, blockingStubFull);
    PublicMethed.freedResource(linkage006Address, linkage006Key, fromAddress, blockingStubFull);
    PublicMethed.freedResource(linkage006Address2, linkage006Key2, fromAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }


}


