package org.vision.core.services.jsonrpc;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.vision.common.runtime.vm.DataWord;
import org.vision.common.utils.*;
import org.vision.core.exception.JsonRpcInvalidParamsException;
import org.vision.core.services.EthereumCompatibleService;

@Slf4j(topic = "API")
public class JsonRpcApiUtil {

  public static long getByJsonBlockId(String blockNumOrTag) throws JsonRpcInvalidParamsException {
    if (EthereumCompatibleService.PENDING_STR.equalsIgnoreCase(blockNumOrTag)) {
      throw new JsonRpcInvalidParamsException("TAG pending not supported");
    }
    if (StringUtils.isEmpty(blockNumOrTag) || EthereumCompatibleService.LATEST_STR.equalsIgnoreCase(blockNumOrTag)) {
      return -1;
    } else if (EthereumCompatibleService.EARLIEST_STR.equalsIgnoreCase(blockNumOrTag)) {
      return 0;
    } else {
      return ByteArray.jsonHexToLong(blockNumOrTag);
    }
  }

  /**
   * convert 40 hex string of address to byte array, padding 0 ahead if length is odd.
   */
  public static byte[] addressToByteArray(String hexAddress) throws JsonRpcInvalidParamsException {
    byte[] addressByte = ByteArray.fromHexString(hexAddress);
    if (addressByte.length != DecodeUtil.ADDRESS_SIZE / 2 - 1) {
      throw new JsonRpcInvalidParamsException("invalid address: " + hexAddress);
    }
    return new DataWord(addressByte).getLast20Bytes();
  }

  /**
   * check if topic is hex string of size 64, padding 0 ahead if length is odd.
   */
  public static byte[] topicToByteArray(String hexTopic) throws JsonRpcInvalidParamsException {
    byte[] topicByte = ByteArray.fromHexString(hexTopic);
    if (topicByte.length != 32) {
      throw new JsonRpcInvalidParamsException("invalid topic: " + hexTopic);
    }
    return topicByte;
  }
}
