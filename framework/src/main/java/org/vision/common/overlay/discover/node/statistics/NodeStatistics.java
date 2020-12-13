/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */

package org.vision.common.overlay.discover.node.statistics;

import lombok.Getter;
import org.vision.core.config.args.Args;
import org.vision.protos.Protocol.ReasonCode;

import java.util.concurrent.atomic.AtomicLong;

public class NodeStatistics {

  public static final int REPUTATION_PREDEFINED = 100000;
  public static final long TOO_MANY_PEERS_PENALIZE_TIMEOUT = 60 * 1000L;
  private static final long CLEAR_CYCLE_TIME = 60 * 60 * 1000L;
  public final MessageStatistics messageStatistics = new MessageStatistics();
  public final MessageCount p2pHandShake = new MessageCount();
  public final MessageCount tcpFlow = new MessageCount();
  public final SimpleStatter discoverMessageLatency;
  public final SimpleStatter pingMessageLatency;
  public final AtomicLong lastPongReplyTime = new AtomicLong(0L); // in milliseconds
  private final long MIN_DATA_LENGTH = Args.getInstance().getReceiveTcpMinDataLength();
  private boolean isPredefined = false;
  private int persistedReputation = 0;
  @Getter
  private int disconnectTimes = 0;
  @Getter
  private ReasonCode visionLastRemoteDisconnectReason = null;
  @Getter
  private ReasonCode visionLastLocalDisconnectReason = null;
  private long lastDisconnectedTime = 0;
  private long firstDisconnectedTime = 0;
  private Reputation reputation;

  public NodeStatistics() {
    discoverMessageLatency = new SimpleStatter();
    pingMessageLatency = new SimpleStatter();
    reputation = new Reputation(this);
  }

  public int getReputation() {
    int score = 0;
    if (!isReputationPenalized()) {
      score += persistedReputation / 5 + reputation.getScore();
    }
    if (isPredefined) {
      score += REPUTATION_PREDEFINED;
    }
    return score;
  }

  public ReasonCode getDisconnectReason() {
    if (visionLastLocalDisconnectReason != null) {
      return visionLastLocalDisconnectReason;
    }
    if (visionLastRemoteDisconnectReason != null) {
      return visionLastRemoteDisconnectReason;
    }
    return ReasonCode.UNKNOWN;
  }

  public boolean isReputationPenalized() {

    if (wasDisconnected() && visionLastRemoteDisconnectReason == ReasonCode.TOO_MANY_PEERS
        && System.currentTimeMillis() - lastDisconnectedTime < TOO_MANY_PEERS_PENALIZE_TIMEOUT) {
      return true;
    }

    if (wasDisconnected() && visionLastRemoteDisconnectReason == ReasonCode.DUPLICATE_PEER
        && System.currentTimeMillis() - lastDisconnectedTime < TOO_MANY_PEERS_PENALIZE_TIMEOUT) {
      return true;
    }

    if (firstDisconnectedTime > 0
        && (System.currentTimeMillis() - firstDisconnectedTime) > CLEAR_CYCLE_TIME) {
      visionLastLocalDisconnectReason = null;
      visionLastRemoteDisconnectReason = null;
      disconnectTimes = 0;
      persistedReputation = 0;
      firstDisconnectedTime = 0;
    }

    if (visionLastLocalDisconnectReason == ReasonCode.INCOMPATIBLE_PROTOCOL
        || visionLastRemoteDisconnectReason == ReasonCode.INCOMPATIBLE_PROTOCOL
        || visionLastLocalDisconnectReason == ReasonCode.BAD_PROTOCOL
        || visionLastRemoteDisconnectReason == ReasonCode.BAD_PROTOCOL
        || visionLastLocalDisconnectReason == ReasonCode.BAD_BLOCK
        || visionLastRemoteDisconnectReason == ReasonCode.BAD_BLOCK
        || visionLastLocalDisconnectReason == ReasonCode.BAD_TX
        || visionLastRemoteDisconnectReason == ReasonCode.BAD_TX
        || visionLastLocalDisconnectReason == ReasonCode.FORKED
        || visionLastRemoteDisconnectReason == ReasonCode.FORKED
        || visionLastLocalDisconnectReason == ReasonCode.UNLINKABLE
        || visionLastRemoteDisconnectReason == ReasonCode.UNLINKABLE
        || visionLastLocalDisconnectReason == ReasonCode.INCOMPATIBLE_CHAIN
        || visionLastRemoteDisconnectReason == ReasonCode.INCOMPATIBLE_CHAIN
        || visionLastRemoteDisconnectReason == ReasonCode.SYNC_FAIL
        || visionLastLocalDisconnectReason == ReasonCode.SYNC_FAIL
        || visionLastRemoteDisconnectReason == ReasonCode.INCOMPATIBLE_VERSION
        || visionLastLocalDisconnectReason == ReasonCode.INCOMPATIBLE_VERSION) {
      persistedReputation = 0;
      return true;
    }
    return false;
  }

  public void nodeDisconnectedRemote(ReasonCode reason) {
    lastDisconnectedTime = System.currentTimeMillis();
    visionLastRemoteDisconnectReason = reason;
  }

  public void nodeDisconnectedLocal(ReasonCode reason) {
    lastDisconnectedTime = System.currentTimeMillis();
    visionLastLocalDisconnectReason = reason;
  }

  public void notifyDisconnect() {
    lastDisconnectedTime = System.currentTimeMillis();
    if (firstDisconnectedTime <= 0) {
      firstDisconnectedTime = lastDisconnectedTime;
    }
    if (visionLastLocalDisconnectReason == ReasonCode.RESET) {
      return;
    }
    disconnectTimes++;
    persistedReputation = persistedReputation / 2;
  }

  public boolean wasDisconnected() {
    return lastDisconnectedTime > 0;
  }

  public boolean isPredefined() {
    return isPredefined;
  }

  public void setPredefined(boolean isPredefined) {
    this.isPredefined = isPredefined;
  }

  public void setPersistedReputation(int persistedReputation) {
    this.persistedReputation = persistedReputation;
  }

  @Override
  public String toString() {
    return "NodeStat[reput: " + getReputation() + "(" + persistedReputation + "), discover: "
        + messageStatistics.discoverInPong + "/" + messageStatistics.discoverOutPing + " "
        + messageStatistics.discoverOutPong + "/" + messageStatistics.discoverInPing + " "
        + messageStatistics.discoverInNeighbours + "/" + messageStatistics.discoverOutFindNode
        + " "
        + messageStatistics.discoverOutNeighbours + "/" + messageStatistics.discoverInFindNode
        + " "
        + ((int) discoverMessageLatency.getAvrg()) + "ms"
        + ", p2p: " + p2pHandShake + "/" + messageStatistics.p2pInHello + "/"
        + messageStatistics.p2pOutHello + " "
        + ", vision: " + messageStatistics.visionInMessage + "/" + messageStatistics.visionOutMessage
        + " "
        + (wasDisconnected() ? "X " + disconnectTimes : "")
        + (visionLastLocalDisconnectReason != null ? ("<=" + visionLastLocalDisconnectReason) : " ")
        + (visionLastRemoteDisconnectReason != null ? ("=>" + visionLastRemoteDisconnectReason) : " ")
        + ", tcp flow: " + tcpFlow.getTotalCount();
  }

  public boolean nodeIsHaveDataTransfer() {
    return tcpFlow.getTotalCount() > MIN_DATA_LENGTH;
  }

  public void resetTcpFlow() {
    tcpFlow.reset();
  }

  public class SimpleStatter {

    private long sum;
    @Getter
    private long count;
    @Getter
    private long last;
    @Getter
    private long min;
    @Getter
    private long max;

    public void add(long value) {
      last = value;
      sum += value;
      min = min == 0 ? value : Math.min(min, value);
      max = Math.max(max, value);
      count++;
    }

    public long getAvrg() {
      return count == 0 ? 0 : sum / count;
    }

  }

}
