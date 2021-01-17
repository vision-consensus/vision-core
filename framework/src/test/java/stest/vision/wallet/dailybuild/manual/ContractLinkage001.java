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
    Long beforeNetLimit = resourceInfo.getPhotonLimit();
    Long beforeFreeNetLimit = resourceInfo.getFreePhotonLimit();
    Long beforeFreeNetUsed = resourceInfo.getFreePhotonUsed();
    Long beforeNetUsed = resourceInfo.getPhotonUsed();
    Long beforeEntropyLimit = resourceInfo.getEntropyLimit();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyLimit:" + beforeEntropyLimit);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforeFreeNetLimit:" + beforeFreeNetLimit);
    logger.info("beforeNetLimit:" + beforeNetLimit);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);

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
    Long netUsed = infoById.get().getReceipt().getPhotonUsage();
    Long entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    Long photonFee = infoById.get().getReceipt().getPhotonFee();
    logger.info("entropyUsageTotal:" + entropyUsageTotal);
    logger.info("fee:" + fee);
    logger.info("entropyFee:" + entropyFee);
    logger.info("netUsed:" + netUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);

    Account infoafter = PublicMethed.queryAccount(linkage001Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(linkage001Address,
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

    Assert.assertTrue(infoById.get().getResultValue() == 1);
    Assert.assertEquals(beforeBalance, afterBalance);
    Assert.assertTrue(fee == 0);
    Assert.assertTrue(afterNetUsed == 0);
    Assert.assertTrue(afterEntropyUsed == 0);
    Assert.assertTrue(afterFreeNetUsed > 0);

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
    Long netUsed1 = infoById1.get().getReceipt().getPhotonUsage();
    Long entropyUsed1 = infoById1.get().getReceipt().getEntropyUsage();
    Long photonFee1 = infoById1.get().getReceipt().getPhotonFee();
    logger.info("entropyUsageTotal1:" + entropyUsageTotal1);
    logger.info("fee1:" + fee1);
    logger.info("entropyFee1:" + entropyFee1);
    logger.info("netUsed1:" + netUsed1);
    logger.info("entropyUsed1:" + entropyUsed1);
    logger.info("photonFee1:" + photonFee1);
    Assert.assertTrue(infoById1.get().getResultValue() == 0);

    Account infoafter1 = PublicMethed.queryAccount(linkage001Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter1 = PublicMethed.getAccountResource(linkage001Address,
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

    Assert.assertTrue(beforeBalance1 - fee1 - 1L == afterBalance1);
    byte[] contractAddress = infoById1.get().getContractAddress().toByteArray();
    Account account = PublicMethed.queryAccount(contractAddress, blockingStubFull);
    Assert.assertTrue(account.getBalance() == 1L);
    Assert.assertTrue(afterNetUsed1 == 0);
    Assert.assertTrue(afterEntropyUsed1 > 0);
    Assert.assertTrue(afterFreeNetUsed1 > 0);

    //Value is account all balance plus 1. balance is not sufficient,Nothing changde.
    AccountResourceMessage resourceInfo2 = PublicMethed.getAccountResource(linkage001Address,
        blockingStubFull);
    Account info2 = PublicMethed.queryAccount(linkage001Address, blockingStubFull);
    Long beforeBalance2 = info2.getBalance();
    Long beforeEntropyLimit2 = resourceInfo2.getEntropyLimit();
    Long beforeEntropyUsed2 = resourceInfo2.getEntropyUsed();
    Long beforeFreeNetLimit2 = resourceInfo2.getFreePhotonLimit();
    Long beforeNetLimit2 = resourceInfo2.getPhotonLimit();
    Long beforeNetUsed2 = resourceInfo2.getPhotonUsed();
    Long beforeFreeNetUsed2 = resourceInfo2.getFreePhotonUsed();
    logger.info("beforeBalance2:" + beforeBalance2);
    logger.info("beforeEntropyLimit2:" + beforeEntropyLimit2);
    logger.info("beforeEntropyUsed2:" + beforeEntropyUsed2);
    logger.info("beforeFreeNetLimit2:" + beforeFreeNetLimit2);
    logger.info("beforeNetLimit2:" + beforeNetLimit2);
    logger.info("beforeNetUsed2:" + beforeNetUsed2);
    logger.info("beforeFreeNetUsed2:" + beforeFreeNetUsed2);

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
    Long afterFreeNetLimit2 = resourceInfoafter2.getFreePhotonLimit();
    Long afterNetLimit2 = resourceInfoafter2.getPhotonLimit();
    Long afterNetUsed2 = resourceInfoafter2.getPhotonUsed();
    Long afterFreeNetUsed2 = resourceInfoafter2.getFreePhotonUsed();
    logger.info("afterBalance2:" + afterBalance2);
    logger.info("afterEntropyLimit2:" + afterEntropyLimit2);
    logger.info("afterEntropyUsed2:" + afterEntropyUsed2);
    logger.info("afterFreeNetLimit2:" + afterFreeNetLimit2);
    logger.info("afterNetLimit2:" + afterNetLimit2);
    logger.info("afterNetUsed2:" + afterNetUsed2);
    logger.info("afterFreeNetUsed2:" + afterFreeNetUsed2);
    Assert.assertTrue(afterNetUsed2 == 0);
    Assert.assertTrue(afterEntropyUsed2 > 0);
    Assert.assertTrue(afterFreeNetUsed2 > 0);
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
    Long beforeFreeNetLimit3 = resourceInfo3.getFreePhotonLimit();
    Long beforeNetLimit3 = resourceInfo3.getPhotonLimit();
    Long beforeNetUsed3 = resourceInfo3.getPhotonUsed();
    Long beforeFreeNetUsed3 = resourceInfo3.getFreePhotonUsed();
    logger.info("beforeBalance3:" + beforeBalance3);
    logger.info("beforeEntropyLimit3:" + beforeEntropyLimit3);
    logger.info("beforeEntropyUsed3:" + beforeEntropyUsed3);
    logger.info("beforeFreeNetLimit3:" + beforeFreeNetLimit3);
    logger.info("beforeNetLimit3:" + beforeNetLimit3);
    logger.info("beforeNetUsed3:" + beforeNetUsed3);
    logger.info("beforeFreeNetUsed3:" + beforeFreeNetUsed3);
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
    Long afterFreeNetLimit3 = resourceInfoafter3.getFreePhotonLimit();
    Long afterNetLimit3 = resourceInfoafter3.getPhotonLimit();
    Long afterNetUsed3 = resourceInfoafter3.getPhotonUsed();
    Long afterFreeNetUsed3 = resourceInfoafter3.getFreePhotonUsed();
    logger.info("afterBalance3:" + afterBalance3);
    logger.info("afterEntropyLimit3:" + afterEntropyLimit3);
    logger.info("afterEntropyUsed3:" + afterEntropyUsed3);
    logger.info("afterFreeNetLimit3:" + afterFreeNetLimit3);
    logger.info("afterNetLimit3:" + afterNetLimit3);
    logger.info("afterNetUsed3:" + afterNetUsed3);
    logger.info("afterFreeNetUsed3:" + afterFreeNetUsed3);

    Assert.assertTrue(afterNetUsed3 > 0);
    Assert.assertTrue(afterEntropyUsed3 > 0);
    Assert.assertTrue(afterFreeNetUsed3 > 0);
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


