package org.vision.core.net.peer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.core.config.Parameter.NetConstants;
import org.vision.core.net.VisionNetDelegate;
import org.vision.protos.Protocol.ReasonCode;

@Slf4j(topic = "net")
@Component
public class PeerStatusCheck {

  @Autowired
  private VisionNetDelegate visionNetDelegate;

  private ScheduledExecutorService peerStatusCheckExecutor = Executors
      .newSingleThreadScheduledExecutor();

  private int blockUpdateTimeout = 30_000;

  public void init() {
    peerStatusCheckExecutor.scheduleWithFixedDelay(() -> {
      try {
        statusCheck();
      } catch (Exception e) {
        logger.error("Unhandled exception", e);
      }
    }, 5, 2, TimeUnit.SECONDS);
  }

  public void close() {
    peerStatusCheckExecutor.shutdown();
  }

  public void statusCheck() {

    long now = System.currentTimeMillis();

    visionNetDelegate.getActivePeer().forEach(peer -> {

      boolean isDisconnected = false;

      if (peer.isNeedSyncFromPeer()
          && peer.getBlockBothHaveUpdateTime() < now - blockUpdateTimeout) {
        logger.warn("Peer {} not sync for a long time.", peer.getInetAddress());
        isDisconnected = true;
      }

      if (!isDisconnected) {
        isDisconnected = peer.getAdvInvRequest().values().stream()
            .anyMatch(time -> time < now - NetConstants.ADV_TIME_OUT);
      }

      if (!isDisconnected) {
        isDisconnected = peer.getSyncBlockRequested().values().stream()
            .anyMatch(time -> time < now - NetConstants.SYNC_TIME_OUT);
      }

      if (isDisconnected) {
        peer.disconnect(ReasonCode.TIME_OUT);
      }
    });
  }

}
