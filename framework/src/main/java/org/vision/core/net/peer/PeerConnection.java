package org.vision.core.net.peer;

import com.alibaba.fastjson.JSONObject;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.vision.core.Constant;
import org.vision.core.net.service.AdvService;
import org.vision.core.net.service.SyncService;
import org.vision.common.overlay.message.HelloMessage;
import org.vision.common.overlay.message.Message;
import org.vision.common.overlay.server.Channel;
import org.vision.common.utils.Pair;
import org.vision.common.utils.Sha256Hash;
import org.vision.core.capsule.BlockCapsule.BlockId;
import org.vision.core.config.Parameter.NetConstants;
import org.vision.core.net.VisionNetDelegate;

@Slf4j(topic = "net")
@Component
@Scope("prototype")
public class PeerConnection extends Channel {

  @Autowired
  private VisionNetDelegate visionNetDelegate;

  @Autowired
  private SyncService syncService;

  @Autowired
  private AdvService advService;

  @Setter
  @Getter
  private HelloMessage helloMessage;

  private int invCacheSize = 100_000;

  @Setter
  @Getter
  private Cache<Item, Long> advInvReceive = CacheBuilder.newBuilder().maximumSize(invCacheSize)
      .expireAfterWrite(1, TimeUnit.HOURS).recordStats().build();

  @Setter
  @Getter
  private Cache<Item, Long> advInvSpread = CacheBuilder.newBuilder().maximumSize(invCacheSize)
      .expireAfterWrite(1, TimeUnit.HOURS).recordStats().build();

  @Setter
  @Getter
  private Map<Item, Long> advInvRequest = new ConcurrentHashMap<>();

  @Setter
  private BlockId fastForwardBlock;

  @Getter
  private BlockId blockBothHave = new BlockId();
  @Getter
  private volatile long blockBothHaveUpdateTime = System.currentTimeMillis();
  @Setter
  @Getter
  private BlockId lastSyncBlockId;
  @Setter
  @Getter
  private volatile long remainNum;
  @Getter
  private Cache<Sha256Hash, Long> syncBlockIdCache = CacheBuilder.newBuilder()
      .maximumSize(2 * NetConstants.SYNC_FETCH_BATCH_NUM).recordStats().build();
  @Setter
  @Getter
  private Deque<BlockId> syncBlockToFetch = new ConcurrentLinkedDeque<>();
  @Setter
  @Getter
  private Map<BlockId, Long> syncBlockRequested = new ConcurrentHashMap<>();
  @Setter
  @Getter
  private Pair<Deque<BlockId>, Long> syncChainRequested = null;
  @Setter
  @Getter
  private Set<BlockId> syncBlockInProcess = new HashSet<>();
  @Setter
  @Getter
  private volatile boolean needSyncFromPeer = true;
  @Setter
  @Getter
  private volatile boolean needSyncFromUs = true;

  public void setBlockBothHave(BlockId blockId) {
    this.blockBothHave = blockId;
    this.blockBothHaveUpdateTime = System.currentTimeMillis();
  }

  public boolean isIdle() {
    return advInvRequest.isEmpty() && syncBlockRequested.isEmpty() && syncChainRequested == null;
  }

  public void sendMessage(Message message) {
    msgQueue.sendMessage(message);
  }

  public void fastSend(Message message) {
    msgQueue.fastSend(message);
  }

  public void onConnect() {
    long headBlockNum = visionNetDelegate.getHeadBlockId().getNum();
    long peerHeadBlockNum = getHelloMessage().getHeadBlockId().getNum();

    if (peerHeadBlockNum > headBlockNum) {
      needSyncFromUs = false;
      setVisionState(VisionState.SYNCING);
      syncService.startSync(this);
    } else {
      needSyncFromPeer = false;
      if (peerHeadBlockNum == headBlockNum) {
        needSyncFromUs = false;
      }
      setVisionState(VisionState.SYNC_COMPLETED);
    }
  }

  public void onDisconnect() {
    syncService.onDisconnect(this);
    advService.onDisconnect(this);
    advInvReceive.cleanUp();
    advInvSpread.cleanUp();
    advInvRequest.clear();
    syncBlockIdCache.cleanUp();
    syncBlockToFetch.clear();
    syncBlockRequested.clear();
    syncBlockInProcess.clear();
    syncBlockInProcess.clear();
  }

  public JSONObject getNodeJson(){
    JSONObject json = new JSONObject();
    json.put("host", getNode().getHost());
    json.put("port", getNode().getPort());
    json.put("connectTime", (System.currentTimeMillis() - getStartTime()) / Constant.ONE_THOUSAND);
    json.put("lastBlockNum", fastForwardBlock != null ? fastForwardBlock.getNum() : blockBothHave.getNum());
    json.put("remainNum",remainNum);
    return json;
    /*
    Peer 18.191.204.246:18888 [146e996f]
    ping msg: count 30699, max-average-min-last: 12 5 1 5
    connect time: 368958s
    last know block num: 2463
    needSyncFromPeer:false
    needSyncFromUs:false
    syncToFetchSize:0
    syncToFetchSizePeekNum:-1
    syncBlockRequestedSize:0
    remainNum:0
    syncChainRequested:0
    blockInProcess:0
    NodeStat[reput: 286(230), discover: 1/1 0/0 62097/62097 62098/62098 24ms, p2p: 1/1/1 , vision: 186632/36979   , tcp flow: 8910
     */
  }

  public String log() {
    long now = System.currentTimeMillis();
    return String.format(
        "Peer %s [%8s]\n"
            + "ping msg: count %d, max-average-min-last: %d %d %d %d\n"
            + "connect time: %ds\n"
            + "last know block num: %s\n"
            + "needSyncFromPeer:%b\n"
            + "needSyncFromUs:%b\n"
            + "syncToFetchSize:%d\n"
            + "syncToFetchSizePeekNum:%d\n"
            + "syncBlockRequestedSize:%d\n"
            + "remainNum:%d\n"
            + "syncChainRequested:%d\n"
            + "blockInProcess:%d\n",
        getNode().getHost() + ":" + getNode().getPort(),
        getNode().getHexIdShort(),

        getNodeStatistics().pingMessageLatency.getCount(),
        getNodeStatistics().pingMessageLatency.getMax(),
        getNodeStatistics().pingMessageLatency.getAvg(),
        getNodeStatistics().pingMessageLatency.getMin(),
        getNodeStatistics().pingMessageLatency.getLast(),

        (now - getStartTime()) / Constant.ONE_THOUSAND,
        fastForwardBlock != null ? fastForwardBlock.getNum() : blockBothHave.getNum(),
        isNeedSyncFromPeer(),
        isNeedSyncFromUs(),
        syncBlockToFetch.size(),
        !syncBlockToFetch.isEmpty() ? syncBlockToFetch.peek().getNum() : -1,
        syncBlockRequested.size(),
        remainNum,
        syncChainRequested == null ? 0 : (now - syncChainRequested.getValue()) 
                / Constant.ONE_THOUSAND,
        syncBlockInProcess.size())
        + nodeStatistics.toString() + "\n";
  }

  public boolean isSyncFinish() {
    return !(needSyncFromPeer || needSyncFromUs);
  }

}
