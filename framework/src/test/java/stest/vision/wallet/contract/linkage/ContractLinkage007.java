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
    Assert.assertEquals(beforeBalance, afterBalance);
    Assert.assertTrue(afterEntropyUsed == 0);
    Assert.assertTrue(afterPhotonUsed == 0);
    Assert.assertTrue(afterFreePhotonUsed == 0);

    Assert.assertTrue(txid == null);
    AccountResourceMessage resourceInfo1 = PublicMethed.getAccountResource(linkage007Address,
        blockingStubFull);
    Account info1 = PublicMethed.queryAccount(linkage007Address, blockingStubFull);
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
    //When the feelimit is 0, the deploy will be failed.Only use FreePhoton,balance not change.
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
    Assert.assertEquals(beforeBalance1, afterBalance1);
    Assert.assertTrue(afterFreePhotonUsed1 > 0);
    Assert.assertTrue(afterPhotonUsed1 == 0);
    Assert.assertTrue(afterEntropyUsed1 == 0);
    Optional<TransactionInfo> infoById;

    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 1);

    //Deploy the contract.success.use FreePhoton,EntropyFee.balcne change
    AccountResourceMessage resourceInfo2 = PublicMethed.getAccountResource(linkage007Address,
        blockingStubFull);
    Account info2 = PublicMethed.queryAccount(linkage007Address, blockingStubFull);
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
    logger.info("---------------:");
    Assert.assertTrue((beforeBalance2 - fee2) == afterBalance2);
    Assert.assertTrue(afterEntropyUsed2 == 0);
    Assert.assertTrue(afterFreePhotonUsed2 > beforeFreePhotonUsed2);
    Assert.assertTrue(infoById2.get().getResultValue() == 0);
    contractAddress = infoById2.get().getContractAddress().toByteArray();

    //When the feelimit is large, the trigger will be failed.Only use FreePhotonUsed,Balance not change
    AccountResourceMessage resourceInfo3 = PublicMethed.getAccountResource(linkage007Address,
        blockingStubFull);
    Account info3 = PublicMethed.queryAccount(linkage007Address, blockingStubFull);
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
    logger.info("---------------:");
    Assert.assertTrue(txid == null);
    Assert.assertEquals(beforeBalance3, afterBalance3);
    Assert.assertTrue(afterFreePhotonUsed3 > beforePhotonUsed3);
    Assert.assertTrue(afterPhotonUsed3 == 0);
    Assert.assertTrue(afterEntropyUsed3 == 0);
    //When the feelimit is 0, the trigger will be failed.Only use FreePhotonUsed,Balance not change
    AccountResourceMessage resourceInfo4 = PublicMethed.getAccountResource(linkage007Address,
        blockingStubFull);
    Account info4 = PublicMethed.queryAccount(linkage007Address, blockingStubFull);
    Long beforeBalance4 = info4.getBalance();
    Long beforeEntropyLimit4 = resourceInfo4.getEntropyLimit();
    Long beforeEntropyUsed4 = resourceInfo4.getEntropyUsed();
    Long beforeFreePhotonLimit4 = resourceInfo4.getFreePhotonLimit();
    Long beforePhotonLimit4 = resourceInfo4.getPhotonLimit();
    Long beforePhotonUsed4 = resourceInfo4.getPhotonUsed();
    Long beforeFreePhotonUsed4 = resourceInfo4.getFreePhotonUsed();
    logger.info("beforeBalance4:" + beforeBalance4);
    logger.info("beforeEntropyLimit4:" + beforeEntropyLimit4);
    logger.info("beforeEntropyUsed4:" + beforeEntropyUsed4);
    logger.info("beforeFreePhotonLimit4:" + beforeFreePhotonLimit4);
    logger.info("beforePhotonLimit4:" + beforePhotonLimit4);
    logger.info("beforePhotonUsed4:" + beforePhotonUsed4);
    logger.info("beforeFreePhotonUsed4:" + beforeFreePhotonUsed4);
    txid = PublicMethed.triggerContract(contractAddress,
        "divideIHaveArgsReturn(int256,int256)", num, false,
        1000, maxFeeLimit + 1, linkage007Address, linkage007Key, blockingStubFull);
    Account infoafter4 = PublicMethed.queryAccount(linkage007Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter4 = PublicMethed.getAccountResource(linkage007Address,
        blockingStubFull1);
    Long afterBalance4 = infoafter4.getBalance();
    Long afterEntropyLimit4 = resourceInfoafter4.getEntropyLimit();
    Long afterEntropyUsed4 = resourceInfoafter4.getEntropyUsed();
    Long afterFreePhotonLimit4 = resourceInfoafter4.getFreePhotonLimit();
    Long afterPhotonLimit4 = resourceInfoafter4.getPhotonLimit();
    Long afterPhotonUsed4 = resourceInfoafter4.getPhotonUsed();
    Long afterFreePhotonUsed4 = resourceInfoafter4.getFreePhotonUsed();
    logger.info("afterBalance4:" + afterBalance4);
    logger.info("afterEntropyLimit4:" + afterEntropyLimit4);
    logger.info("afterEntropyUsed4:" + afterEntropyUsed4);
    logger.info("afterFreePhotonLimit4:" + afterFreePhotonLimit4);
    logger.info("afterPhotonLimit4:" + afterPhotonLimit4);
    logger.info("afterPhotonUsed4:" + afterPhotonUsed4);
    logger.info("afterFreePhotonUsed4:" + afterFreePhotonUsed4);
    logger.info("---------------:");
    Assert.assertEquals(beforeBalance4, afterBalance4);
    Assert.assertTrue(afterFreePhotonUsed4 > beforePhotonUsed4);
    Assert.assertTrue(afterPhotonUsed4 == 0);
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


