package stest.vision.wallet.dailybuild.assetissue;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
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
public class WalletTestAssetIssue007 {

  private static final long now = System.currentTimeMillis();
  private static final long totalSupply = now;
  private static final long sendAmount = 10000000000L;
  private static final long netCostMeasure = 200L;
  private static final Integer vsNum = 1;
  private static final Integer icoNum = 1;
  private static String name = "AssetIssue007_" + Long.toString(now);
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
  Long freeAssetPhotonLimit = 10000L;
  Long publicFreeAssetPhotonLimit = 10000L;
  String description = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.assetDescription");
  String url = Configuration.getByPath("testng.conf").getString("defaultParameter.assetUrl");
  //get account
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] asset007Address = ecKey1.getAddress();
  String testKeyForAssetIssue007 = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] participateAssetAddress = ecKey2.getAddress();
  String participateAssetCreateKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
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
    PublicMethed.printAddress(testKeyForAssetIssue007);
    PublicMethed.printAddress(participateAssetCreateKey);

    channelFull = ManagedChannelBuilder.forTarget(fullnode).usePlaintext(true).build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  @Test(enabled = true, description = "Participate asset issue use participate photon")
  public void testParticipateAssetIssueUseParticipatePhoton() {
    Assert.assertTrue(PublicMethed
        .sendcoin(asset007Address, sendAmount, fromAddress, testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Long start = System.currentTimeMillis() + 5000;
    Long end = System.currentTimeMillis() + 1000000000;
    Assert.assertTrue(PublicMethed
        .createAssetIssue(asset007Address, name, totalSupply, vsNum, icoNum, start, end, 1,
            description, url, freeAssetPhotonLimit, publicFreeAssetPhotonLimit, 1L, 1L,
            testKeyForAssetIssue007, blockingStubFull));

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info(name);
    //Assert.assertTrue(PublicMethed.waitProduceNextBlock(blockingStubFull));
    //When no balance, participate an asset issue
    Assert.assertFalse(PublicMethed
        .participateAssetIssue(asset007Address, name.getBytes(), 1L, participateAssetAddress,
            participateAssetCreateKey, blockingStubFull));

    ByteString addressBs = ByteString.copyFrom(asset007Address);
    Account request = Account.newBuilder().setAddress(addressBs).build();
    AccountPhotonMessage asset007NetMessage = blockingStubFull.getAccountPhoton(request);
    final Long asset007BeforeFreePhotonUsed = asset007NetMessage.getFreePhotonUsed();

    //SendCoin to participate account.
    Assert.assertTrue(PublicMethed
        .sendcoin(participateAssetAddress, 10000000L, fromAddress, testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    addressBs = ByteString.copyFrom(participateAssetAddress);
    request = Account.newBuilder().setAddress(addressBs).build();
    AccountPhotonMessage participateAccountPhotonMessage = blockingStubFull.getAccountPhoton(request);
    final Long participateAccountBeforePhotonUsed = participateAccountPhotonMessage.getFreePhotonUsed();
    Assert.assertTrue(participateAccountBeforePhotonUsed == 0);

    Account getAssetIdFromThisAccount;
    getAssetIdFromThisAccount = PublicMethed.queryAccount(asset007Address, blockingStubFull);
    ByteString assetAccountId = getAssetIdFromThisAccount.getAssetIssuedID();
    logger.info(assetAccountId.toString());

    //Participate an assetIssue, then query the net information.
    Assert.assertTrue(PublicMethed
        .participateAssetIssue(asset007Address, assetAccountId.toByteArray(), 1L,
            participateAssetAddress, participateAssetCreateKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    addressBs = ByteString.copyFrom(asset007Address);
    request = Account.newBuilder().setAddress(addressBs).build();
    asset007NetMessage = blockingStubFull.getAccountPhoton(request);
    final Long asset007AfterFreePhotonUsed = asset007NetMessage.getFreePhotonUsed();

    addressBs = ByteString.copyFrom(participateAssetAddress);
    request = Account.newBuilder().setAddress(addressBs).build();
    participateAccountPhotonMessage = blockingStubFull.getAccountPhoton(request);
    final Long participateAccountAfterPhotonUsed = participateAccountPhotonMessage.getFreePhotonUsed();

    logger.info(Long.toString(asset007BeforeFreePhotonUsed));
    logger.info(Long.toString(asset007AfterFreePhotonUsed));
    logger.info(Long.toString(participateAccountBeforePhotonUsed));
    logger.info(Long.toString(participateAccountAfterPhotonUsed));
    Assert.assertTrue(asset007AfterFreePhotonUsed <= asset007BeforeFreePhotonUsed);
    Assert.assertTrue(participateAccountAfterPhotonUsed - participateAccountBeforePhotonUsed > 150);

    Assert.assertTrue(PublicMethed
        .participateAssetIssue(asset007Address, assetAccountId.toByteArray(), 1L,
            participateAssetAddress, participateAssetCreateKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed
        .participateAssetIssue(asset007Address, assetAccountId.toByteArray(), 1L,
            participateAssetAddress, participateAssetCreateKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account participateInfo = PublicMethed
        .queryAccount(participateAssetCreateKey, blockingStubFull);
    final Long beforeBalance = participateInfo.getBalance();
    Assert.assertTrue(PublicMethed
        .participateAssetIssue(asset007Address, assetAccountId.toByteArray(), 1L,
            participateAssetAddress, participateAssetCreateKey, blockingStubFull));
    participateInfo = PublicMethed.queryAccount(participateAssetCreateKey, blockingStubFull);
    final Long afterBalance = participateInfo.getBalance();

    Assert.assertTrue(beforeBalance - vsNum * 1 * icoNum >= afterBalance);
  }

  @AfterMethod
  public void aftertest() {
    PublicMethed
        .freedResource(asset007Address, testKeyForAssetIssue007, fromAddress, blockingStubFull);
  }

  /**
   * constructor.
   */

  @AfterClass(enabled = true)
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


