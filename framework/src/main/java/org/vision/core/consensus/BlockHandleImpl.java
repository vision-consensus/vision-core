package org.vision.core.consensus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.common.backup.BackupManager;
import org.vision.consensus.Consensus;
import org.vision.consensus.base.BlockHandle;
import org.vision.consensus.base.Param.Miner;
import org.vision.consensus.base.State;
import org.vision.core.capsule.BlockCapsule;
import org.vision.core.db.Manager;
import org.vision.core.net.VisionNetService;
import org.vision.core.net.message.BlockMessage;

@Slf4j(topic = "consensus")
@Component
public class BlockHandleImpl implements BlockHandle {

  @Autowired
  private Manager manager;

  @Autowired
  private BackupManager backupManager;

  @Autowired
  private VisionNetService visionNetService;

  @Autowired
  private Consensus consensus;

  @Override
  public State getState() {
    if (!backupManager.getStatus().equals(BackupManager.BackupStatusEnum.MASTER)) {
      return State.BACKUP_IS_NOT_MASTER;
    }
    return State.OK;
  }

  public Object getLock() {
    return manager;
  }

  public BlockCapsule produce(Miner miner, long blockTime, long timeout) {
    BlockCapsule blockCapsule = manager.generateBlock(miner, blockTime, timeout);
    if (blockCapsule == null) {
      return null;
    }
    try {
      consensus.receiveBlock(blockCapsule);
      BlockMessage blockMessage = new BlockMessage(blockCapsule);
      visionNetService.fastForward(blockMessage);
      manager.pushBlock(blockCapsule);
      visionNetService.broadcast(blockMessage);
    } catch (Exception e) {
      logger.error("Handle block {} failed.", blockCapsule.getBlockId().getString(), e);
      return null;
    }
    return blockCapsule;
  }
}
