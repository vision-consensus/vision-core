package org.vision.core.net.messagehandler;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.vision.common.utils.Pair;
import org.vision.core.capsule.BlockCapsule;
import org.vision.core.config.Parameter;
import org.vision.core.exception.P2pException;
import org.vision.core.net.message.ChainInventoryMessage;
import org.vision.core.net.peer.PeerConnection;

public class ChainInventoryMsgHandlerTest {

  private ChainInventoryMsgHandler handler = new ChainInventoryMsgHandler();
  private PeerConnection peer = new PeerConnection();
  private ChainInventoryMessage msg = new ChainInventoryMessage(new ArrayList<>(), 0L);
  private List<BlockCapsule.BlockId> blockIds = new ArrayList<>();

  @Test
  public void testProcessMessage() {
    try {
      handler.processMessage(peer, msg);
    } catch (P2pException e) {
      Assert.assertTrue(e.getMessage().equals("not send syncBlockChainMsg"));
    }

    peer.setSyncChainRequested(new Pair<>(new LinkedList<>(), System.currentTimeMillis()));

    try {
      handler.processMessage(peer, msg);
    } catch (P2pException e) {
      Assert.assertTrue(e.getMessage().equals("blockIds is empty"));
    }

    long size = Parameter.NetConstants.SYNC_FETCH_BATCH_NUM + 2;
    for (int i = 0; i < size; i++) {
      blockIds.add(new BlockCapsule.BlockId());
    }
    msg = new ChainInventoryMessage(blockIds, 0L);

    try {
      handler.processMessage(peer, msg);
    } catch (P2pException e) {
      Assert.assertTrue(e.getMessage().equals("big blockIds size: " + size));
    }

    blockIds.clear();
    size = Parameter.NetConstants.SYNC_FETCH_BATCH_NUM / 100;
    for (int i = 0; i < size; i++) {
      blockIds.add(new BlockCapsule.BlockId());
    }
    msg = new ChainInventoryMessage(blockIds, 100L);

    try {
      handler.processMessage(peer, msg);
    } catch (P2pException e) {
      Assert.assertTrue(e.getMessage().equals("remain: 100, blockIds size: " + size));
    }
  }

}
