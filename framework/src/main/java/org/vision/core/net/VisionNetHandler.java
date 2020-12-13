package org.vision.core.net;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.vision.core.net.message.VisionMessage;
import org.vision.core.net.peer.PeerConnection;
import org.vision.common.overlay.server.Channel;
import org.vision.common.overlay.server.MessageQueue;

@Component
@Scope("prototype")
public class VisionNetHandler extends SimpleChannelInboundHandler<VisionMessage> {

  protected PeerConnection peer;

  private MessageQueue msgQueue;

  @Autowired
  private VisionNetService visionNetService;

  @Override
  public void channelRead0(final ChannelHandlerContext ctx, VisionMessage msg) throws Exception {
    msgQueue.receivedMessage(msg);
    visionNetService.onMessage(peer, msg);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    peer.processException(cause);
  }

  public void setMsgQueue(MessageQueue msgQueue) {
    this.msgQueue = msgQueue;
  }

  public void setChannel(Channel channel) {
    this.peer = (PeerConnection) channel;
  }

}