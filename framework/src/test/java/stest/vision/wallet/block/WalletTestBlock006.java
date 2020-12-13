package stest.vision.wallet.block;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.vision.api.GrpcAPI;
import org.vision.api.GrpcAPI.NumberMessage;
import org.vision.api.WalletGrpc;
import org.vision.api.WalletSolidityGrpc;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Block;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter;

@Slf4j
public class WalletTestBlock006 {

  private ManagedChannel channelFull = null;
  private ManagedChannel channelSolidity = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(1);
  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(Parameter.CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass
  public void beforeClass() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
        .usePlaintext(true)
        .build();
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);
  }

  @Test(enabled = true)
  public void testGetTransactionCountByBlockNumFromFullnode() {
    NumberMessage.Builder builder = NumberMessage.newBuilder();
    builder.setNum(0);
    Long transactionNumInBlock = 0L;
    transactionNumInBlock = blockingStubFull.getTransactionCountByBlockNum(builder
        .build()).getNum();
    Assert.assertTrue(transactionNumInBlock >= 1);

    builder.setNum(-10);
    transactionNumInBlock = blockingStubFull.getTransactionCountByBlockNum(builder
        .build()).getNum();
    Assert.assertTrue(transactionNumInBlock == -1);

    Block currentBlock = blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build());
    Long currentBlockNum = currentBlock.getBlockHeader().getRawData().getNumber();
    builder.setNum(currentBlockNum + 10000L);
    transactionNumInBlock = blockingStubFull.getTransactionCountByBlockNum(builder
        .build()).getNum();
    Assert.assertTrue(transactionNumInBlock == -1);
  }

  @Test(enabled = true)
  public void testGetTransactionCountByBlockNumFromSolidity() {
    NumberMessage.Builder builder = NumberMessage.newBuilder();
    builder.setNum(0);
    Long transactionNumInBlock = 0L;
    transactionNumInBlock = blockingStubSolidity.getTransactionCountByBlockNum(builder
        .build()).getNum();
    Assert.assertTrue(transactionNumInBlock >= 1);

    builder.setNum(-10);
    transactionNumInBlock = blockingStubSolidity.getTransactionCountByBlockNum(builder
        .build()).getNum();
    Assert.assertTrue(transactionNumInBlock == -1);

    Block currentBlock = blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build());
    Long currentBlockNum = currentBlock.getBlockHeader().getRawData().getNumber();
    builder.setNum(currentBlockNum + 10000L);
    transactionNumInBlock = blockingStubSolidity.getTransactionCountByBlockNum(builder
        .build()).getNum();
    Assert.assertTrue(transactionNumInBlock == -1);
  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelSolidity != null) {
      channelSolidity.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


