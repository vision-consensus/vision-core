package stest.vision.wallet.dailybuild.manual;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.vision.api.WalletGrpc;
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Utils;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.TransactionInfo;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter.CommonConstant;
import stest.vision.wallet.common.client.utils.Base58;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractScenario015 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  byte[] contractAddress1 = null;
  byte[] contractAddress2 = null;
  byte[] contractAddress3 = null;
  String txid = "";
  Optional<TransactionInfo> infoById = null;
  String contractName = "";
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contract014Address = ecKey1.getAddress();
  String contract014Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] receiverAddress = ecKey2.getAddress();
  String receiverKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
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
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  @Test(enabled = true, description = "VISION VRC20 transfer token")
  public void vrc20Vision() {
    ecKey1 = new ECKey(Utils.getRandom());
    contract014Address = ecKey1.getAddress();
    contract014Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

    ecKey2 = new ECKey(Utils.getRandom());
    receiverAddress = ecKey2.getAddress();
    receiverKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
    PublicMethed.printAddress(contract014Key);
    PublicMethed.printAddress(receiverKey);

    Assert.assertTrue(PublicMethed.sendcoin(contract014Address, 500000000L, fromAddress,
        testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    //Deploy contract1, contract1 has a function to transaction 5 vdt to target account
    String contractName = "VISION VRC20";
    String code = Configuration.getByPath("testng.conf")
        .getString("code.code_Scenario015_VRC20_VISION");
    String abi = Configuration.getByPath("testng.conf")
        .getString("abi.abi_Scenario015_VRC20_VISION");
    txid = PublicMethed.deployContractAndGetTransactionInfoById(contractName, abi, code, "",
        maxFeeLimit, 0L, 100, null, contract014Key, contract014Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    logger.info(txid);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    contractAddress1 = infoById.get().getContractAddress().toByteArray();
    //Set SiringAuctionAddress to kitty core.
    String siringContractString = "\"" + Base58.encode58Check(fromAddress) + "\"";
    txid = PublicMethed
        .triggerContract(contractAddress1, "balanceOf(address)", siringContractString,
            false, 0, 10000000L, contract014Address, contract014Key, blockingStubFull);
    logger.info(txid);

    siringContractString = "\"" + Base58.encode58Check(fromAddress) + "\",\"" + 17 + "\"";
    txid = PublicMethed.triggerContract(contractAddress1, "transfer(address,uint256)",
        siringContractString, false, 0, 10000000L, contract014Address,
        contract014Key, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = null;

    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);

    siringContractString = "\"" + Base58.encode58Check(fromAddress) + "\"";
    txid = PublicMethed
        .triggerContract(contractAddress1, "balanceOf(address)",
            siringContractString, false, 0, 10000000L, contract014Address,
            contract014Key, blockingStubFull);
    logger.info(txid);
  }


  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed.freedResource(contract014Address, contract014Key, fromAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


