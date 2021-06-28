/*
 * vision-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * vision-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.vision.core.db;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.JsonFormat;
import org.vision.common.utils.Producer;
import org.vision.common.utils.Sha256Hash;
import org.vision.common.utils.Util;
import org.vision.core.capsule.BlockCapsule;
import org.vision.core.capsule.BlockCapsule.BlockId;
import org.vision.core.capsule.TransactionRetCapsule;
import org.vision.core.exception.BadItemException;
import org.vision.protos.Protocol;

@Slf4j(topic = "DB")
@Component
public class BlockStore extends VisionStoreWithRevoking<BlockCapsule> {

  @Autowired
  private BlockStore(@Value("block") String dbName) {
    super(dbName);
  }

  @Override
  public void put(byte[] blockId, BlockCapsule capsule){
    super.put(blockId, capsule);
    if(CommonParameter.PARAMETER.isKafkaEnable()){
      JSONObject obj = JSONObject.parseObject(Util.printBlock(capsule.getInstance(), true));
      obj.put("transactionCount", capsule.getTransactions().size());
      obj.put("blockSize", capsule.getInstance().toByteArray().length);
      obj.put("ifSolidity", false);
      long originEntropyUsage = 0L;
      long entropyUsageTotal = 0L;
      long photonUsage = 0L;
      if (capsule.getTransactions().size() != 0) {
        originEntropyUsage = capsule.getResult().getInstance().getTransactioninfoList().stream().map(item -> JSONObject.parseObject(JsonFormat.printToString(item, true))).filter(tmp -> tmp.containsKey("receipt")).mapToLong(tmp -> tmp.getJSONObject("receipt").getLong("origin_entropy_usage")).sum();
        entropyUsageTotal = capsule.getResult().getInstance().getTransactioninfoList().stream().map(item -> JSONObject.parseObject(JsonFormat.printToString(item, true))).filter(tmp -> tmp.containsKey("receipt")).mapToLong(tmp -> tmp.getJSONObject("receipt").getLong("entropy_usage_total")).sum();
        photonUsage = capsule.getResult().getInstance().getTransactioninfoList().stream().map(item -> JSONObject.parseObject(JsonFormat.printToString(item, true))).filter(tmp -> tmp.containsKey("receipt")).mapToLong(tmp -> tmp.getJSONObject("receipt").getLong("photon_usage")).sum();
      }
      obj.put("originEntropyUsage", originEntropyUsage);
      obj.put("entropyUsageTotal", entropyUsageTotal);
      obj.put("photonUsage", photonUsage);
      //String witness = obj.getJSONObject("block_header").getJSONObject("raw_data").getString("witness_address");
      Producer.getInstance().send("BLOCK", obj.toJSONString());
    }
  }

  public List<BlockCapsule> getLimitNumber(long startNumber, long limit) {
    BlockId startBlockId = new BlockId(Sha256Hash.ZERO_HASH, startNumber);
    return revokingDB.getValuesNext(startBlockId.getBytes(), limit).stream()
        .map(bytes -> {
          try {
            return new BlockCapsule(bytes);
          } catch (BadItemException ignored) {
          }
          return null;
        })
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(BlockCapsule::getNum))
        .collect(Collectors.toList());
  }

  public List<BlockCapsule> getBlockByLatestNum(long getNum) {

    return revokingDB.getlatestValues(getNum).stream()
        .map(bytes -> {
          try {
            return new BlockCapsule(bytes);
          } catch (BadItemException ignored) {
          }
          return null;
        })
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(BlockCapsule::getNum))
        .collect(Collectors.toList());
  }
}
