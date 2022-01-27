package org.vision.core.store;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.JsonFormat;
import org.vision.common.utils.Producer;
import org.vision.core.capsule.AssetIssueCapsule;
import org.vision.core.db.VisionStoreWithRevoking;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static org.vision.common.utils.Commons.ASSET_ISSUE_COUNT_LIMIT_MAX;

@Slf4j(topic = "DB")
@Component
public class AssetIssueStore extends VisionStoreWithRevoking<AssetIssueCapsule> {

  @Autowired
  protected AssetIssueStore(@Value("asset-issue") String dbName) {
    super(dbName);
  }

  @Autowired
  private BalanceTraceStore balanceTraceStore;

  @Override
  public AssetIssueCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  @Override
  public void put(byte[] key, AssetIssueCapsule item) {
    super.put(key, item);

    if(CommonParameter.PARAMETER.isKafkaEnable()){
      JSONObject itemJsonObject = JSONObject.parseObject(JsonFormat.printToString(item.getInstance()));
      if (CommonParameter.getInstance().isHistoryBalanceLookup() && balanceTraceStore != null) {
        itemJsonObject.putAll(balanceTraceStore.assembleJsonInfo());
      }
      Producer.getInstance().send("ASSETISSUE", Hex.toHexString(item.getOwnerAddress().toByteArray()), itemJsonObject.toJSONString());
    }
  }

  /**
   * get all asset issues.
   */
  public List<AssetIssueCapsule> getAllAssetIssues() {
    return Streams.stream(iterator())
        .map(Entry::getValue)
        .collect(Collectors.toList());
  }

  private List<AssetIssueCapsule> getAssetIssuesPaginated(List<AssetIssueCapsule> assetIssueList,
      long offset, long limit) {
    if (limit < 0 || offset < 0) {
      return null;
    }

//    return Streams.stream(iterator())
//        .map(Entry::getValue)
//        .sorted(Comparator.comparing(a -> a.getName().toStringUtf8(), String::compareTo))
//        .skip(offset)
//        .limit(Math.min(limit, ASSET_ISSUE_COUNT_LIMIT_MAX))
//        .collect(Collectors.toList());

    if (assetIssueList.size() <= offset) {
      return null;
    }
    assetIssueList.sort((o1, o2) -> {
      if (o1.getName() != o2.getName()) {
        return o1.getName().toStringUtf8().compareTo(o2.getName().toStringUtf8());
      }
      return Long.compare(o1.getOrder(), o2.getOrder());
    });
    limit = limit > ASSET_ISSUE_COUNT_LIMIT_MAX ? ASSET_ISSUE_COUNT_LIMIT_MAX : limit;
    long end = offset + limit;
    end = end > assetIssueList.size() ? assetIssueList.size() : end;
    return assetIssueList.subList((int) offset, (int) end);
  }

  public List<AssetIssueCapsule> getAssetIssuesPaginated(long offset, long limit) {
    return getAssetIssuesPaginated(getAllAssetIssues(), offset, limit);
  }
}
