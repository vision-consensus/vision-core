package stest.vision.wallet.dailybuild.vvmnewcommand.shiftcommand;

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
import org.vision.api.WalletSolidityGrpc;
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
public class ShiftCommand002 {

  private final String testNetAccountKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] testNetAccountAddress = PublicMethed.getFinalAddress(testNetAccountKey);
  byte[] contractAddress = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contractExcAddress = ecKey1.getAddress();
  String contractExcKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private ManagedChannel channelSolidity = null;
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
    System.out.println("code can not fount in git");
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = true)
  public void beforeClass() {
    PublicMethed.printAddress(contractExcKey);
    channelFull = ManagedChannelBuilder.forTarget(fullnode).usePlaintext(true).build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1).usePlaintext(true).build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);

    channelSolidity = ManagedChannelBuilder.forTarget(soliditynode).usePlaintext(true).build();
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);
  }


  @Test(enabled = true, description = "OLd compiler compile shift instruction")
  public void test1Oldcompile() {
    Assert.assertTrue(PublicMethed
        .sendcoin(contractExcAddress, 10000000000L, testNetAccountAddress, testNetAccountKey,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String contractName = "TestBitwiseShift";
    String code = "608060405234801561001057600080fd5b50d3801561001d57600080fd5b50d2801561002a5"
        + "7600080fd5b506101478061003a6000396000f3006080604052600436106100565763ffffffff7c0100"
        + "000000000000000000000000000000000000000000000000000000600035041663614eb4da811461005"
        + "b578063b5675d6d146100a2578063baf27c0c146100d7575b600080fd5b34801561006757600080fd5b"
        + "50d3801561007457600080fd5b50d2801561008157600080fd5b5061009060043560243561010c565b6"
        + "0408051918252519081900360200190f35b3480156100ae57600080fd5b50d380156100bb57600080fd"
        + "5b50d280156100c857600080fd5b50610090600435602435610111565b3480156100e357600080fd5b5"
        + "0d380156100f057600080fd5b50d280156100fd57600080fd5b50610090600435602435610116565b90"
        + "1b90565b901d90565b901c905600a165627a7a723058200d5cc53ffdc6db62c4d7414d8b7d95c98218e"
        + "50b4c1ea5961d527de1439733450029";
    String abi = "[{\"constant\":false,\"inputs\":[{\"name\":\"num\",\"type\":\"uint256\"},"
        + "{\"name\":\"input\",\"type\":\"uint256\"}],\"name\":\"shlTest\",\"outputs\":"
        + "[{\"name\":\"out\",\"type\":\"bytes32\"}],\"payable\":false,\"stateMutability\":"
        + "\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":"
        + "\"num\",\"type\":\"uint256\"},{\"name\":\"input\",\"type\":\"uint256\"}],\"name"
        + "\":\"sarTest\",\"outputs\":[{\"name\":\"out\",\"type\":\"bytes32\"}],\"payable"
        + "\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant"
        + "\":false,\"inputs\":[{\"name\":\"num\",\"type\":\"uint256\"},{\"name\":\"input\""
        + ",\"type\":\"uint256\"}],\"name\":\"shrTest\",\"outputs\":[{\"name\":\"out\",\""
        + "type\":\"bytes32\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\""
        + "type\":\"function\"}]";

    contractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, 0L, 100, null, contractExcKey,
            contractExcAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account info;

    AccountResourceMessage resourceInfo = PublicMethed
        .getAccountResource(contractExcAddress, blockingStubFull);
    info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);
    String txid = "";
    String num = "1" + "," + "5";

    txid = PublicMethed
        .triggerContract(contractAddress, "sarTest(uint256,uint256)", num, false, 0, maxFeeLimit,
            contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Long fee = infoById.get().getFee();
    Long photonUsed = infoById.get().getReceipt().getPhotonUsage();
    Long entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    Long photonFee = infoById.get().getReceipt().getPhotonFee();
    long entropyUsageTotal = infoById.get().getReceipt().getEntropyUsageTotal();

    logger.info("fee:" + fee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);
    logger.info("entropyUsageTotal:" + entropyUsageTotal);

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    AccountResourceMessage resourceInfoafter = PublicMethed
        .getAccountResource(contractExcAddress, blockingStubFull);
    Long afterBalance = infoafter.getBalance();
    Long afterEntropyUsed = resourceInfoafter.getEntropyUsed();
    Long afterPhotonUsed = resourceInfoafter.getPhotonUsed();
    Long afterFreePhotonUsed = resourceInfoafter.getFreePhotonUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEntropyUsed:" + afterEntropyUsed);
    logger.info("afterPhotonUsed:" + afterPhotonUsed);
    logger.info("afterFreePhotonUsed:" + afterFreePhotonUsed);

    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(beforeEntropyUsed + entropyUsed >= afterEntropyUsed);
    Assert.assertTrue(beforeFreePhotonUsed + photonUsed >= afterFreePhotonUsed);
    Assert.assertTrue(beforePhotonUsed + photonUsed >= afterPhotonUsed);
    txid = PublicMethed
        .triggerContract(contractAddress, "shlTest(uint256,uint256)", num, false, 0, maxFeeLimit,
            contractExcAddress, contractExcKey, blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    txid = PublicMethed
        .triggerContract(contractAddress, "shrTest(uint256,uint256)", num, false, 0, maxFeeLimit,
            contractExcAddress, contractExcKey, blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);

  }

  @Test(enabled = true, description = "New compiler compile shift instruction")
  public void test2Newcompile() {
    Assert.assertTrue(PublicMethed
        .sendcoin(contractExcAddress, 10000000000L, testNetAccountAddress, testNetAccountKey,
            blockingStubFull));
    String filePath = "src/test/resources/soliditycode/ShiftCommand001.sol";
    String contractName = "TestBitwiseShift";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, 0L, 100, null, contractExcKey,
            contractExcAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account info;

    AccountResourceMessage resourceInfo = PublicMethed
        .getAccountResource(contractExcAddress, blockingStubFull);
    info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEntropyUsed = resourceInfo.getEntropyUsed();
    Long beforePhotonUsed = resourceInfo.getPhotonUsed();
    Long beforeFreePhotonUsed = resourceInfo.getFreePhotonUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEntropyUsed:" + beforeEntropyUsed);
    logger.info("beforePhotonUsed:" + beforePhotonUsed);
    logger.info("beforeFreePhotonUsed:" + beforeFreePhotonUsed);
    String txid = "";
    String num = "1" + "," + "5";

    txid = PublicMethed
        .triggerContract(contractAddress, "sarTest(uint256,uint256)", num, false, 0, maxFeeLimit,
            contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Long fee = infoById.get().getFee();
    Long photonUsed = infoById.get().getReceipt().getPhotonUsage();
    Long entropyUsed = infoById.get().getReceipt().getEntropyUsage();
    Long photonFee = infoById.get().getReceipt().getPhotonFee();
    long entropyUsageTotal = infoById.get().getReceipt().getEntropyUsageTotal();

    logger.info("fee:" + fee);
    logger.info("photonUsed:" + photonUsed);
    logger.info("entropyUsed:" + entropyUsed);
    logger.info("photonFee:" + photonFee);
    logger.info("entropyUsageTotal:" + entropyUsageTotal);

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    AccountResourceMessage resourceInfoafter = PublicMethed
        .getAccountResource(contractExcAddress, blockingStubFull);
    Long afterBalance = infoafter.getBalance();
    Long afterEntropyUsed = resourceInfoafter.getEntropyUsed();
    Long afterPhotonUsed = resourceInfoafter.getPhotonUsed();
    Long afterFreePhotonUsed = resourceInfoafter.getFreePhotonUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEntropyUsed:" + afterEntropyUsed);
    logger.info("afterPhotonUsed:" + afterPhotonUsed);
    logger.info("afterFreePhotonUsed:" + afterFreePhotonUsed);

    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(beforeEntropyUsed + entropyUsed >= afterEntropyUsed);
    Assert.assertTrue(beforeFreePhotonUsed + photonUsed >= afterFreePhotonUsed);
    Assert.assertTrue(beforePhotonUsed + photonUsed >= afterPhotonUsed);
    txid = PublicMethed
        .triggerContract(contractAddress, "shlTest(uint256,uint256)", num, false, 0, maxFeeLimit,
            contractExcAddress, contractExcKey, blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    txid = PublicMethed
        .triggerContract(contractAddress, "shrTest(uint256,uint256)", num, false, 0, maxFeeLimit,
            contractExcAddress, contractExcKey, blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);

  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed
        .freedResource(contractAddress, contractExcKey, testNetAccountAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelFull1 != null) {
      channelFull1.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelSolidity != null) {
      channelSolidity.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }


}
