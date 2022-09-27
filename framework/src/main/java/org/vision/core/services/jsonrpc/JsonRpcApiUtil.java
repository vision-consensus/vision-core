package org.vision.core.services.jsonrpc;

import com.google.common.base.Throwables;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.vision.api.GrpcAPI;
import org.vision.common.runtime.vm.DataWord;
import org.vision.common.utils.*;
import org.vision.core.ChainBaseManager;
import org.vision.core.Wallet;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.exception.JsonRpcInvalidParamsException;
import org.vision.core.services.EthereumCompatibleService;
import org.vision.protos.Protocol;
import org.vision.protos.Protocol.Transaction;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.TransactionInfo;
import org.vision.protos.contract.*;
import org.vision.protos.contract.AssetIssueContractOuterClass.UnfreezeAssetContract;
import org.vision.protos.contract.AssetIssueContractOuterClass.TransferAssetContract;
import org.vision.protos.contract.BalanceContract.*;
import org.vision.protos.contract.ExchangeContract.ExchangeWithdrawContract;
import org.vision.protos.contract.ExchangeContract.ExchangeInjectContract;
import org.vision.protos.contract.ShieldContract.ShieldedTransferContract;
import org.vision.protos.contract.SmartContractOuterClass.TriggerSmartContract;
import org.vision.protos.contract.WitnessContract.VoteWitnessContract;

import org.vision.core.config.Parameter.NativeTransactionContractAbi;

import java.util.List;
import java.util.Map;

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

  public static long getTransactionAmount(Transaction.Contract contract, String hash,
                                          Wallet wallet, ChainBaseManager manager) {
    long amount = 0;
    try {
      switch (contract.getType()) {
        case UnfreezeBalanceContract:
        case WithdrawBalanceContract:
        case WitnessCreateContract:
        case AssetIssueContract:
        case ExchangeCreateContract:
        case AccountPermissionUpdateContract:
          TransactionInfo transactionInfo = wallet
                  .getTransactionInfoById(ByteString.copyFrom(ByteArray.fromHexString(hash)));
          amount = getAmountFromTransactionInfo(hash, contract.getType(), transactionInfo);
          break;
        default:
          amount = getTransactionAmount(contract, hash, 0, null, wallet, manager);
          break;
      }
    } catch (Exception e) {
      logger.error("Exception happens when get amount. Exception = [{}]",
              Throwables.getStackTraceAsString(e));
    }

    return amount;
  }

  public static long getTransactionAmount(Transaction.Contract contract, String hash,
                                          long blockNum, TransactionInfo transactionInfo,
                                          Wallet wallet, ChainBaseManager manager) {
    long amount = 0;
    try {
      Any contractParameter = contract.getParameter();
      switch (contract.getType()) {
        case TransferContract:
          amount = contractParameter.unpack(TransferContract.class).getAmount();
          break;
        case TransferAssetContract:
          amount = contractParameter.unpack(TransferAssetContract.class).getAmount();
          break;
        case VoteWitnessContract:
          List<VoteWitnessContract.Vote> votesList = contractParameter.unpack(VoteWitnessContract.class).getVotesList();
          long voteNumber = 0L;
          for (VoteWitnessContract.Vote vote : votesList) {
            voteNumber += vote.getVoteCount();
          }
          amount = voteNumber;
          break;
        case ParticipateAssetIssueContract:
          break;
        case FreezeBalanceContract:
          amount = contractParameter.unpack(FreezeBalanceContract.class).getFrozenBalance();
          break;
        case TriggerSmartContract:
          amount = contractParameter.unpack(TriggerSmartContract.class).getCallValue();
          break;
        case ExchangeInjectContract:
          amount = contractParameter.unpack(ExchangeInjectContract.class).getQuant();
          break;
        case ExchangeWithdrawContract:
          amount = contractParameter.unpack(ExchangeWithdrawContract.class).getQuant();
          break;
        case ExchangeTransactionContract:
          amount = contractParameter.unpack(ExchangeContract.ExchangeTransactionContract.class).getQuant();
          break;
        case ShieldedTransferContract:
          ShieldedTransferContract shieldedTransferContract = contract.getParameter()
                  .unpack(ShieldedTransferContract.class);
          if (shieldedTransferContract.getFromAmount() > 0L) {
            amount = shieldedTransferContract.getFromAmount();
          } else if (shieldedTransferContract.getToAmount() > 0L) {
            amount = shieldedTransferContract.getToAmount();
          }
          break;
        case UnfreezeBalanceContract:
        case WithdrawBalanceContract:
          amount = getAmountFromTransactionInfo(hash, contract.getType(), transactionInfo);
          break;
        case UnfreezeAssetContract:
          amount = getUnfreezeAssetAmount(contractParameter.unpack(UnfreezeAssetContract.class)
                  .getOwnerAddress().toByteArray(), wallet);
          break;
        default:
      }
    } catch (Exception e) {

    }
    return amount;
  }

  public static long getUnfreezeAssetAmount(byte[] addressBytes, Wallet wallet) {
    long amount = 0L;
    try {
      if (addressBytes == null) {
        return amount;
      }

      GrpcAPI.AssetIssueList assetIssueList = wallet
              .getAssetIssueByAccount(ByteString.copyFrom(addressBytes));
      if (assetIssueList != null) {
        if (assetIssueList.getAssetIssueCount() != 1) {
          return amount;
        } else {
          AssetIssueContractOuterClass.AssetIssueContract assetIssue = assetIssueList.getAssetIssue(0);
          for (AssetIssueContractOuterClass.AssetIssueContract.FrozenSupply frozenSupply : assetIssue.getFrozenSupplyList()) {
            amount += frozenSupply.getFrozenAmount();
          }
        }
      }
    } catch (Exception e) {

    }
    return amount;
  }

  public static long getAmountFromTransactionInfo(String hash, ContractType contractType,
                                                  TransactionInfo transactionInfo) {
    long amount = 0L;
    try {

      if (transactionInfo != null) {

        switch (contractType) {
          case UnfreezeBalanceContract:
            amount = transactionInfo.getUnfreezeAmount();
            break;
          case WithdrawBalanceContract:
            amount = transactionInfo.getWithdrawAmount();
            break;
          case ExchangeInjectContract:
            amount = transactionInfo.getExchangeInjectAnotherAmount();
            break;
          case ExchangeWithdrawContract:
            amount = transactionInfo.getExchangeWithdrawAnotherAmount();
            break;
          case ExchangeTransactionContract:
            amount = transactionInfo.getExchangeReceivedAmount();
            break;
          case WitnessCreateContract:
          case AssetIssueContract:
          case ExchangeCreateContract:
          case AccountPermissionUpdateContract:
            amount = transactionInfo.getFee();
            break;
          default:
            break;
        }
      } else {
        logger.error("Can't find transaction {} ", hash);
      }
    } catch (Exception e) {

    } catch (Throwable t) {
    }
    return amount;
  }

  public static String getTxID(Transaction transaction) {
    return ByteArray.toHexString(Sha256Hash.hash(true, transaction.getRawData().toByteArray()));
  }


  public static Transaction parseEvmTransactionData(TransactionCapsule.EthTrx ethTrx, Wallet wallet)
          throws ContractValidateException, JsonRpcInvalidParamsException {
    String data = ethTrx.parseData();
    if (StringUtils.isEmpty(data)){
      return null;
    }

    TransactionCapsule trxCap = null;
    String functionSelector = data.substring(0, NativeTransactionContractAbi.TRANSACTION_FUNCTION_SELECTOR_LENGTH);
    String dataValue = data.substring(NativeTransactionContractAbi.TRANSACTION_FUNCTION_SELECTOR_LENGTH);

    int valueSize = NativeTransactionContractAbi.VALUE_SIZE;
    switch (functionSelector){
      case NativeTransactionContractAbi.VoteWitness_FunctionSelector: // voteWitness(address[],uint256[])
        // first array index start should add two parameter size
        int voteAddressArrayIndex = valueSize * 2;
        int voteAddressArraySize = ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(voteAddressArrayIndex, voteAddressArrayIndex + valueSize)));

        // second array index start should add two parameter size, first array length, first array parameters size
        int voteCountArrayIndex = valueSize * 2 + valueSize + valueSize * voteAddressArraySize;
        int voteCountArraySize = ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(voteCountArrayIndex, voteCountArrayIndex + valueSize)));
        if (voteAddressArraySize != voteCountArraySize){
          throw new JsonRpcInvalidParamsException("The size of voteAddress does not match the number of voteCount ");
        }

        VoteWitnessContract.Builder buildVoter = ethTrx.rlpParseToVoteWitnessContract().toBuilder();
        trxCap = wallet.createTransactionCapsule(buildVoter.build(), ContractType.VoteWitnessContract);
        break;
      case NativeTransactionContractAbi.FreezeBalance_FunctionSelector:
      case NativeTransactionContractAbi.FreezeBalanceStage_FunctionSelector:
        FreezeBalanceContract.Builder builderFreeze = ethTrx.rlpParseToFreezeBalanceContract().toBuilder();
        trxCap = wallet.createTransactionCapsule(builderFreeze.build(), ContractType.FreezeBalanceContract);
        break;
      case NativeTransactionContractAbi.UnfreezeBalance_FunctionSelector:
      case NativeTransactionContractAbi.UnfreezeBalanceStage_FunctionSelector:
        UnfreezeBalanceContract.Builder build1 = ethTrx.rlpParseToUnfreezeBalanceContract().toBuilder();
        trxCap = wallet.createTransactionCapsule(build1.build(), ContractType.UnfreezeBalanceContract);
        break;
      case NativeTransactionContractAbi.WithdrawBalance_FunctionSelector:
        WithdrawBalanceContract.Builder buildWithdraw = ethTrx.rlpParseToWithdrawBalanceContract().toBuilder();
        trxCap = wallet.createTransactionCapsule(buildWithdraw.build(), ContractType.WithdrawBalanceContract);
        break;
      case NativeTransactionContractAbi.CreateWitness_FunctionSelector:
        WitnessContract.WitnessCreateContract.Builder builderWitnessCreate = ethTrx.rlpParseToWitnessCreateContract().toBuilder();
        trxCap = wallet.createTransactionCapsule(builderWitnessCreate.build(), ContractType.WitnessCreateContract);
        break;
      case NativeTransactionContractAbi.UpdateWitness_FunctionSelector:
        WitnessContract.WitnessUpdateContract.Builder builderWitnessUpdate = ethTrx.rlpParseToWitnessUpdateContract().toBuilder();
        trxCap = wallet.createTransactionCapsule(builderWitnessUpdate.build(), ContractType.WitnessUpdateContract);
        break;
      case NativeTransactionContractAbi.UpdateBrokerage_FunctionSelector:
        StorageContract.UpdateBrokerageContract.Builder builderUpdateBrokerage = ethTrx.rlpParseToUpdateBrokerageContract().toBuilder();
        trxCap = wallet.createTransactionCapsule(builderUpdateBrokerage.build(), ContractType.UpdateBrokerageContract);
        break;
      case NativeTransactionContractAbi.ProposalApprove_FunctionSelector:
        ProposalContract.ProposalApproveContract.Builder builderProposalApprove = ethTrx.rlpParseToProposalApproveContract().toBuilder();
        trxCap = wallet.createTransactionCapsule(builderProposalApprove.build(), ContractType.ProposalApproveContract);
        break;
      case NativeTransactionContractAbi.ProposalCreateInteger_FunctionSelector:
      case NativeTransactionContractAbi.ProposalCreateString_FunctionSelector:
        ProposalContract.ProposalCreateContract.Builder builderProposalCreate = ethTrx.rlpParseToProposalCreateContract().toBuilder();
        trxCap = wallet.createTransactionCapsule(builderProposalCreate.build(), ContractType.ProposalCreateContract);
        break;
      case NativeTransactionContractAbi.ProposalDelete_FunctionSelector:
        ProposalContract.ProposalDeleteContract.Builder builderProposalDelete = ethTrx.rlpParseToProposalDeleteContract().toBuilder();
        trxCap = wallet.createTransactionCapsule(builderProposalDelete.build(), ContractType.ProposalDeleteContract);
        break;
      case NativeTransactionContractAbi.AccountUpdate_FunctionSelector:
        AccountContract.AccountUpdateContract.Builder builderAccountUpdate = ethTrx.rlpParseToAccountUpdateContract().toBuilder();
        trxCap = wallet.createTransactionCapsule(builderAccountUpdate.build(), ContractType.AccountUpdateContract);
        break;
      default:
        break;
    }
    if (trxCap == null){
      return null;
    }

    Protocol.Transaction.Builder txBuilder = trxCap.getInstance().toBuilder();
    Protocol.Transaction.raw.Builder rawBuilder = trxCap.getInstance().getRawData().toBuilder();
    txBuilder.setRawData(rawBuilder);
    txBuilder.addSignature(ByteString.copyFrom(ethTrx.getSignature().toByteArray()));

    return txBuilder.build();
  }

  /**
   * evm call vision-core native method
   * @return
   */
  public static String parseEvmCallTransactionData(String data, ChainBaseManager chainBaseManager){
    if (StringUtils.isEmpty(data) || data.length() < 8){
      return null;
    }

    if (data.contains("0x")){
      data = data.substring(2);
    }

    int functionSelectorLength = NativeTransactionContractAbi.TRANSACTION_FUNCTION_SELECTOR_LENGTH;

    String functionSelector = data.substring(0, functionSelectorLength);

    String result = null;

    switch (functionSelector){
      case NativeTransactionContractAbi.GetReward_FunctionSelector:
        String address = TransactionCapsule.EthTrx.parseToVisionAddress(data.substring(functionSelectorLength));
        Map<String, Long> rewardMap = chainBaseManager.getMortgageService().queryAllReward(ByteArray.fromHexString(address));
        DataWord reward = new DataWord(rewardMap.get("reward"));
        DataWord spreadReward = new DataWord(rewardMap.get("spreadReward"));
        result = ByteArray.toJsonHex(reward.toHexString() + spreadReward.toHexString());
        break;
      case NativeTransactionContractAbi.GetBrokerage_FunctionSelector:
        address = TransactionCapsule.EthTrx.parseToVisionAddress(data.substring(functionSelectorLength));
        byte[] addressByte = ByteArray.fromHexString(address);
        long cycle = chainBaseManager.getDynamicPropertiesStore().getCurrentCycleNumber();
        int brokerage = chainBaseManager.getDelegationStore().getBrokerage(cycle, addressByte);
        result = ByteArray.toJsonHex(new DataWord(brokerage).toHexString());
        break;
      case NativeTransactionContractAbi.GetNextMaintenanceTime_FunctionSelector:
        long num = chainBaseManager.getDynamicPropertiesStore().getNextMaintenanceTime();
        result = ByteArray.toJsonHex(new DataWord(num).toHexString());
        break;
      default:
        break;
    }

    return result;
  }

  /**
   * validate to is or not 0x8888888888888888888888888888888888888888
   * @param contractAddress contract_address
   * @return bool
   */
  public static boolean validateContractAddress(String contractAddress){
    if (StringUtils.isEmpty(contractAddress)){
      return false;
    }

    contractAddress = contractAddress.contains("0x") ? contractAddress : "0x" + contractAddress;
    return NativeTransactionContractAbi.TRANSACTION_CONTRACT_ADDRESS_ETH.equals(contractAddress);
  }

}
