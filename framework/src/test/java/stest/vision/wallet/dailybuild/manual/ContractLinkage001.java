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
public class ContractLinkage001 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] linkage001Address = ecKey1.getAddress();
  String linkage001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
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
    PublicMethed.printAddress(linkage001Key);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);

  }

  @Test(enabled = true, description = "Deploy contract with valid or invalid value")
  public void deployContentValue() {
    Assert.assertTrue(PublicMethed.sendcoin(linkage001Address, 20000000000L, fromAddress,
        testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account info;
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(linkage001Address,
        blockingStubFull);
    info = PublicMethed.queryAccount(linkage001Address, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforePhotonLimit = resourceInfo.getPhotonLimit();
    Long beforeFreePhotonLimit = resourceInfo.getFreePhotonLimit();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeEntropyLimit = resourceInfo.getEntropyLimit();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyLimit:" + beforeEntropyLimit);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforeFreePhotonLimit:" + beforeFreePhotonLimit);
    logger.info("beforePhotonLimit:" + beforePhotonLimit);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);

    //Value is equal balance,this will be failed.Only use FreeNet,Other not change.
    String filePath = "./src/test/resources/soliditycode/contractLinkage001.sol";
    String contractName = "divideIHaveArgsReturnStorage";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String payableCode = retMap.get("byteCode").toString();
    String payableAbi = retMap.get("abI").toString();
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account accountGet = PublicMethed.queryAccount(linkage001Key, blockingStubFull);
    Long accountBalance = accountGet.getBalance();
    String txid = PublicMethed.deployContractAndGetTransactionInfoById(contractName, payableAbi,
        payableCode, "", maxFeeLimit, accountBalance, 100, null,
        linkage001Key, linkage001Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Long entropyUsageTotal = infoById.get().getReceipt().getEntropyUsageTotal();
    Long fee = infoById.get().getFee();
    Long entropyFee = infoById.get().getReceipt().getEntropyFee();
    Long PhotonUsed = infoById.get().getReceipt().getPhotonUsage();
    Long entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    Long photonFee = infoById.get().getReceipt().getPhotonFee();
    logger.info("entropyUsageTotal:" + entropyUsageTotal);
    logger.info("fee:" + fee);
    logger.info("entropyFee:" + entropyFee);
    logger.info("PhotonUsed:" + PhotonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);

    Account infoafter = PublicMethed.queryAccount(linkage001Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(linkage001Address,
        blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterEntropyLimit = resourceInfoafter.getEntropyLimit();
    Long afterEntropyUsed = resourceInfoafter.getEntropyUsed();
    Long afterFreePhotonLimit = resourceInfoafter.getFreePhotonLimit();
    Long afterPhotonLimit = resourceInfoafter.getPhotonLimit();
    Long afterPhotonUsed = resourceInfoafter.getPhotonUsed();
    Long afterFreePhotonUsed = resourceInfoafter.getFreePhotonUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEntropyLimit:" + afterEntropyLimit);
    logger.info("afterEntropyUsed:" + afterEntropyUsed);
    logger.info("afterFreePhotonLimit:" + afterFreePhotonLimit);
    logger.info("afterPhotonLimit:" + afterPhotonLimit);
    logger.info("afterPhotonUsed:" + afterPhotonUsed);
    logger.info("afterFreePhotonUsed:" + afterFreePhotonUsed);

    Assert.assertTrue(infoById.get().getResultValue() == 1);
    Assert.assertEquals(beforeBalance, afterBalance);
    Assert.assertTrue(fee == 0);
    Assert.assertTrue(afterPhotonUsed == 0);
    Assert.assertTrue(afterEntropyUsed == 0);
    Assert.assertTrue(afterFreePhotonUsed > 0);

    Assert.assertTrue(PublicMethed.freezeBalanceGetEntropy(linkage001Address, 50000000L,
        0, 1, linkage001Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    maxFeeLimit = maxFeeLimit - 50000000L;
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    AccountResourceMessage resourceInfo1 = PublicMethed.getAccountResource(linkage001Address,
        blockingStubFull);
    Account info1 = PublicMethed.queryAccount(linkage001Address, blockingStubFull);
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

    //Value is 1,use BalanceGetEntropy,use FreeNet,fee==0.
    txid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, payableAbi, payableCode,
            "", maxFeeLimit, 1L, 100, null, linkage001Key,
            linkage001Address, blockingStubFull);
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
    Assert.assertTrue(infoById1.get().getResultValue() == 0);

    Account infoafter1 = PublicMethed.queryAccount(linkage001Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter1 = PublicMethed.getAccountResource(linkage001Address,
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

    Assert.assertTrue(beforeBalance1 - fee1 - 1L == afterBalance1);
    byte[] contractAddress = infoById1.get().getContractAddress().toByteArray();
    Account account = PublicMethed.queryAccount(contractAddress, blockingStubFull);
    Assert.assertTrue(account.getBalance() == 1L);
    Assert.assertTrue(afterPhotonUsed1 == 0);
    Assert.assertTrue(afterEntropyUsed1 > 0);
    Assert.assertTrue(afterFreePhotonUsed1 > 0);

    //Value is account all balance plus 1. balance is not sufficient,Nothing changde.
    AccountResourceMessage resourceInfo2 = PublicMethed.getAccountResource(linkage001Address,
        blockingStubFull);
    Account info2 = PublicMethed.queryAccount(linkage001Address, blockingStubFull);
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

    account = PublicMethed.queryAccount(linkage001Key, blockingStubFull);
    Long valueBalance = account.getBalance();
    contractAddress = PublicMethed.deployContract(contractName, payableAbi, payableCode, "",
        maxFeeLimit, valueBalance + 1, 100, null, linkage001Key,
        linkage001Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Assert.assertTrue(contractAddress == null);
    Account infoafter2 = PublicMethed.queryAccount(linkage001Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter2 = PublicMethed.getAccountResource(linkage001Address,
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
    Assert.assertTrue(afterPhotonUsed2 == 0);
    Assert.assertTrue(afterEntropyUsed2 > 0);
    Assert.assertTrue(afterFreePhotonUsed2 > 0);
    Assert.assertEquals(beforeBalance2, afterBalance2);

    //Value is account all balance.use freezeBalanceGetEntropy ,freezeBalanceGetNet .Balance ==0
    Assert.assertTrue(PublicMethed.freezeBalance(linkage001Address, 5000000L,
        0, linkage001Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    AccountResourceMessage resourceInfo3 = PublicMethed.getAccountResource(linkage001Address,
        blockingStubFull);
    Account info3 = PublicMethed.queryAccount(linkage001Address, blockingStubFull);
    Long beforeBalance3 = info3.getBalance();
    Long beforeEntropyLimit3 = resourceInfo3.getEntropyLimit();
    Long beforeEntropyUsed3 = resourceInfo3.getEntropyUsed();
    Long beforeFreePhotonLimit3 = resourceInfo3.getFreePhotonLimit();
    Long beforePhotonLimit3 = resourceInfo3.getPhotonLimit();
    Long beforePhotonUsed3 = resourceInfo3.getPhotonUsed();
    Long beforeFreePhotonUsed3 = resourceInfo3.getFreePhotonUsed();
    logger.info("beforeBalance3:" + beforeBalance3);
    logger.info("beforeEntropyLimit3:" + beforeEntropyLimit3);
    logger.info("beforeEntropyUsed3:" + beforeEntropyUsed3);
    logger.info("beforeFreePhotonLimit3:" + beforeFreePhotonLimit3);
    logger.info("beforePhotonLimit3:" + beforePhotonLimit3);
    logger.info("beforePhotonUsed3:" + beforePhotonUsed3);
    logger.info("beforeFreePhotonUsed3:" + beforeFreePhotonUsed3);
    account = PublicMethed.queryAccount(linkage001Key, blockingStubFull);
    valueBalance = account.getBalance();
    txid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, payableAbi, payableCode,
            "", maxFeeLimit, valueBalance, 100, null, linkage001Key,
            linkage001Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    fee = infoById.get().getFee();
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    contractAddress = infoById.get().getContractAddress().toByteArray();
    Account infoafter3 = PublicMethed.queryAccount(linkage001Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter3 = PublicMethed.getAccountResource(linkage001Address,
        blockingStubFull1);
    Long afterBalance3 = infoafter3.getBalance();
    Long afterEntropyLimit3 = resourceInfoafter3.getEntropyLimit();
    Long afterEntropyUsed3 = resourceInfoafter3.getEntropyUsed();
    Long afterFreePhotonLimit3 = resourceInfoafter3.getFreePhotonLimit();
    Long afterPhotonLimit3 = resourceInfoafter3.getPhotonLimit();
    Long afterPhotonUsed3 = resourceInfoafter3.getPhotonUsed();
    Long afterFreePhotonUsed3 = resourceInfoafter3.getFreePhotonUsed();
    logger.info("afterBalance3:" + afterBalance3);
    logger.info("afterEntropyLimit3:" + afterEntropyLimit3);
    logger.info("afterEntropyUsed3:" + afterEntropyUsed3);
    logger.info("afterFreePhotonLimit3:" + afterFreePhotonLimit3);
    logger.info("afterPhotonLimit3:" + afterPhotonLimit3);
    logger.info("afterPhotonUsed3:" + afterPhotonUsed3);
    logger.info("afterFreePhotonUsed3:" + afterFreePhotonUsed3);

    Assert.assertTrue(afterPhotonUsed3 > 0);
    Assert.assertTrue(afterEntropyUsed3 > 0);
    Assert.assertTrue(afterFreePhotonUsed3 > 0);
    Assert.assertTrue(beforeBalance2 - fee == afterBalance2);
    Assert.assertTrue(afterBalance3 == 0);
    Assert.assertTrue(PublicMethed.queryAccount(contractAddress, blockingStubFull)
        .getBalance() == valueBalance);
    PublicMethed
        .unFreezeBalance(linkage001Address, linkage001Key, 1,
            linkage001Address, blockingStubFull);
  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed.freedResource(linkage001Address, linkage001Key, fromAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


