package stest.vision.wallet.dailybuild.delaytransaction;

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
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter;
import stest.vision.wallet.common.client.utils.PublicMethed;

//import org.vision.protos.Protocol.DeferredTransaction;

@Slf4j
public class DelayTransaction011 {

  public static final long ONE_DELAY_SECONDS = 60 * 60 * 24L;
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  ECKey ecKey = new ECKey(Utils.getRandom());
  byte[] noPhotonAddress = ecKey.getAddress();
  String noPhotonKey = ByteArray.toHexString(ecKey.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] delayAccount2Address = ecKey2.getAddress();
  //Optional<DeferredTransaction> deferredTransactionById = null;
  String delayAccount2Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(1);
  private Long delayTransactionFee = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.delayTransactionFee");
  private Long cancleDelayTransactionFee = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.cancleDelayTransactionFee");

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

  @Test(enabled = false, description = "When Photon not enough, create delay transaction.")
  public void test1PhotonInDelayTransaction() {
    //get account
    ecKey = new ECKey(Utils.getRandom());
    noPhotonAddress = ecKey.getAddress();
    noPhotonKey = ByteArray.toHexString(ecKey.getPrivKeyBytes());
    PublicMethed.printAddress(noPhotonKey);
    ecKey2 = new ECKey(Utils.getRandom());
    delayAccount2Address = ecKey2.getAddress();
    delayAccount2Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
    PublicMethed.printAddress(delayAccount2Key);

    Assert.assertTrue(PublicMethed.sendcoin(noPhotonAddress, 10000000000L, fromAddress,
        testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    while (PublicMethed.queryAccount(noPhotonAddress, blockingStubFull).getFreePhotonUsage()
        < 4700L) {
      PublicMethed.sendcoin(delayAccount2Address, 1L, noPhotonAddress, noPhotonKey,
          blockingStubFull);
    }
    PublicMethed.sendcoin(delayAccount2Address, 1L, noPhotonAddress, noPhotonKey,
        blockingStubFull);
    PublicMethed.sendcoin(delayAccount2Address, 1L, noPhotonAddress, noPhotonKey,
        blockingStubFull);
    Assert.assertTrue(PublicMethed.sendcoin(fromAddress, PublicMethed.queryAccount(
            noPhotonAddress, blockingStubFull).getBalance() - 3000L, noPhotonAddress,
            noPhotonKey, blockingStubFull));
    logger.info("balance is: " + PublicMethed.queryAccount(noPhotonAddress,
        blockingStubFull).getBalance());
    logger.info("Free net usage is " + PublicMethed.queryAccount(noPhotonAddress,
        blockingStubFull).getFreePhotonUsage());

    String updateAccountName = "account_" + Long.toString(System.currentTimeMillis());
    byte[] accountNameBytes = ByteArray.fromString(updateAccountName);
    String txid = PublicMethed.updateAccountDelayGetTxid(noPhotonAddress, accountNameBytes,
        10L, noPhotonKey, blockingStubFull);
    logger.info(txid);
    Assert.assertTrue(PublicMethed.getTransactionById(txid, blockingStubFull)
        .get().getRawData().getContractCount() == 0);

    Assert.assertTrue(PublicMethed.sendcoin(noPhotonAddress, 103332L - 550L, fromAddress,
        testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    txid = PublicMethed.updateAccountDelayGetTxid(noPhotonAddress, accountNameBytes,
        10L, noPhotonKey, blockingStubFull);

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


