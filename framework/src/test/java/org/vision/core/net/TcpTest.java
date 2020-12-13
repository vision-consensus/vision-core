package org.vision.core.net;

import static org.vision.protos.Protocol.ReasonCode.DUPLICATE_PEER;
import static org.vision.protos.Protocol.ReasonCode.FORKED;
import static org.vision.protos.Protocol.ReasonCode.INCOMPATIBLE_CHAIN;
import static org.vision.protos.Protocol.ReasonCode.INCOMPATIBLE_VERSION;

import com.google.common.cache.CacheBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Assert;
import org.vision.common.overlay.discover.node.Node;
import org.vision.common.overlay.message.Message;
import org.vision.common.overlay.server.SyncPool;
import org.vision.common.utils.ReflectUtils;
import org.vision.core.ChainBaseManager;
import org.vision.core.capsule.BlockCapsule;
import org.vision.core.config.args.Args;
import org.vision.core.db.Manager;
import org.vision.core.net.message.BlockMessage;
import org.vision.core.net.message.MessageTypes;
import org.vision.core.net.peer.PeerConnection;
import org.vision.common.application.VisionApplicationContext;
import org.vision.common.overlay.message.DisconnectMessage;
import org.vision.common.overlay.message.HelloMessage;
import org.vision.common.overlay.message.P2pMessage;
import org.vision.common.overlay.message.P2pMessageFactory;
import org.vision.common.overlay.server.ChannelManager;
import org.vision.protos.Protocol.Block;

@Slf4j
public class TcpTest {

  Node node = Node.instanceOf("127.0.0.1:" + Args.getInstance().getNodeListenPort());
  private ChannelManager channelManager;
  private Manager manager;
  private ChainBaseManager chainBaseManager;
  private SyncPool pool;
  private VisionNetDelegate visionNetDelegate;
  private int tryTimes = 10;
  private int sleepTime = 1000;
  private boolean finish = false;

  public TcpTest(VisionApplicationContext context) {
    channelManager = context.getBean(ChannelManager.class);
    manager = context.getBean(Manager.class);
    chainBaseManager = context.getBean(ChainBaseManager.class);
    pool = context.getBean(SyncPool.class);
    visionNetDelegate = context.getBean(VisionNetDelegate.class);
  }

  public void normalTest() throws InterruptedException {
    Channel channel = BaseNet.connect(new HandshakeHandler(TestType.normal));
    HelloMessage message = new HelloMessage(node, System.currentTimeMillis(),
        chainBaseManager.getGenesisBlockId(), chainBaseManager.getSolidBlockId(),
        chainBaseManager.getHeadBlockId());
    sendMessage(channel, message);
    validResultCloseConnect(channel);
  }

  public void errorGenesisBlockIdTest() throws InterruptedException {
    Channel channel = BaseNet.connect(new HandshakeHandler(TestType.errorGenesisBlock));
    BlockCapsule.BlockId genesisBlockId = new BlockCapsule.BlockId();
    HelloMessage message = new HelloMessage(node, System.currentTimeMillis(), genesisBlockId,
        chainBaseManager.getSolidBlockId(), chainBaseManager.getHeadBlockId());
    sendMessage(channel, message);
    validResultCloseConnect(channel);
  }

  public void errorVersionTest() throws InterruptedException {
    Channel channel = BaseNet.connect(new HandshakeHandler(TestType.errorVersion));
    Args.getInstance().setNodeP2pVersion(1);
    HelloMessage message = new HelloMessage(node, System.currentTimeMillis(),
        chainBaseManager.getGenesisBlockId(), chainBaseManager.getSolidBlockId(),
        chainBaseManager.getHeadBlockId());
    Args.getInstance().setNodeP2pVersion(2);
    sendMessage(channel, message);
    validResultCloseConnect(channel);
  }

  public void errorSolidBlockIdTest() throws InterruptedException {
    Channel channel = BaseNet.connect(new HandshakeHandler(TestType.errorSolid));
    HelloMessage message = new HelloMessage(node, System.currentTimeMillis(),
        chainBaseManager.getGenesisBlockId(), new BlockCapsule.BlockId(), chainBaseManager.getHeadBlockId());
    sendMessage(channel, message);
    validResultCloseConnect(channel);
  }

  public void repeatConnectTest() throws InterruptedException {
    Channel channel = BaseNet.connect(new HandshakeHandler(TestType.normal));
    HelloMessage message = new HelloMessage(node, System.currentTimeMillis(),
        chainBaseManager.getGenesisBlockId(), chainBaseManager.getSolidBlockId(),
        chainBaseManager.getHeadBlockId());
    sendMessage(channel, message);
    validResultUnCloseConnect();
    Channel repeatChannel = BaseNet.connect(new HandshakeHandler(TestType.repeatConnect));
    sendMessage(repeatChannel, message);
    validResultCloseConnect(repeatChannel);
    clearConnect(channel);
  }

  public void unHandshakeTest() throws InterruptedException {
    List<PeerConnection> beforeActivePeers =
        ReflectUtils.getFieldValue(pool, "activePeers");
    int beforeSize = beforeActivePeers.size();
    Channel channel = BaseNet.connect(new HandshakeHandler(TestType.normal));
    BlockMessage message = new BlockMessage(new BlockCapsule(Block.getDefaultInstance()));
    sendMessage(channel, message);
    List<PeerConnection> afterActivePeers =
        ReflectUtils.getFieldValue(pool, "activePeers");
    int afterSize = afterActivePeers.size();
    Assert.assertEquals(beforeSize, afterSize);
    clearConnect(channel);
  }

  public void errorMsgTest() throws InterruptedException {
    Channel channel = BaseNet.connect(new HandshakeHandler(TestType.normal));
    HelloMessage message = new HelloMessage(node, System.currentTimeMillis(),
        chainBaseManager.getGenesisBlockId(), chainBaseManager.getSolidBlockId(),
        chainBaseManager.getHeadBlockId());
    sendMessage(channel, message);
    validResultUnCloseConnect();
    List<PeerConnection> beforeActivePeers =
        ReflectUtils.getFieldValue(pool, "activePeers");
    int beforeSize = beforeActivePeers.size();
    logger.info("beforeSize : {}", beforeSize);
    channel.writeAndFlush(
        Unpooled.wrappedBuffer(ArrayUtils.add("nihao".getBytes(), 0, (byte) 1)))
        .addListener((ChannelFutureListener) future -> {
          if (future.isSuccess()) {
            logger.info("send msg success");
          } else {
            logger.error("send msg fail", future.cause());
          }
        });
    Thread.sleep(2000);
    List<PeerConnection> afterActivePeers =
        ReflectUtils.getFieldValue(pool, "activePeers");
    int afterSize = afterActivePeers.size();
    logger.info("afterSize : {}", afterSize);
    Assert.assertEquals(beforeSize, afterSize + 1);
    clearConnect(channel);
  }

  private void sendMessage(Channel channel, Message message) {
    channel.writeAndFlush(message.getSendData())
        .addListener((ChannelFutureListener) future -> {
          if (future.isSuccess()) {
            logger.info("send msg success");
          } else {
            logger.error("send msg fail", future.cause());
          }
        });
  }

  private void validResultCloseConnect(Channel channel) throws InterruptedException {
    int trys = 0;
    while (!finish && ++trys < tryTimes) {
      Thread.sleep(sleepTime);
    }
    Assert.assertEquals(finish, true);
    finish = false;
    channel.close();
    Thread.sleep(sleepTime);
    Collection<PeerConnection> peerConnections = ReflectUtils
        .invokeMethod(visionNetDelegate, "getActivePeer");
    for (PeerConnection peer : peerConnections) {
      peer.close();
    }
    ReflectUtils.setFieldValue(channelManager, "recentlyDisconnected",
        CacheBuilder.newBuilder().maximumSize(1000)
            .expireAfterWrite(30, TimeUnit.SECONDS).recordStats().build());
  }

  private void validResultUnCloseConnect() throws InterruptedException {
    int n = 0;
    while (!finish && ++n < tryTimes) {
      Thread.sleep(sleepTime);
    }
    Assert.assertEquals(finish, true);
    finish = false;
  }

  private void clearConnect(Channel channel) throws InterruptedException {
    channel.close();
    Thread.sleep(sleepTime);
    Collection<PeerConnection> peerConnections = ReflectUtils
        .invokeMethod(visionNetDelegate, "getActivePeer");
    for (PeerConnection peer : peerConnections) {
      peer.close();
    }
    ReflectUtils.setFieldValue(channelManager, "recentlyDisconnected",
        CacheBuilder.newBuilder().maximumSize(1000)
            .expireAfterWrite(30, TimeUnit.SECONDS).recordStats().build());
  }

  public void test() throws InterruptedException {
    logger.info("begin normal test ");
    normalTest();
    logger.info("begin errorGenesisBlockId test ");
    errorGenesisBlockIdTest();
    logger.info("begin errorVersion test ");
    errorVersionTest();
    logger.info("begin errorSolidBlockId test ");
    errorSolidBlockIdTest();
    logger.info("begin repeatConnect test");
    repeatConnectTest();
    logger.info("begin unHandshake test");
    unHandshakeTest();
    logger.info("begin errorMsg test");
    errorMsgTest();
  }

  private enum TestType {
    normal, errorGenesisBlock, errorVersion, errorSolid, repeatConnect
  }

  private class HandshakeHandler extends ByteToMessageDecoder {

    private P2pMessageFactory messageFactory = new P2pMessageFactory();

    private TestType testType;

    public HandshakeHandler(TestType testType) {
      this.testType = testType;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out)
        throws Exception {
      byte[] encoded = new byte[buffer.readableBytes()];
      buffer.readBytes(encoded);
      P2pMessage msg = messageFactory.create(encoded);
      switch (testType) {
        case normal:
          Assert.assertEquals(msg.getType(), MessageTypes.P2P_HELLO);
          break;
        case errorGenesisBlock:
          Assert.assertEquals(msg.getType(), MessageTypes.P2P_DISCONNECT);
          Assert.assertEquals(((DisconnectMessage) msg).getReasonCode(), INCOMPATIBLE_CHAIN);
          break;
        case errorVersion:
          Assert.assertEquals(msg.getType(), MessageTypes.P2P_DISCONNECT);
          Assert.assertEquals(((DisconnectMessage) msg).getReasonCode(), INCOMPATIBLE_VERSION);
          break;
        case errorSolid:
          Assert.assertEquals(msg.getType(), MessageTypes.P2P_DISCONNECT);
          Assert.assertEquals(((DisconnectMessage) msg).getReasonCode(), FORKED);
          break;
        case repeatConnect:
          Assert.assertEquals(msg.getType(), MessageTypes.P2P_DISCONNECT);
          Assert.assertEquals(((DisconnectMessage) msg).getReasonCode(), DUPLICATE_PEER);
          break;
        default:
          break;
      }

      finish = true;
    }
  }
}
