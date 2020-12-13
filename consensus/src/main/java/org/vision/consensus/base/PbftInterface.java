package org.vision.consensus.base;

import org.vision.consensus.pbft.message.PbftBaseMessage;
import org.vision.core.capsule.BlockCapsule;

public interface PbftInterface {

  boolean isSyncing();

  void forwardMessage(PbftBaseMessage message);

  BlockCapsule getBlock(long blockNum) throws Exception;

}