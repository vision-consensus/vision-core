package stest.vision.wallet.dailybuild.assetissue;

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
import org.vision.api.GrpcAPI.AccountPhotonMessage;
import org.vision.api.WalletGrpc;
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Utils;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Account;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class WalletTestAssetIssue015 {

  private static final long now = System.currentTimeMillis();
  private static final long totalSupply = now;
  private static final long sendAmount = 10000000000L;
  private static final long netCostMeasure = 200L;
  private static String name = "AssetIssue015_" + Long.toString(now);
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
  Long freeAssetPhotonLimit = 30000L;
  Long publicFreeAssetPhotonLimit = 30000L;
  String description = "for case assetissue015";
  String url = "https://stest.assetissue015.url";
  ByteString assetAccountId;
  //get account
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] asset015Address = ecKey1.getAddress();
  String testKeyForAssetIssue015 = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] transferAssetAddress = ecKey2.getAddress();
  String transferAssetCreateKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  ECKey ecKey3 = new ECKey(Utils.getRandom());
  byte[] newAddress = ecKey3.getAddress();
  String testKeyForNewAddress = ByteArray.toHexString(ecKey3.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(Parameter.CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = true)
  public void beforeClass() {
    logger.info(testKeyForAssetIssue015);
    logger.info(transferAssetCreateKey);
    logger.info(testKeyForNewAddress);

    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  @Test(enabled = true, description = "Use transfer net when token owner has not enough photon")
  public void atestWhenCreatorHasNoEnoughPhotonUseTransferNet() {
    ecKey1 = new ECKey(Utils.getRandom());
    asset015Address = ecKey1.getAddress();
    testKeyForAssetIssue015 = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

    ecKey2 = new ECKey(Utils.getRandom());
    transferAssetAddress = ecKey2.getAddress();
    transferAssetCreateKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

    ecKey3 = new ECKey(Utils.getRandom());
    newAddress = ecKey3.getAddress();
    testKeyForNewAddress = ByteArray.toHexString(ecKey3.getPrivKeyBytes());

    Assert.assertTrue(PublicMethed
        .sendcoin(asset015Address, sendAmount, fromAddress, testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Long start = System.currentTimeMillis() + 2000;
    Long end = System.currentTimeMillis() + 1000000000;
    Assert.assertTrue(PublicMethed
        .createAssetIssue(asset015Address, name, totalSupply, 1, 1, start, end, 1, description,
            url, freeAssetPhotonLimit, publicFreeAssetPhotonLimit, 1L, 1L, testKeyForAssetIssue015,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account getAssetIdFromThisAccount;
    getAssetIdFromThisAccount = PublicMethed.queryAccount(asset015Address, blockingStubFull);
    assetAccountId = getAssetIdFromThisAccount.getAssetIssuedID();

    //Transfer asset to an account.
    Assert.assertTrue(PublicMethed
        .transferAsset(transferAssetAddress, assetAccountId.toByteArray(), 10000000L,
            asset015Address, testKeyForAssetIssue015, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    //Before use transfer net, query the net used from creator and transfer.
    AccountPhotonMessage assetCreatorNet = PublicMethed
        .getAccountPhoton(asset015Address, blockingStubFull);
    AccountPhotonMessage assetTransferNet = PublicMethed
        .getAccountPhoton(transferAssetAddress, blockingStubFull);
    Long creatorBeforeFreePhotonUsed = assetCreatorNet.getFreePhotonUsed();
    Long transferBeforeFreePhotonUsed = assetTransferNet.getFreePhotonUsed();
    logger.info(Long.toString(creatorBeforeFreePhotonUsed));
    logger.info(Long.toString(transferBeforeFreePhotonUsed));

    //Transfer send some asset issue to default account, to test if this
    // transaction use the transaction free net.
    Assert.assertTrue(PublicMethed.transferAsset(toAddress, assetAccountId.toByteArray(), 1L,
        transferAssetAddress, transferAssetCreateKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    assetCreatorNet = PublicMethed
        .getAccountPhoton(asset015Address, blockingStubFull);
    assetTransferNet = PublicMethed
        .getAccountPhoton(transferAssetAddress, blockingStubFull);
    Long creatorAfterFreePhotonUsed = assetCreatorNet.getFreePhotonUsed();
    Long transferAfterFreePhotonUsed = assetTransferNet.getFreePhotonUsed();
    logger.info(Long.toString(creatorAfterFreePhotonUsed));
    logger.info(Long.toString(transferAfterFreePhotonUsed));

    Assert.assertTrue(creatorAfterFreePhotonUsed - creatorBeforeFreePhotonUsed < netCostMeasure);
    Assert.assertTrue(transferAfterFreePhotonUsed - transferBeforeFreePhotonUsed > netCostMeasure);
  }

  @Test(enabled = true, description = "Use balance when transfer has not enough Photon")
  public void btestWhenTransferHasNoEnoughPhotonUseBalance() {
    Integer i = 0;
    AccountPhotonMessage assetTransferNet = PublicMethed
        .getAccountPhoton(transferAssetAddress, blockingStubFull);
    while (assetTransferNet.getPhotonUsed() < 4700 && i++ < 200) {
      PublicMethed.transferAsset(toAddress, assetAccountId.toByteArray(), 1L,
          transferAssetAddress, transferAssetCreateKey, blockingStubFull);
      assetTransferNet = PublicMethed
          .getAccountPhoton(transferAssetAddress, blockingStubFull);
    }

    logger.info(Long.toString(assetTransferNet.getFreePhotonUsed()));
    Assert.assertTrue(assetTransferNet.getFreePhotonUsed() >= 4700);

    Assert.assertTrue(PublicMethed.sendcoin(transferAssetAddress,
        20000000, fromAddress, testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account transferAccount = PublicMethed.queryAccount(transferAssetCreateKey, blockingStubFull);
    Long beforeBalance = transferAccount.getBalance();
    logger.info(Long.toString(beforeBalance));

    Assert.assertTrue(PublicMethed.transferAsset(toAddress, assetAccountId.toByteArray(), 1L,
        transferAssetAddress, transferAssetCreateKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    transferAccount = PublicMethed.queryAccount(transferAssetCreateKey, blockingStubFull);
    Long afterBalance = transferAccount.getBalance();
    logger.info(Long.toString(afterBalance));

    Assert.assertTrue(beforeBalance - afterBalance > 2000);
  }

  @Test(enabled = true, description = "Transfer asset use Photon when freeze balance")
  public void ctestWhenFreezeBalanceUseNet() {
    Assert.assertTrue(PublicMethed.freezeBalance(transferAssetAddress, 5000000,
        3, transferAssetCreateKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    AccountPhotonMessage assetTransferNet = PublicMethed
        .getAccountPhoton(transferAssetAddress, blockingStubFull);
    Account transferAccount = PublicMethed.queryAccount(transferAssetCreateKey, blockingStubFull);

    final Long transferPhotonUsedBefore = assetTransferNet.getPhotonUsed();
    final Long transferBalanceBefore = transferAccount.getBalance();
    logger.info("before  " + Long.toString(transferBalanceBefore));

    Assert.assertTrue(PublicMethed.transferAsset(toAddress, assetAccountId.toByteArray(), 1L,
        transferAssetAddress, transferAssetCreateKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    assetTransferNet = PublicMethed
        .getAccountPhoton(transferAssetAddress, blockingStubFull);
    transferAccount = PublicMethed.queryAccount(transferAssetCreateKey, blockingStubFull);
    final Long transferPhotonUsedAfter = assetTransferNet.getPhotonUsed();
    final Long transferBalanceAfter = transferAccount.getBalance();
    logger.info("after " + Long.toString(transferBalanceAfter));

    Assert.assertTrue(transferBalanceAfter - transferBalanceBefore == 0);
    Assert.assertTrue(transferPhotonUsedAfter - transferPhotonUsedBefore > 200);


  }


  /**
   * constructor.
   */

  @AfterClass(enabled = true)
  public void shutdown() throws InterruptedException {
    PublicMethed
        .freedResource(asset015Address, testKeyForAssetIssue015, fromAddress, blockingStubFull);
    PublicMethed
        .freedResource(transferAssetAddress, transferAssetCreateKey, fromAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


