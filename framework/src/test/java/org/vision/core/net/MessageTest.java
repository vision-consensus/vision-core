package org.vision.core.net;

import org.junit.Assert;
import org.junit.Test;
import org.vision.core.exception.P2pException;
import org.vision.core.net.message.MessageTypes;
import org.vision.common.overlay.message.DisconnectMessage;
import org.vision.protos.Protocol.ReasonCode;

public class MessageTest {

  private DisconnectMessage disconnectMessage;

  @Test
  public void test1() throws Exception {
    byte[] bytes = new DisconnectMessage(ReasonCode.TOO_MANY_PEERS).getData();
    DisconnectMessageTest disconnectMessageTest = new DisconnectMessageTest();
    try {
      disconnectMessage = new DisconnectMessage(MessageTypes.P2P_DISCONNECT.asByte(),
          disconnectMessageTest.toByteArray());
    } catch (Exception e) {
      System.out.println(e.getMessage());
      Assert.assertTrue(e instanceof P2pException);
    }
  }

  public void test2() throws Exception {
    DisconnectMessageTest disconnectMessageTest = new DisconnectMessageTest();
    long startTime = System.currentTimeMillis();
    for (int i = 0; i < 100000; i++) {
      disconnectMessage = new DisconnectMessage(MessageTypes.P2P_DISCONNECT.asByte(),
          disconnectMessageTest.toByteArray());
    }
    long endTime = System.currentTimeMillis();
    System.out.println("spend time : " + (endTime - startTime));
  }

}
