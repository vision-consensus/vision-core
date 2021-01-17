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
public class ContractLinkage007 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  String contractName;
  String code;
  String abi;
  byte[] contractAddress;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] linkage007Address = ecKey1.getAddress();
  String linkage007Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
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
    PublicMethed.printAddress(linkage007Key);
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
  public void testRangeOfFeeLimit() {

    //Now the feelimit range is 0-1000000000,including 0 and 1000000000
    Assert.assertTrue(PublicMethed.sendcoin(linkage007Address, 2000000000L, fromAddress,
        testKey002, blockingStubFull));

    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(linkage007Address,
        blockingStubFull);
    Account info;
    info = PublicMethed.queryAccount(linkage007Address, blockingStubFull);
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
    //When the feelimit is large, the deploy will be failed,No used everything.

    String filePath = "./src/test/resources/soliditycode/contractLinkage002.sol";
    String contractName = "divideIHaveArgsReturnStorage";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    String txid;
    txid = PublicMethed.deployContractAndGetTransactionInfoById(contractName, abi, code,
        "", maxFeeLimit + 1, 0L, 100, null, linkage007Key,
        linkage007Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account infoafter = PublicMethed.queryAccount(linkage007Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(linkage007Address,
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
    Assert.assertTrue(afterEntropyUsed == 0);
    Assert.assertTrue(afterNetUsed == 0);
    Assert.assertTrue(afterFreeNetUsed == 0);

    Assert.assertTrue(txid == null);
    AccountResourceMessage resourceInfo1 = PublicMethed.getAccountResource(linkage007Address,
        blockingStubFull);
    Account info1 = PublicMethed.queryAccount(linkage007Address, blockingStubFull);
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
    //When the feelimit is 0, the deploy will be failed.Only use FreeNet,balance not change.
    txid = PublicMethed.deployContractAndGetTransactionInfoById(contractName, abi, code,
        "", 0L, 0L, 100, null, linkage007Key,
        linkage007Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account infoafter1 = PublicMethed.queryAccount(linkage007Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter1 = PublicMethed.getAccountResource(linkage007Address,
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
    Assert.assertEquals(beforeBalance1, afterBalance1);
    Assert.assertTrue(afterFreeNetUsed1 > 0);
    Assert.assertTrue(afterNetUsed1 == 0);
    Assert.assertTrue(afterEntropyUsed1 == 0);
    Optional<TransactionInfo> infoById;

    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 1);

    //Deploy the contract.success.use FreeNet,EntropyFee.balcne change
    AccountResourceMessage resourceInfo2 = PublicMethed.getAccountResource(linkage007Address,
        blockingStubFull);
    Account info2 = PublicMethed.queryAccount(linkage007Address, blockingStubFull);
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
    txid = PublicMethed.deployContractAndGetTransactionInfoById(contractName, abi, code,
        "", maxFeeLimit, 0L, 100, null, linkage007Key,
        linkage007Address, blockingStubFull);
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
    Account infoafter2 = PublicMethed.queryAccount(linkage007Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter2 = PublicMethed.getAccountResource(linkage007Address,
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
    logger.info("---------------:");
    Assert.assertTrue((beforeBalance2 - fee2) == afterBalance2);
    Assert.assertTrue(afterEntropyUsed2 == 0);
    Assert.assertTrue(afterFreeNetUsed2 > beforeFreeNetUsed2);
    Assert.assertTrue(infoById2.get().getResultValue() == 0);
    contractAddress = infoById2.get().getContractAddress().toByteArray();

    //When the feelimit is large, the trigger will be failed.Only use FreeNetUsed,Balance not change
    AccountResourceMessage resourceInfo3 = PublicMethed.getAccountResource(linkage007Address,
        blockingStubFull);
    Account info3 = PublicMethed.queryAccount(linkage007Address, blockingStubFull);
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
    //String initParmes = "\"" + Base58.encode58Check(fromAddress) + "\",\"63\"";
    String num = "4" + "," + "2";
    txid = PublicMethed.triggerContract(contractAddress,
        "divideIHaveArgsReturn(int256,int256)", num, false,
        1000, maxFeeLimit + 1, linkage007Address, linkage007Key, blockingStubFull);
    Account infoafter3 = PublicMethed.queryAccount(linkage007Address, blockingStubFull1);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    AccountResourceMessage resourceInfoafter3 = PublicMethed.getAccountResource(linkage007Address,
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
    logger.info("---------------:");
    Assert.assertTrue(txid == null);
    Assert.assertEquals(beforeBalance3, afterBalance3);
    Assert.assertTrue(afterFreeNetUsed3 > beforeNetUsed3);
    Assert.assertTrue(afterNetUsed3 == 0);
    Assert.assertTrue(afterEntropyUsed3 == 0);
    //When the feelimit is 0, the trigger will be failed.Only use FreeNetUsed,Balance not change
    AccountResourceMessage resourceInfo4 = PublicMethed.getAccountResource(linkage007Address,
        blockingStubFull);
    Account info4 = PublicMethed.queryAccount(linkage007Address, blockingStubFull);
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
        "divideIHaveArgsReturn(int256,int256)", num, false,
        1000, maxFeeLimit + 1, linkage007Address, linkage007Key, blockingStubFull);
    Account infoafter4 = PublicMethed.queryAccount(linkage007Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter4 = PublicMethed.getAccountResource(linkage007Address,
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
    Assert.assertEquals(beforeBalance4, afterBalance4);
    Assert.assertTrue(afterFreeNetUsed4 > beforeNetUsed4);
    Assert.assertTrue(afterNetUsed4 == 0);
    Assert.assertTrue(afterEntropyUsed4 == 0);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info(Integer.toString(infoById.get().getResultValue()));
    Assert.assertTrue(infoById.get().getFee() == 0);
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


