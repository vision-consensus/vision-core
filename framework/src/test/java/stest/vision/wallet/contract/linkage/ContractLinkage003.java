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
import org.vision.protos.contract.SmartContractOuterClass.SmartContract;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter.CommonConstant;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractLinkage003 {

  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey003);
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] linkage003Address = ecKey1.getAddress();
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
  public void deployWhenNoEntropy() {
    Assert.assertTrue(PublicMethed.sendcoin(linkage003Address, 200000000L, fromAddress,
        testKey003, blockingStubFull));
    Account info;
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(linkage003Address,
        blockingStubFull);
    info = PublicMethed.queryAccount(linkage003Address, blockingStubFull);
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

    String filePath = "src/test/resources/soliditycode//contractLinkage003.sol";
    String contractName = "divideIHaveArgsReturnStorage";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    //use FreeNet and balance,EntropyUsed==0.
    String txid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit,
            0L, 0, null, linkage002Key, linkage003Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    byte[] contractAddress = infoById.get().getContractAddress().toByteArray();
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

    Account infoafter = PublicMethed.queryAccount(linkage003Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(linkage003Address,
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

    SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    Assert.assertFalse(smartContract.getName().isEmpty());
    Assert.assertTrue((beforeBalance - fee) == afterBalance);
    Assert.assertTrue(afterEntropyUsed == 0L);
    Assert.assertTrue(afterFreePhotonUsed > 0L);
    Assert.assertTrue(afterPhotonUsed == 0L);


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


