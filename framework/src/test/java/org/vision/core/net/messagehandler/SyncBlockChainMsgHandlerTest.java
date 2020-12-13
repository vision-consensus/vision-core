package org.vision.core.net.messagehandler;

import java.util.ArrayList;
import org.junit.Assert;
import org.junit.Test;
import org.vision.core.exception.P2pException;
import org.vision.core.net.message.SyncBlockChainMessage;
import org.vision.core.net.peer.PeerConnection;

public class SyncBlockChainMsgHandlerTest {

  private SyncBlockChainMsgHandler handler = new SyncBlockChainMsgHandler();
  private PeerConnection peer = new PeerConnection();

  @Test
  public void testProcessMessage() {
    try {
      handler.processMessage(peer, new SyncBlockChainMessage(new ArrayList<>()));
    } catch (P2pException e) {
      Assert.assertTrue(e.getMessage().equals("SyncBlockChain blockIds is empty"));
    }
  }

}
