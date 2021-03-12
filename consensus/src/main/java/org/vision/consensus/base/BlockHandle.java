package org.vision.consensus.base;

import org.vision.consensus.base.Param.Miner;
import org.vision.core.capsule.BlockCapsule;

public interface BlockHandle {

  State getState();

  Object getLock();

  BlockCapsule produce(Miner miner, long blockTime, long timeout);

}