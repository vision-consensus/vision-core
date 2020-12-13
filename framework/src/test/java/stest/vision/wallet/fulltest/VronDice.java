package stest.vision.wallet.fulltest;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
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
import org.vision.protos.Protocol.TransactionInfo;
import org.vision.protos.contract.SmartContractOuterClass.SmartContract;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter.CommonConstant;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class VronDice {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  byte[] contractAddress;
  Long maxFeeLimit = 1000000000L;
  Optional<TransactionInfo> infoById = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contract008Address = ecKey1.getAddress();
  String contract008Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ArrayList<String> txidList = new ArrayList<String>();
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);

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
    PublicMethed.printAddress(contract008Key);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    PublicMethed.printAddress(testKey002);
    AccountResourceMessage accountResource = PublicMethed.getAccountResource(contract008Address,
        blockingStubFull);
  }

  @Test(enabled = true, threadPoolSize = 30, invocationCount = 30)
  public void vronDice() {
    ECKey ecKey1 = new ECKey(Utils.getRandom());
    byte[] vronDiceAddress = ecKey1.getAddress();
    String vronDiceKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
    PublicMethed
        .sendcoin(vronDiceAddress, 100000000000L, fromAddress, testKey002, blockingStubFull);
    String contractName = "VronDice";
    String code = Configuration.getByPath("testng.conf")
        .getString("code.code_VronDice_vronDice");
    String abi = Configuration.getByPath("testng.conf")
        .getString("abi.abi_VronDice_vronDice");
    byte[] contractAddress = PublicMethed.deployContract(contractName, abi, code, "",
        maxFeeLimit, 1000000000L, 100, null, vronDiceKey, vronDiceAddress, blockingStubFull);
    SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    Assert.assertTrue(smartContract.getAbi() != null);

    String txid;

    for (Integer i = 0; i < 100; i++) {
      String initParmes = "\"" + "10" + "\"";
      txid = PublicMethed.triggerContract(contractAddress,
          "rollDice(uint256)", initParmes, false,
          1000000, maxFeeLimit, vronDiceAddress, vronDiceKey, blockingStubFull);
      logger.info(txid);
      txidList.add(txid);

      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

    }

  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    try {
      Thread.sleep(20000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    Integer successTimes = 0;
    Integer failedTimes = 0;
    Integer totalTimes = 0;
    for (String txid1 : txidList) {
      totalTimes++;
      infoById = PublicMethed.getTransactionInfoById(txid1, blockingStubFull);
      if (infoById.get().getBlockNumber() > 3523732) {
        logger.info("blocknum is " + infoById.get().getBlockNumber());
        successTimes++;
      } else {
        failedTimes++;
      }
    }
    logger.info("Total times is " + totalTimes.toString());
    logger.info("success times is " + successTimes.toString());
    logger.info("failed times is " + failedTimes.toString());
    logger.info("success percent is " + successTimes / totalTimes);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


