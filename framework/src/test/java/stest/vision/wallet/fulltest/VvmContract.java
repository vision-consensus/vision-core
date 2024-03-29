package stest.vision.wallet.fulltest;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter.CommonConstant;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class VvmContract {

  //testng001、testng002、testng003、testng004
  private final String testKey002 =
      "FC8BF0238748587B9617EB6D15D47A66C0E07C1A1959033CF249C6532DC29FE6";
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contract008Address = ecKey1.getAddress();
  String contract008Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = false)
  public void beforeClass() {
    PublicMethed.printAddress(contract008Key);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    Assert.assertTrue(PublicMethed.sendcoin(contract008Address, 500000000L, fromAddress,
        testKey002, blockingStubFull));
    logger.info(Long.toString(PublicMethed.queryAccount(contract008Key, blockingStubFull)
        .getBalance()));
    Assert.assertTrue(PublicMethed.freezeBalanceGetEntropy(contract008Address, 1000000L,
        3, 1, contract008Key, blockingStubFull));
    Assert.assertTrue(PublicMethed.buyStorage(50000000L, contract008Address, contract008Key,
        blockingStubFull));
    Assert.assertTrue(PublicMethed.freezeBalance(contract008Address, 5000000L,
        3, contract008Key, blockingStubFull));

  }

  @Test(enabled = false)
  public void deployErc721CryptoKitties() {
    AccountResourceMessage accountResource = PublicMethed.getAccountResource(contract008Address,
        blockingStubFull);
    Long entropyLimit = accountResource.getEntropyLimit();
    Long storageLimit = accountResource.getStorageLimit();
    Long entropyUsage = accountResource.getEntropyUsed();
    Long storageUsage = accountResource.getStorageUsed();

    logger.info("before entropy limit is " + Long.toString(entropyLimit));
    logger.info("before entropy usage is " + Long.toString(entropyUsage));
    logger.info("before storage limit is " + Long.toString(storageLimit));
    logger.info("before storage usaged is " + Long.toString(storageUsage));
    Long maxFeeLimit = 50000000L;
    String contractName = "ERC721";
    String code = Configuration.getByPath("testng.conf")
        .getString("code.code_VvmContract_deployErc721CryptoKitties");
    String abi = Configuration.getByPath("testng.conf")
        .getString("abi.abi_VvmContract_deployErc721CryptoKitties");
    Long m = 0L;
    Long freePhoton;
    accountResource = PublicMethed.getAccountResource(contract008Address, blockingStubFull);
    Long photon = accountResource.getFreePhotonUsed();
    Account account = PublicMethed.queryAccount(contract008Key, blockingStubFull);
    Long photonUsed = account.getPhotonUsage();
    logger.info("before photon used is " + Long.toString(photonUsed));
    logger.info("before balance is " + account.getBalance());

    for (Integer i = 0; i < 1; i++) {
      byte[] contractAddress = PublicMethed.deployContract("1", abi, code, "",
          30000000L, 0L, 1, null, contract008Key, contract008Address, blockingStubFull);
      accountResource = PublicMethed.getAccountResource(contract008Address, blockingStubFull);
      freePhoton = accountResource.getFreePhotonUsed();
      entropyUsage = accountResource.getEntropyUsed();
      logger.info(
          "Time " + Integer.toString(i) + ": entropy usage is " + Long.toString(entropyUsage - m));
      logger.info("Time " + Integer.toString(i) + ": free photon used is " + Long
          .toString(freePhoton - photon));
      account = PublicMethed.queryAccount(contract008Key, blockingStubFull);
      logger.info("after balance is " + account.getBalance());
      photonUsed = account.getPhotonUsage();
      logger.info("after photon used is " + Long.toString(photonUsed));
      photon = freePhoton;
      m = entropyUsage;
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

    }
    //SmartContract smartContract = PublicMethed.getContract(contractAddress,blockingStubFull);

    //Assert.assertFalse(smartContract.getAbi().toString().isEmpty());
    //Assert.assertTrue(smartContract.getName().equalsIgnoreCase(contractName));
    //Assert.assertFalse(smartContract.getBytecode().toString().isEmpty());
    //logger.info(smartContract.getName());
    //logger.info(smartContract.getAbi().toString());
    accountResource = PublicMethed.getAccountResource(contract008Address, blockingStubFull);
    entropyLimit = accountResource.getEntropyLimit();
    storageLimit = accountResource.getStorageLimit();
    entropyUsage = accountResource.getEntropyUsed();
    storageUsage = accountResource.getStorageUsed();
    //Assert.assertTrue(storageUsage > 0);
    //Assert.assertTrue(storageLimit > 0);
    //Assert.assertTrue(entropyLimit > 0);
    //Assert.assertTrue(entropyUsage > 0);

    logger.info("after entropy limit is " + Long.toString(entropyLimit));
    logger.info("after entropy usage is " + Long.toString(entropyUsage));
    logger.info("after storage limit is " + Long.toString(storageLimit));
    logger.info("after storage usaged is " + Long.toString(storageUsage));
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


