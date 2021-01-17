package stest.vision.wallet.contract.linkage;

import static org.vision.protos.Protocol.Transaction.Result.contractResult.SUCCESS_VALUE;

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
import org.vision.protos.Protocol.Transaction.Result.contractResult;
import org.vision.protos.Protocol.TransactionInfo;
import org.vision.protos.contract.SmartContractOuterClass.SmartContract;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter.CommonConstant;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractLinkage002 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] linkage002Address = ecKey1.getAddress();
  String linkage002Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
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
    PublicMethed.printAddress(linkage002Key);
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
  public void updateSetting() {
    String sendcoin = PublicMethed
        .sendcoinGetTransactionId(linkage002Address, 200000000000L, fromAddress,
            testKey002, blockingStubFull);
    Account info;
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById0 = null;
    infoById0 = PublicMethed.getTransactionInfoById(sendcoin, blockingStubFull);
    logger.info("infoById0   " + infoById0.get());
    Assert.assertEquals(ByteArray.toHexString(infoById0.get().getContractResult(0).toByteArray()),
        "");
    Assert.assertEquals(infoById0.get().getResult().getNumber(), 0);
    Optional<Transaction> ById = PublicMethed.getTransactionById(sendcoin, blockingStubFull);
    Assert.assertEquals(ById.get().getRet(0).getContractRet().getNumber(),
        SUCCESS_VALUE);
    Assert.assertEquals(ById.get().getRet(0).getContractRetValue(), SUCCESS_VALUE);
    Assert.assertEquals(ById.get().getRet(0).getContractRet(), contractResult.SUCCESS);

    Assert.assertTrue(PublicMethed.freezeBalanceGetEntropy(linkage002Address, 50000000L,
        3, 1, linkage002Key, blockingStubFull));
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(linkage002Address,
        blockingStubFull);
    info = PublicMethed.queryAccount(linkage002Address, blockingStubFull);
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

    String filePath = "./src/test/resources/soliditycode/contractLinkage002.sol";
    String contractName = "divideIHaveArgsReturnStorage";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    //Set the consumeUserResourcePercent is -1,Nothing change.
    byte[] contractAddress;
    contractAddress = PublicMethed.deployContract(contractName, abi, code, "",
        maxFeeLimit, 0L, -1, null, linkage002Key, linkage002Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account infoafter = PublicMethed.queryAccount(linkage002Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(linkage002Address,
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
    Assert.assertEquals(beforeBalance, afterBalance);
    Assert.assertTrue(afterNetUsed == 0);
    Assert.assertTrue(afterEntropyUsed == 0);
    Assert.assertTrue(afterFreeNetUsed > 0);

    //Set the consumeUserResourcePercent is 101,Nothing change.
    AccountResourceMessage resourceInfo3 = PublicMethed.getAccountResource(linkage002Address,
        blockingStubFull);
    Account info3 = PublicMethed.queryAccount(linkage002Address, blockingStubFull);
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

    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 101, null, linkage002Key, linkage002Address, blockingStubFull);
    Account infoafter3 = PublicMethed.queryAccount(linkage002Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter3 = PublicMethed.getAccountResource(linkage002Address,
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

    Assert.assertEquals(beforeBalance3, afterBalance3);
    Assert.assertTrue(afterNetUsed3 == 0);
    Assert.assertTrue(afterEntropyUsed3 == 0);
    Assert.assertTrue(afterFreeNetUsed3 > 0);

    //Set consumeUserResourcePercent is 100,balance not change,use FreeNet freezeBalanceGetEntropy.

    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 100, null, linkage002Key, linkage002Address, blockingStubFull);
    SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    Assert.assertTrue(smartContract.getConsumeUserResourcePercent() == 100);

    //Set the consumeUserResourcePercent is 0,balance not change,use FreeNet freezeBalanceGetEntropy.
    AccountResourceMessage resourceInfo2 = PublicMethed.getAccountResource(linkage002Address,
        blockingStubFull);
    Account info2 = PublicMethed.queryAccount(linkage002Address, blockingStubFull);
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

    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 0, null, linkage002Key, linkage002Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account infoafter2 = PublicMethed.queryAccount(linkage002Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter2 = PublicMethed.getAccountResource(linkage002Address,
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

    Assert.assertEquals(beforeBalance2, afterBalance2);
    Assert.assertTrue(afterNetUsed2 == 0);
    Assert.assertTrue(afterEntropyUsed2 > 0);
    Assert.assertTrue(afterFreeNetUsed2 > 0);
    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    Assert.assertTrue(smartContract.getConsumeUserResourcePercent() == 0);

    //Update the consumeUserResourcePercent setting.
    Assert.assertTrue(PublicMethed.updateSetting(contractAddress, 66L,
        linkage002Key, linkage002Address, blockingStubFull));
    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    Assert.assertTrue(smartContract.getConsumeUserResourcePercent() == 66);

    //Updaate the consumeUserResourcePercent setting with -1 and 101
    Assert.assertFalse(PublicMethed.updateSetting(contractAddress, -1L,
        linkage002Key, linkage002Address, blockingStubFull));
    Assert.assertFalse(PublicMethed.updateSetting(contractAddress, 101L,
        linkage002Key, linkage002Address, blockingStubFull));

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


