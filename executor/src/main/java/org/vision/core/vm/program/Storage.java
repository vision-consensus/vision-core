package org.vision.core.vm.program;

import static java.lang.System.arraycopy;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Producer;
import org.vision.common.utils.StringUtil;
import org.vision.core.capsule.StorageRowCapsule;
import org.vision.core.store.StorageRowStore;
import org.vision.common.crypto.Hash;
import org.vision.common.runtime.vm.DataWord;
import org.vision.common.utils.ByteUtil;


@Slf4j(topic = "Storage")
public class Storage {

  private static final int PREFIX_BYTES = 16;
  @Getter
  private final Map<DataWord, StorageRowCapsule> rowCache = new HashMap<>();
  @Getter
  private byte[] addrHash;
  @Getter
  private StorageRowStore store;
  @Getter
  private byte[] address;

  public Storage(byte[] address, StorageRowStore store) {
    addrHash = addrHash(address);
    this.address = address;
    this.store = store;
  }

  public Storage(Storage storage) {
    this.addrHash = storage.addrHash.clone();
    this.address = storage.getAddress().clone();
    this.store = storage.store;
    storage.getRowCache().forEach((DataWord rowKey, StorageRowCapsule row) -> {
      StorageRowCapsule newRow = new StorageRowCapsule(row);
      this.rowCache.put(rowKey.clone(), newRow);
    });
  }

  private static byte[] compose(byte[] key, byte[] addrHash) {
    byte[] result = new byte[key.length];
    arraycopy(addrHash, 0, result, 0, PREFIX_BYTES);
    arraycopy(key, PREFIX_BYTES, result, PREFIX_BYTES, PREFIX_BYTES);
    return result;
  }

  // 32 bytes
  private static byte[] addrHash(byte[] address) {
    return Hash.sha3(address);
  }

  private static byte[] addrHash(byte[] address, byte[] trxHash) {
    if (ByteUtil.isNullOrZeroArray(trxHash)) {
      return Hash.sha3(address);
    }
    return Hash.sha3(ByteUtil.merge(address, trxHash));
  }

  public void generateAddrHash(byte[] vsId) {
    // update addreHash for create2
    addrHash = addrHash(address, vsId);
  }

  public DataWord getValue(DataWord key) {
    if (rowCache.containsKey(key)) {
      return new DataWord(rowCache.get(key).getValue());
    } else {
      StorageRowCapsule row = store.get(compose(key.getData(), addrHash));
      if (row == null || row.getInstance() == null) {
        return null;
      }
      rowCache.put(key, row);
      return new DataWord(row.getValue());
    }
  }

  public void put(DataWord key, DataWord value) {
    if (rowCache.containsKey(key)) {
      rowCache.get(key).setValue(value.getData());
    } else {
      byte[] rowKey = compose(key.getData(), addrHash);
      StorageRowCapsule row = new StorageRowCapsule(rowKey, value.getData());
      rowCache.put(key, row);
    }
  }

  public void commit() {
    rowCache.forEach((DataWord rowKey, StorageRowCapsule row) -> {
      if (row.isDirty()) {
        String rawValue = DataWord.bigIntValue(row.getRowValue());
        if (new DataWord(row.getValue()).isZero()) {
          this.store.delete(row.getRowKey());
        } else {
          this.store.put(row.getRowKey(), row);
        }

        if (CommonParameter.PARAMETER.isKafkaEnable()) {
          try{
            JSONObject json = new JSONObject();
            json.put(rowKey.toHexString(), rawValue);
            json.put("address", StringUtil.encode58Check(address));
            json.put("hexAddress", ByteArray.toHexString(address));
            json.put("source", "storage");
            Producer.getInstance().send("STORAGE", Hex.toHexString(address), json.toJSONString());
            logger.info("commit send STORAGE success, address:{}", StringUtil.encode58Check(address));
          }catch (Exception e){
            logger.error("commit send STORAGE fail", e);
          }
        }

      }
    });
  }
}
