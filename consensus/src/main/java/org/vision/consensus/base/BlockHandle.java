package org.vision.consensus.base;

import org.vision.core.capsule.BlockCapsule;

public interface BlockHandle {

  State getState();

  Object getLock();

  BlockCapsule produce(Param.Miner miner, long blockTime, long timeout);

}