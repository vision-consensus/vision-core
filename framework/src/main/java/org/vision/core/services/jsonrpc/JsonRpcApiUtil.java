package org.vision.core.services.jsonrpc;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.vision.common.runtime.vm.DataWord;
import org.vision.common.utils.*;
import org.vision.core.exception.JsonRpcInvalidParamsException;
import org.vision.core.services.EthereumCompatibleService;
import org.vision.protos.contract.SmartContractOuterClass.TriggerSmartContract;

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

  /**
   * convert 40 or 42 hex string of address to byte array, compatible with "41"(T) ahead,
   * padding 0 ahead if length is odd.
   */
  public static byte[] addressCompatibleToByteArray(String hexAddress)
          throws JsonRpcInvalidParamsException {
    byte[] addressByte;
    try {
      addressByte = ByteArray.fromHexString(hexAddress);
      if (addressByte.length != DecodeUtil.ADDRESS_SIZE / 2
              && addressByte.length != DecodeUtil.ADDRESS_SIZE / 2 - 1) {
        throw new JsonRpcInvalidParamsException("invalid address hash value");
      }

      if (addressByte.length == DecodeUtil.ADDRESS_SIZE / 2 - 1) {
        addressByte = ByteUtil.merge(new byte[] {DecodeUtil.addressPreFixByte}, addressByte);
      } else if (addressByte[0] != ByteArray.fromHexString(DecodeUtil.addressPreFixString)[0]) {
        // addressByte.length == DecodeUtil.ADDRESS_SIZE / 2
        throw new JsonRpcInvalidParamsException("invalid address hash value");
      }
    } catch (Exception e) {
      throw new JsonRpcInvalidParamsException(e.getMessage());
    }
    return addressByte;
  }

  public static boolean paramStringIsNull(String string) {
    return StringUtils.isEmpty(string) || string.equals("0x");
  }

  public static boolean paramQuantityIsNull(String quantity) {
    return StringUtils.isEmpty(quantity) || quantity.equals("0x0");
  }

  public static long parseQuantityValue(String value) throws JsonRpcInvalidParamsException {
    long callValue = 0L;

    if (StringUtils.isNotEmpty(value)) {
      try {
        callValue = ByteArray.jsonHexToLong(value);
      } catch (Exception e) {
        throw new JsonRpcInvalidParamsException("invalid param value: invalid hex number");
      }
    }

    return callValue;
  }

  public static TriggerSmartContract triggerCallContract(byte[] address, byte[] contractAddress,
                                                                                 long callValue, byte[] data, long tokenValue, String tokenId) {
    TriggerSmartContract.Builder builder = TriggerSmartContract.newBuilder();

    builder.setOwnerAddress(ByteString.copyFrom(address));
    builder.setContractAddress(ByteString.copyFrom(contractAddress));
    builder.setData(ByteString.copyFrom(data));
    builder.setCallValue(callValue);

    if (StringUtils.isNotEmpty(tokenId)) {
      builder.setCallTokenValue(tokenValue);
      builder.setTokenId(Long.parseLong(tokenId));
    }

    return builder.build();
  }

}
