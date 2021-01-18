package stest.vision.wallet.dailybuild.delaytransaction;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.vision.api.WalletGrpc;
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Utils;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Account;
import org.vision.protos.contract.SmartContractOuterClass.SmartContract;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class DelayTransaction006 {

  private static final long now = System.currentTimeMillis();
  private static final long totalSupply = now;
  private static final String name = "Asset008_" + Long.toString(now);
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
  String description = "just-test";
  String url = "https://github.com/vworldgenesis/wallet-cli/";
  Long delaySecond = 10L;
  ByteString assetId;
  SmartContract smartContract;
  ECKey ecKey = new ECKey(Utils.getRandom());
  byte[] assetOwnerAddress = ecKey.getAddress();
  String assetOwnerKey = ByteArray.toHexString(ecKey.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(1);
  private Long delayTransactionFee = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.delayTransactionFee");
  private Long cancleDelayTransactionFee = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.cancleDelayTransactionFee");
  private byte[] contractAddress = null;

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(Parameter.CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = false)
  public void beforeClass() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  @Test(enabled = false, description = "Delay update asset contract")
  public void test1DelayUpdateSetting() {
    //get account
    ecKey = new ECKey(Utils.getRandom());
    assetOwnerAddress = ecKey.getAddress();
    assetOwnerKey = ByteArray.toHexString(ecKey.getPrivKeyBytes());
    PublicMethed.printAddress(assetOwnerKey);

    Assert.assertTrue(PublicMethed.sendcoin(assetOwnerAddress, 2048000000, fromAddress,
        testKey002, blockingStubFull));

    final Long oldFreeAssetPhotonLimit = 2000L;
    Long start = System.currentTimeMillis() + 2000;
    Long end = System.currentTimeMillis() + 1000000000;
    Assert.assertTrue(PublicMethed.createAssetIssue(assetOwnerAddress,
        name, totalSupply, 1, 1, start, end, 1, description, url,
        oldFreeAssetPhotonLimit, 2000L, 1L, 1L,
        assetOwnerKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account assetOwnerAccount = PublicMethed.queryAccount(assetOwnerKey, blockingStubFull);
    assetId = assetOwnerAccount.getAssetIssuedID();
    String newAssetUrl = "new.url";
    String newAssetDescription = "new.description";

    final Long newFreeAssetPhotonLimit = 3333L;
    final String txid = PublicMethed.updateAssetDelay(assetOwnerAddress,
        newAssetDescription.getBytes(), newAssetUrl.getBytes(), newFreeAssetPhotonLimit,
        23L, delaySecond, assetOwnerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed.getAssetIssueById(ByteArray.toStr(assetId
        .toByteArray()), blockingStubFull).getFreeAssetPhotonLimit() == oldFreeAssetPhotonLimit);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed.getAssetIssueById(ByteArray.toStr(assetId
        .toByteArray()), blockingStubFull).getFreeAssetPhotonLimit() == newFreeAssetPhotonLimit);
    Long afterNetUsaged = PublicMethed.queryAccount(assetOwnerKey, blockingStubFull)
        .getFreePhotonUsage();
    Long photonFee = PublicMethed.getTransactionInfoById(txid, blockingStubFull).get()
        .getReceipt().getPhotonFee();
    Long fee = PublicMethed.getTransactionInfoById(txid, blockingStubFull).get().getFee();
    Long beforeNetUsaged = PublicMethed.queryAccount(assetOwnerKey, blockingStubFull)
        .getFreePhotonUsage();
    Long inDelayNetUsaged = PublicMethed.queryAccount(assetOwnerKey, blockingStubFull)
        .getFreePhotonUsage();
    Assert.assertTrue(fee - photonFee == delayTransactionFee);
    Assert.assertTrue(beforeNetUsaged + 50 < inDelayNetUsaged);
    Assert.assertTrue(inDelayNetUsaged + 50 < afterNetUsaged);

  }

  @Test(enabled = false, description = "Cancel delay asset setting contract")
  public void test2CancelDelayUpdateAsset() {
    //get account
    final Long oldFreeAssetPhotonLimit = PublicMethed.getAssetIssueById(assetId.toStringUtf8(),
        blockingStubFull).getFreeAssetPhotonLimit();
    final Long newFreeAssetPhotonLimit = 461L;

    String newAssetUrl = "new.url";
    String newAssetDescription = "new.description";
    logger.info("Before delay net usage: " + PublicMethed.queryAccount(assetOwnerKey,
        blockingStubFull).getFreePhotonUsage());
    String txid = PublicMethed.updateAssetDelay(assetOwnerAddress, newAssetDescription.getBytes(),
        newAssetUrl.getBytes(), newFreeAssetPhotonLimit, 23L, delaySecond, assetOwnerKey,
        blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info("In delay net usage: " + PublicMethed.queryAccount(assetOwnerKey, blockingStubFull)
        .getFreePhotonUsage());
    Assert.assertFalse(PublicMethed.cancelDeferredTransactionById(txid, fromAddress, testKey002,
        blockingStubFull));
    final String cancelTxid = PublicMethed.cancelDeferredTransactionByIdGetTxid(txid,
        assetOwnerAddress, assetOwnerKey, blockingStubFull);
    Assert.assertFalse(PublicMethed.cancelDeferredTransactionById(txid, assetOwnerAddress,
        assetOwnerKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    logger.info("After cancle net usage: " + PublicMethed.queryAccount(assetOwnerKey,
        blockingStubFull).getFreePhotonUsage());

    Assert.assertTrue(PublicMethed.getAssetIssueById(assetId.toStringUtf8(),
        blockingStubFull).getFreeAssetPhotonLimit() == oldFreeAssetPhotonLimit);

    final Long photonFee = PublicMethed.getTransactionInfoById(cancelTxid, blockingStubFull).get()
        .getReceipt().getPhotonFee();
    final Long fee = PublicMethed.getTransactionInfoById(cancelTxid, blockingStubFull).get()
        .getFee();
    logger.info("photon fee : " + PublicMethed.getTransactionInfoById(cancelTxid, blockingStubFull)
        .get().getReceipt().getPhotonFee());
    logger.info("Fee : " + PublicMethed.getTransactionInfoById(cancelTxid, blockingStubFull)
        .get().getFee());

    Assert.assertTrue(fee - photonFee == cancleDelayTransactionFee);

    Long afterNetUsaged = PublicMethed.queryAccount(assetOwnerKey, blockingStubFull)
        .getFreePhotonUsage();
    Long beforeNetUsaged = PublicMethed.queryAccount(assetOwnerKey, blockingStubFull)
        .getFreePhotonUsage();

    logger.info("beforeNetUsaged: " + beforeNetUsaged);
    logger.info("afterNetUsaged:  " + afterNetUsaged);
    Assert.assertTrue(beforeNetUsaged >= afterNetUsaged);

  }


  /**
   * constructor.
   */

  @AfterClass(enabled = false)
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


