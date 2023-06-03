package org.vision.core.services;

import com.alibaba.fastjson.JSON;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;
import org.vision.api.GrpcAPI;
import org.vision.api.GrpcAPI.BytesMessage;
import org.vision.api.GrpcAPI.EstimateEntropyMessage;
import org.vision.common.application.EthereumCompatible;
import org.vision.common.crypto.Hash;
import org.vision.common.logsfilter.ContractEventParser;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.runtime.vm.DataWord;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.ByteUtil;
import org.vision.common.utils.Sha256Hash;
import org.vision.core.ChainBaseManager;
import org.vision.core.Constant;
import org.vision.core.Wallet;
import org.vision.core.capsule.BlockCapsule;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.core.config.Parameter;
import org.vision.core.config.args.Args;
import org.vision.core.db.BlockIndexStore;
import org.vision.core.db2.core.Chainbase;
import org.vision.core.exception.*;
import org.vision.core.services.http.Util;
import org.vision.core.services.jsonrpc.JsonRpcApiUtil;
import org.vision.core.services.jsonrpc.filters.LogBlockQuery;
import org.vision.core.services.jsonrpc.filters.LogFilterWrapper;
import org.vision.core.services.jsonrpc.filters.LogMatch;
import org.vision.core.store.StorageRowStore;
import org.vision.core.vm.program.Storage;
import org.vision.program.Version;
import org.vision.protos.Protocol;
import org.vision.protos.Protocol.Block;
import org.vision.protos.Protocol.Transaction;
import org.vision.protos.Protocol.Transaction.Contract;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.Transaction.Result.code;
import org.vision.api.GrpcAPI.TransactionExtention;
import org.vision.api.GrpcAPI.Return;
import org.vision.api.GrpcAPI.Return.response_code;
import org.vision.protos.contract.Common;
import org.vision.protos.contract.SmartContractOuterClass;
import org.vision.protos.contract.AccountContract.AccountCreateContract;
import org.vision.protos.contract.AssetIssueContractOuterClass.ParticipateAssetIssueContract;
import org.vision.protos.contract.AssetIssueContractOuterClass.TransferAssetContract;
import org.vision.protos.contract.BalanceContract.FreezeBalanceContract;
import org.vision.protos.contract.BalanceContract.TransferContract;
import org.vision.protos.contract.BalanceContract.UnfreezeBalanceContract;
import org.vision.protos.contract.ShieldContract.ShieldedTransferContract;
import org.vision.protos.contract.SmartContractOuterClass.ClearABIContract;
import org.vision.protos.contract.SmartContractOuterClass.TriggerSmartContract;
import org.vision.protos.contract.SmartContractOuterClass.UpdateEntropyLimitContract;
import org.vision.protos.contract.SmartContractOuterClass.UpdateSettingContract;
import org.vision.protos.contract.VoteAssetContractOuterClass.VoteAssetContract;
import org.vision.protos.contract.WitnessContract.VoteWitnessContract;
import org.vision.protos.contract.WitnessContract.VoteWitnessContract.Vote;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.vision.core.db.TransactionTrace.convertToVisionAddress;
import static org.vision.core.services.http.Util.setTransactionExtraData;
import static org.vision.core.services.http.Util.setTransactionPermissionId;
import static org.vision.core.services.jsonrpc.JsonRpcApiUtil.*;

@Slf4j
@Component
public class EthereumCompatibleService implements EthereumCompatible {

    public static final String HASH_REGEX = "(0x)?[a-zA-Z0-9]{64}$";

    public static final String EARLIEST_STR = "earliest";
    public static final String PENDING_STR = "pending";
    public static final String LATEST_STR = "latest";

    private static final String JSON_ERROR = "invalid json request";
    private static final String BLOCK_NUM_ERROR = "invalid block number";
    private static final String TAG_NOT_SUPPORT_ERROR = "TAG [earliest | pending] not supported";
    private static final String QUANTITY_NOT_SUPPORT_ERROR =
            "QUANTITY not supported, just support TAG as latest";
    public static final int EXPIRE_SECONDS = 5 * 60;
    public static final long ESTIMATE_MIN_ENTROPY = 21000L;
    private static final String ERROR_SELECTOR = "08c379a0"; // Function selector for Error(string)

    private Wallet wallet;
    private ChainBaseManager chainBaseManager;
    private ExecutorService executorService;
    private NodeInfoService nodeInfoService;

    public EthereumCompatibleService() {
    }

    public EthereumCompatibleService(NodeInfoService nodeInfoService, Wallet wallet, ChainBaseManager manager) {
        this.nodeInfoService = nodeInfoService;
        this.wallet = wallet;
        this.chainBaseManager = manager;
        this.executorService = Executors.newFixedThreadPool(5);
    }

    @Override
    public String eth_chainId() {
        CommonParameter parameter = Args.getInstance();
        return Constant.ETH_PRE_FIX_STRING_MAINNET + Integer.toHexString(parameter.nodeP2pVersion);
    }

    @Override
    public String web3_clientVersion() {
        Pattern shortVersion = Pattern.compile("(\\d\\.\\d).*");
        Matcher matcher = shortVersion.matcher(System.getProperty("java.version"));
        matcher.matches();

        return String.join("/", Arrays.asList(
                "VISION", "v" + Version.getVersion(),
                System.getProperty("os.name"),
                "Java" + matcher.group(1),
                Version.VERSION_NAME));
    }

    @Override
    public String web3_sha3(String data) throws JsonRpcInvalidParamsException  {
        byte[] input;
        try {
            input = ByteArray.fromHexString(data);
        } catch (Exception e) {
            throw new JsonRpcInvalidParamsException("invalid input value");
        }

        byte[] result = Hash.sha3(input);
        return ByteArray.toJsonHex(result);
    }

    @Override
    public String net_version() {
        CommonParameter parameter = Args.getInstance();
        return Constant.ETH_PRE_FIX_STRING_MAINNET + Integer.toHexString(parameter.nodeP2pVersion);
    }

    @Override
    public String net_peerCount() {
        return ByteArray.toJsonHex(nodeInfoService.getNodeInfo().getPeerList().size());
    }

    @Override
    public boolean net_listening() {
        int activeConnectCount = nodeInfoService.getNodeInfo().getActiveConnectCount();
        return activeConnectCount >= 1;
    }

    @Override
    public String eth_protocolVersion() {
        return null;
    }

    @Override
    public Object eth_syncing() {
        if (nodeInfoService.getNodeInfo().getPeerList().isEmpty()) {
            return false;
        }

        long startingBlockNum = nodeInfoService.getNodeInfo().getBeginSyncNum();
        Block nowBlock = wallet.getNowBlock();
        long currentBlockNum = nowBlock.getBlockHeader().getRawData().getNumber();
        long diff = (System.currentTimeMillis()
                - nowBlock.getBlockHeader().getRawData().getTimestamp()) / 3000;
        diff = diff > 0 ? diff : 0;
        long highestBlockNum = currentBlockNum + diff; // estimated the highest block number

        return new SyncingResult(ByteArray.toJsonHex(startingBlockNum),
                ByteArray.toJsonHex(currentBlockNum),
                ByteArray.toJsonHex(highestBlockNum)
        );
    }

    @Override
    public String eth_coinbase() {
        return null;
    }

    @Override
    public boolean eth_mining() {
        return false;
    }

    @Override
    public String eth_hashrate() {
        return null;
    }

    @Override
    public String eth_gasPrice() {
        // feeLimit = 100000000vdt = 21000 * 160Gwei
        return Constant.ETH_PRE_FIX_STRING_MAINNET + Long.toHexString(CommonParameter.PARAMETER.gasPrice);
    }

    @Override
    public String[] eth_accounts() {
        return new String[0];
    }

    @Override
    public String eth_blockNumber() {
        Protocol.Block reply = wallet.getNowBlock();
        return Constant.ETH_PRE_FIX_STRING_MAINNET + Long.toHexString(reply.getBlockHeader().getRawData().getNumber()).toLowerCase();
    }

    @Override
    public String eth_getBalance(String address, String block) throws Exception {
        Protocol.Account.Builder build = Protocol.Account.newBuilder();
        build.setAddress(ByteString.copyFrom(ByteArray.fromHexString(address.replace(Constant.ETH_PRE_FIX_STRING_MAINNET, Constant.ADD_PRE_FIX_STRING_MAINNET).toLowerCase())));
        Protocol.Account reply = wallet.getAccount(build.build());
        return null == reply ? Constant.ETH_PRE_FIX_STRING_MAINNET + "0" : Constant.ETH_PRE_FIX_STRING_MAINNET + new BigInteger(reply.getBalance() + "").multiply(new BigInteger("1000000000000")).toString(16);
    }

    @Override
    public String eth_getLastBalance(String address) throws Exception {
        return null;
    }

    @Override
    public FeeHistory eth_feeHistory(String blockCount, String newestBlock, List<Double> rewardPercentiles) throws Exception {
        if (EARLIEST_STR.equalsIgnoreCase(newestBlock)
                || PENDING_STR.equalsIgnoreCase(newestBlock)) {
            throw new JsonRpcInvalidParamsException(TAG_NOT_SUPPORT_ERROR);
        } else if (LATEST_STR.equalsIgnoreCase(newestBlock)) {
            FeeHistory feeHistory = new FeeHistory();
            try{
                long blockCountNumber = ByteArray.hexToBigInteger(blockCount).longValue();
                if (blockCountNumber <= 0){
                    feeHistory.setOldestBlock("0x0");
                    feeHistory.setGasUsedRatio(null);
                    return feeHistory;
                }

                Block block = wallet.getByJsonBlockId(newestBlock);
                long newestBlockNumber = block.getBlockHeader().getRawData().getNumber();
                long oldestBlockNumber = newestBlockNumber - (blockCountNumber - 1);
                String oldestBlock = Long.toHexString(oldestBlockNumber);
                feeHistory.setOldestBlock(oldestBlock);
                long rewardCount = 0;
                if (rewardPercentiles != null){
                    rewardCount = rewardPercentiles.size();
                }

                List<String> baseFeePerGas = new ArrayList<>();
                List<Double> gasUsedRatio = new ArrayList<>();
                List<List<String>> reward = new ArrayList<>();

                for (int i = 0; i < blockCountNumber; i++){
                    baseFeePerGas.add(eth_gasPrice());
                    Block tmpBlock = wallet.getBlockByNum(newestBlockNumber - i);
                    BlockCapsule blockCapsule = new BlockCapsule(block);
                    long totalEntropy = 0, feeLimit = 0;
                    if (tmpBlock.getTransactionsCount() > 0){
                        for (TransactionCapsule transactionCapsule: blockCapsule.getTransactions()){
                            Transaction.Contract contract = transactionCapsule.getInstance().getRawData().getContract(0);
                            if (!contract.getType().equals(ContractType.TriggerSmartContract) && !contract.getType().equals(ContractType.CreateSmartContract)){
                                continue;
                            }
                            Protocol.TransactionInfo transactionInfo = wallet.getTransactionInfoById(ByteString.copyFrom(transactionCapsule.getTransactionId().getBytes()));
                            totalEntropy += transactionInfo.getReceipt().getEntropyUsageTotal();
                            feeLimit += transactionCapsule.getFeeLimit();
                        }
                    }
                    double gasUsedRatioVal = feeLimit > 0 ? totalEntropy * 1.0 / feeLimit : 0.0;
                    gasUsedRatioVal = Math.max(Math.min(1, gasUsedRatioVal), 0);
                    gasUsedRatio.add(gasUsedRatioVal);

                    if (rewardCount > 0) {
                        List<String> rewardItem = new ArrayList<>();
                        for (int j = 0; j < rewardCount; j++) {
                            rewardItem.add("0x0");
                        }
                        reward.add(rewardItem);
                    }
                }
                feeHistory.setBaseFeePerGas(baseFeePerGas);
                feeHistory.setGasUsedRatio(gasUsedRatio);
                feeHistory.setReward(reward);

                return feeHistory;
            }catch (Exception e){
                String message = e.getMessage();
                throw new JsonRpcInternalException(message);
            }
        }else {
            try {
                ByteArray.hexToBigInteger(newestBlock);
            } catch (Exception e) {
                throw new JsonRpcInvalidParamsException(BLOCK_NUM_ERROR);
            }

            throw new JsonRpcInvalidParamsException(QUANTITY_NOT_SUPPORT_ERROR);
        }
    }

    @Override
    public String eth_getStorageAt(String address, String storageIdx, String blockId) throws Exception {
        if (EARLIEST_STR.equalsIgnoreCase(blockId)
                || PENDING_STR.equalsIgnoreCase(blockId)) {
            throw new JsonRpcInvalidParamsException(TAG_NOT_SUPPORT_ERROR);
        } else if (LATEST_STR.equalsIgnoreCase(blockId)) {
            byte[] addressByte = addressCompatibleToByteArray(address);

            // get contract from contractStore
            BytesMessage.Builder build = BytesMessage.newBuilder();
            BytesMessage bytesMessage = build.setValue(ByteString.copyFrom(addressByte)).build();
            SmartContractOuterClass.SmartContract smartContract = wallet.getContract(bytesMessage);
            if (smartContract == null) {
                return ByteArray.toJsonHex(new byte[32]);
            }

            StorageRowStore store = chainBaseManager.getStorageRowStore();
            Storage storage = new Storage(addressByte, store);

            DataWord value = storage.getValue(new DataWord(ByteArray.fromHexString(storageIdx)));
            return ByteArray.toJsonHex(value == null ? new byte[32] : value.getData());
        } else {
            try {
                ByteArray.hexToBigInteger(blockId);
            } catch (Exception e) {
                throw new JsonRpcInvalidParamsException(BLOCK_NUM_ERROR);
            }

            throw new JsonRpcInvalidParamsException(QUANTITY_NOT_SUPPORT_ERROR);
        }
    }

    @Override
    public String eth_getTransactionCount(String address, String blockId) throws Exception {
        Protocol.Block reply = wallet.getNowBlock();
        return Constant.ETH_PRE_FIX_STRING_MAINNET + Long.toHexString(reply.getBlockHeader().getRawData().getNumber()).toLowerCase();
    }

    @Override
    public String eth_getBlockTransactionCountByHash(String blockHash) throws JsonRpcInvalidParamsException {
        Block b = getBlockByJsonHash(blockHash);
        if (b == null) {
            return null;
        }

        long n = b.getTransactionsList().size();
        return ByteArray.toJsonHex(n);
    }

    @Override
    public String eth_getBlockTransactionCountByNumber(String bnOrId) throws JsonRpcInvalidParamsException {
        List<Transaction> list = wallet.getTransactionsByJsonBlockId(bnOrId);
        if (list == null) {
            return null;
        }

        long n = list.size();
        return ByteArray.toJsonHex(n);
    }

    @Override
    public String eth_getUncleCountByBlockHash(String blockHash) throws Exception {
        return null;
    }

    @Override
    public String eth_getUncleCountByBlockNumber(String bnOrId) throws Exception {
        return null;
    }

    @Override
    public String eth_getCode(String addr, String bnOrId) throws Exception {
        if (EARLIEST_STR.equalsIgnoreCase(bnOrId)
                || PENDING_STR.equalsIgnoreCase(bnOrId)) {
            throw new Exception(TAG_NOT_SUPPORT_ERROR);
        } else if (LATEST_STR.equalsIgnoreCase(bnOrId)) {
            byte[] addressData = getHexNo0x(addr).getBytes();

            BytesMessage.Builder build = BytesMessage.newBuilder();
            BytesMessage bytesMessage = build.setValue(ByteString.copyFrom(addressData)).build();
            SmartContractOuterClass.SmartContractDataWrapper contractDataWrapper = wallet.getContractInfo(bytesMessage);

            if (contractDataWrapper != null) {
                return ByteArray.toJsonHex(contractDataWrapper.getRuntimecode().toByteArray());
            } else {
                return "0x";
            }

        } else {
            try {
                ByteArray.hexToBigInteger(bnOrId);
            } catch (Exception e) {
                throw new JsonRpcInvalidParamsException(BLOCK_NUM_ERROR);
            }

            throw new JsonRpcInvalidParamsException(QUANTITY_NOT_SUPPORT_ERROR);
        }
    }

    @Override
    public String eth_sign(String addr, String data) throws Exception {
        return null;
    }

    @Override
    public String eth_sendTransaction(CallArguments transactionArgs) throws Exception {
        return null;
    }

    @Override
    public String eth_sendRawTransaction(String rawData) throws Exception {
        if (chainBaseManager.getDynamicPropertiesStore().getAllowEthereumCompatibleTransaction() == 0) {
            logger.info("AllowEthereumCompatibleTransaction is off");
            return null;
        }
        TransactionCapsule.EthTrx ethTrx = new TransactionCapsule.EthTrx(ByteArray.fromHexString(rawData));
        ethTrx.rlpParse();
        GrpcAPI.TransactionExtention.Builder trxExtBuilder = GrpcAPI.TransactionExtention.newBuilder();
        GrpcAPI.Return.Builder retBuilder = GrpcAPI.Return.newBuilder();
        Protocol.Transaction trx = null;
        TransactionCapsule transactionCapsule = null;
        try {
            byte[] receiveAddressStr = ethTrx.getReceiveAddress();
            boolean isDeployContract = (null == receiveAddressStr || receiveAddressStr.length == 0);
            String data = ByteArray.toHexString(ethTrx.getData());

            boolean isOldTransaction = true;
            if (chainBaseManager.getDynamicPropertiesStore().supportEthereumCompatibleTransactionNativeStep1()){
                if (JsonRpcApiUtil.validateContractAddress(ByteArray.toJsonHex(receiveAddressStr))){
                    isOldTransaction = false;
                    trx = JsonRpcApiUtil.parseEvmTransactionData(ethTrx, wallet);
                }
            }else {
                if (JsonRpcApiUtil.validateContractAddress(ByteArray.toJsonHex(receiveAddressStr))){
                    throw new JsonRpcInvalidParamsException("not support contract: " + Parameter.NativeTransactionContractAbi.TRANSACTION_CONTRACT_ADDRESS_ETH);
                }
            }

            if (isOldTransaction){
                if (isDeployContract) {
                    if (StringUtils.isBlank(data)) {
                        throw new IllegalArgumentException("no data!");
                    }
                }
                byte[] receiveAddress = ByteArray.fromHexString(Constant.ADD_PRE_FIX_STRING_MAINNET + ByteArray.toHexString(ethTrx.getReceiveAddress()));
                int accountType = wallet.getAccountType(receiveAddress);
                if ((1 == accountType && !StringUtils.isBlank(data)) || isDeployContract) {
                    long gasPriceTmp = Long.parseLong( toHexString(ethTrx.getGasPrice()), 16);
                    double gasPrice = gasPriceTmp / 1_000_000_000.00;
                    long gasLimit = Long.parseLong(toHexString(ethTrx.getGasLimit()), 16);
                    long feeLimit = (long) gasPrice * gasLimit * 2;
                    Message message = null;
                    Protocol.Transaction.Contract.ContractType contractType = null;
                    if (isDeployContract) {
                        message = ethTrx.rlpParseToDeployContract(chainBaseManager.getDynamicPropertiesStore());
                        contractType = Protocol.Transaction.Contract.ContractType.CreateSmartContract;
                    } else {
                        message = ethTrx.rlpParseToTriggerSmartContract(chainBaseManager.getDynamicPropertiesStore());
                        contractType = Protocol.Transaction.Contract.ContractType.TriggerSmartContract;
                    }
                    TransactionCapsule trxCap = wallet
                            .createTransactionCapsule(message, contractType);
                    Protocol.Transaction.Builder txBuilder = trxCap.getInstance().toBuilder();
                    Protocol.Transaction.raw.Builder rawBuilder = trxCap.getInstance().getRawData().toBuilder();
                    rawBuilder.setFeeLimit(feeLimit);
                    logger.info("rawData={}", rawBuilder);
                    txBuilder.setRawData(rawBuilder);
                    txBuilder.addSignature(ByteString.copyFrom(ethTrx.getSignature().toByteArray()));
                    if (isDeployContract) {
                        trx = txBuilder.build();
                    } else {
                        trx = wallet.triggerContract(ethTrx.rlpParseToTriggerSmartContract(chainBaseManager.getDynamicPropertiesStore()), new TransactionCapsule(txBuilder.build()), trxExtBuilder,
                                retBuilder);
                    }
                } else {
                    TransactionCapsule transactionCapsule1 = wallet.createTransactionCapsule(ethTrx.rlpParseToTransferContract(), Protocol.Transaction.Contract.ContractType.TransferContract);
                    trx = transactionCapsule1.getInstance().toBuilder().addSignature(ByteString.copyFrom(ethTrx.getSignature().toByteArray())).build();
                }
            }

            GrpcAPI.Return result = wallet.broadcastTransaction(trx);
            transactionCapsule = new TransactionCapsule(trx);
            if (GrpcAPI.Return.response_code.SUCCESS != result.getCode()) {
                logger.error("Broadcast transaction {} has failed, {}.", transactionCapsule.getTransactionId(), result.getMessage().toStringUtf8());
                String errMsg = new String(result.getMessage().toByteArray(), StandardCharsets.UTF_8);

                throw new JsonRpcInternalException(errMsg);
            }
        } catch (Exception e) {
            logger.error("sendRawTransaction error", e);
            String errString = null;
            if (e.getMessage() != null) {
                errString = e.getMessage().replaceAll("[\"]", "\'");
            }
            if (StringUtils.isNotEmpty(errString)){
                throw new JsonRpcInternalException(errString);
            }
            return null;
        }
        String trxHash = ByteArray.toJsonHex(transactionCapsule.getTransactionId().getBytes());
        logger.info("trxHash={}", trxHash);
        return trxHash;
    }

    @Override
    public String eth_call(CallArguments args, String bnOrId) throws Exception {
        SmartContractOuterClass.TriggerSmartContract.Builder build = SmartContractOuterClass.TriggerSmartContract.newBuilder();
        GrpcAPI.TransactionExtention.Builder trxExtBuilder = GrpcAPI.TransactionExtention.newBuilder();
        GrpcAPI.Return.Builder retBuilder = GrpcAPI.Return.newBuilder();
        try {
            if (chainBaseManager.getDynamicPropertiesStore().supportEthereumCompatibleTransactionNativeStep1()){
                if (JsonRpcApiUtil.validateContractAddress(args.getTo())){
                    return JsonRpcApiUtil.parseEvmCallTransactionData(args.getData(), chainBaseManager);
                }
            }else {
                if(JsonRpcApiUtil.validateContractAddress(args.getTo())){
                    logger.info("AllowEthereumCompatibleTransactionNativeStep1 is off");
                    return null;
                }
            }

            build.setData(ByteString.copyFrom(ByteArray.fromHexString(args.getData())));
            build.setContractAddress(ByteString.copyFrom(ByteArray.fromHexString(args.getTo().replace(Constant.ETH_PRE_FIX_STRING_MAINNET, Constant.ADD_PRE_FIX_STRING_MAINNET).toLowerCase())));
            build.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString("460000000000000000000000000000000000000000")));
            TransactionCapsule trxCap = wallet
                    .createTransactionCapsule(build.build(), Protocol.Transaction.Contract.ContractType.TriggerSmartContract);
            Protocol.Transaction.Builder txBuilder = trxCap.getInstance().toBuilder();
            Protocol.Transaction.raw.Builder rawBuilder = trxCap.getInstance().getRawData().toBuilder();
            rawBuilder.setFeeLimit(0);
            txBuilder.setRawData(rawBuilder);
            Protocol.Transaction trx = wallet
                    .triggerConstantContract(build.build(), new TransactionCapsule(txBuilder.build()),
                            trxExtBuilder,
                            retBuilder);
            trxExtBuilder.setTransaction(trx);
            retBuilder.setResult(true).setCode(GrpcAPI.Return.response_code.SUCCESS);
        } catch (ContractValidateException e) {
            retBuilder.setResult(false).setCode(GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR)
                    .setMessage(ByteString.copyFromUtf8(e.getMessage()));
        } catch (Exception e) {
            String errString = null;
            if (e.getMessage() != null) {
                errString = e.getMessage().replaceAll("[\"]", "\'");
            }
            retBuilder.setResult(false).setCode(GrpcAPI.Return.response_code.OTHER_ERROR)
                    .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + errString));
        }
        trxExtBuilder.setResult(retBuilder);
        if(trxExtBuilder.build().getConstantResultCount()>0){
            return "0x" + ByteArray.toHexString(trxExtBuilder.build().getConstantResult(0).toByteArray());
        }else{
            return null;
        }
    }

    @Override
    public String eth_estimateGas(CallArguments args) throws Exception {
        byte[] ownerAddress = addressCompatibleToByteArray(args.getFrom());

        ContractType contractType = args.getContractType(wallet);
        if (contractType == ContractType.TransferContract) {
            buildTransferContractTransaction(ownerAddress, new BuildArguments(args));
            return "0x0";
        }

        boolean supportEstimateEntropy = CommonParameter.getInstance().isEstimateEntropy();

        TransactionExtention.Builder trxExtBuilder = TransactionExtention.newBuilder();
        Return.Builder retBuilder = Return.newBuilder();
        EstimateEntropyMessage.Builder estimateBuilder = EstimateEntropyMessage.newBuilder();

        try {
            byte[] contractAddress;

            if (contractType == ContractType.TriggerSmartContract) {
                contractAddress = addressCompatibleToByteArray(args.getTo());
            } else {
                contractAddress = new byte[0];
            }
            if (supportEstimateEntropy){
                estimateEntropy(ownerAddress,
                        contractAddress,
                        args.parseValue(),
                        ByteArray.fromHexString(args.getData()),
                        trxExtBuilder,
                        retBuilder,
                        estimateBuilder);
            }else {
                callTriggerConstantContract(ownerAddress,
                        contractAddress,
                        args.parseValue(),
                        ByteArray.fromHexString(args.getData()),
                        trxExtBuilder,
                        retBuilder);
            }

            if (trxExtBuilder.getTransaction().getRet(0).getRet().equals(code.FAILED)) {
                String errMsg = retBuilder.getMessage().toStringUtf8();

                byte[] data = trxExtBuilder.getConstantResult(0).toByteArray();
                if (data.length > 4 && Hex.toHexString(data).startsWith(ERROR_SELECTOR)) {
                    String msg = ContractEventParser
                            .parseDataBytes(Arrays.copyOfRange(data, 4, data.length),
                                    "string", 0);
                    errMsg += ": " + msg;
                }

                throw new JsonRpcInternalException(errMsg);
            } else {

                if (supportEstimateEntropy) {
                    return ByteArray.toJsonHex(Math.max(estimateBuilder.getEntropyRequired(), ESTIMATE_MIN_ENTROPY));
                } else {
                    return ByteArray.toJsonHex(Math.max(trxExtBuilder.getEntropyUsed(), ESTIMATE_MIN_ENTROPY));
                }

            }
        } catch (ContractValidateException e) {
            String errString = "invalid contract";
            if (e.getMessage() != null) {
                errString = e.getMessage();
            }

            throw new JsonRpcInvalidRequestException(errString);
        } catch (Exception e) {
            String errString = JSON_ERROR;
            if (e.getMessage() != null) {
                errString = e.getMessage().replaceAll("[\"]", "'");
            }

            throw new JsonRpcInternalException(errString);
        }
    }

    private void estimateEntropy(byte[] ownerAddressByte, byte[] contractAddressByte,
                                long value, byte[] data, TransactionExtention.Builder trxExtBuilder,
                                Return.Builder retBuilder, EstimateEntropyMessage.Builder estimateBuilder)
            throws ContractValidateException, ContractExeException, HeaderNotFound, VMIllegalException {

        TriggerSmartContract triggerContract = triggerCallContract(
                ownerAddressByte,
                contractAddressByte,
                value,
                data,
                0,
                null
        );

        TransactionCapsule trxCap = wallet.createTransactionCapsule(triggerContract,
                ContractType.TriggerSmartContract);
        Transaction trx =
                wallet.estimateEntropy(triggerContract, trxCap, trxExtBuilder, retBuilder, estimateBuilder);
        trxExtBuilder.setTransaction(trx);
        trxExtBuilder.setTxid(trxCap.getTransactionId().getByteString());
        trxExtBuilder.setResult(retBuilder);
        retBuilder.setResult(true).setCode(response_code.SUCCESS);
        estimateBuilder.setResult(retBuilder);
    }

    private void callTriggerConstantContract(byte[] ownerAddressByte, byte[] contractAddressByte,
                                             long value, byte[] data, TransactionExtention.Builder trxExtBuilder,
                                             Return.Builder retBuilder)
            throws ContractValidateException, ContractExeException, HeaderNotFound, VMIllegalException {

        TriggerSmartContract triggerContract = triggerCallContract(
                ownerAddressByte,
                contractAddressByte,
                value,
                data,
                0,
                null
        );

        TransactionCapsule trxCap = wallet.createTransactionCapsule(triggerContract,
                ContractType.TriggerSmartContract);
        Transaction trx =
                wallet.triggerConstantContract(triggerContract, trxCap, trxExtBuilder, retBuilder);

        trxExtBuilder.setTransaction(trx);
        trxExtBuilder.setTxid(trxCap.getTransactionId().getByteString());
        trxExtBuilder.setResult(retBuilder);
        retBuilder.setResult(true).setCode(response_code.SUCCESS);
    }

    private TransactionJson buildTransferContractTransaction(byte[] ownerAddress,
                                                             BuildArguments args) throws JsonRpcInvalidParamsException, JsonRpcInvalidRequestException,
            JsonRpcInternalException {
        long amount = args.parseValue();

        TransferContract.Builder build = TransferContract.newBuilder();
        build.setOwnerAddress(ByteString.copyFrom(ownerAddress))
                .setToAddress(ByteString.copyFrom(addressCompatibleToByteArray(args.getTo())))
                .setAmount(amount);

        return createTransactionJson(build, ContractType.TransferContract, args);
    }

    private TransactionJson createTransactionJson(GeneratedMessageV3.Builder<?> build,
                                                  ContractType contractTyp, BuildArguments args)
            throws JsonRpcInvalidRequestException, JsonRpcInternalException {
        try {
            Transaction tx = wallet
                    .createTransactionCapsule(build.build(), contractTyp)
                    .getInstance();
            tx = setTransactionPermissionId(args.getPermissionId(), tx);
            tx = setTransactionExtraData(args.getExtraData(), tx, args.isVisible());

            TransactionJson transactionJson = new TransactionJson();
            transactionJson
                    .setTransaction(JSON.parseObject(Util.printCreateTransaction(tx, args.isVisible())));

            return transactionJson;
        } catch (ContractValidateException e) {
            throw new JsonRpcInvalidRequestException(e.getMessage());
        } catch (Exception e) {
            throw new JsonRpcInternalException(e.getMessage());
        }
    }

    @Override
    public BlockResult eth_getBlockByHash(String blockHash, Boolean fullTransactionObjects) throws Exception {
        ByteString blockId = ByteString.copyFrom(ByteArray.fromHexString(blockHash));
        Protocol.Block reply = wallet.getBlockById(blockId);

        BlockResult blockResult = new BlockResult();
        transferBlock2Ether(blockResult, reply, fullTransactionObjects);
        return blockResult;
    }

    private final AtomicLong counter = new AtomicLong();

    public BlockResult eth_getBlockByNumberOld(String bnOrId, Boolean fullTransactionObjects) throws Exception {
        BlockResult blockResult = new BlockResult();
        blockResult.difficulty = "0x2";
        blockResult.extraData = "0xd883010003846765746888676f312e31332e34856c696e75780000000000000049116d665d92e4581c19cd8a67a316739ec2faa4d3e8d3fc518ad6c9e02dc51154bcd4ffbf3156d9d8265500c6bc775ff05b5a54650397fdd057f1d9cb98f6a501";
        blockResult.gasLimit = "0x1ca35b8";
        blockResult.gasUsed = "0x1ca0e7f";
        blockResult.hash = "0xc3bf874d7791a23e0ea85ec230c06d52d1b31b6681d040ddb61bd71ef0a78fd6";
        blockResult.logsBloom = "0x01a134c10a00418818c20640828220c1844241248a880c100043028425806200220a50010002153408040840100008219d01896016b0023994012881502808801102e000098e02400600800801a80022042948738908140e88020840040556004040012822208c460479000a00c158007814140404d40040000404101a502322001120050001812a8801670448a2480000c012159003506810004048006200145672100090a4080a10c80c00c04000220200000020a638a648401046053003406000605b04028006004149080088c8190044448427008019c0430003cf40a20010150042080c00020d104000402c8e20000500244112800a4202818200010000";
        blockResult.miner = "0x0ed0d1f46391e08f0ceab98fc3700e5d9c1c7b19";
        blockResult.mixHash = "0x0000000000000000000000000000000000000000000000000000000000000000";
        blockResult.nonce = "0x0000000000000000";
        //blockResult.number = "0x2d6c2e";
        blockResult.number = "0x" + Long.toHexString(counter.incrementAndGet()).toLowerCase();
        blockResult.parentHash = "0xa540368c3f57b1d43f5691928023e52abaf9cbe2267cf0abbec7263717aeb28c";
        blockResult.receiptsRoot = "0x5f695898d27a2e25c2ae05c436be37a38bc3f8993386bf409f0ce40d6292298c";
        blockResult.sha3Uncles = "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347";
        blockResult.stateRoot = "0x9274579544e15879ee336be728bced7ca425204f32955603aa360531930ca92e";
        blockResult.timestamp = "0x604f84aa";
        blockResult.totalDifficulty = "0x5abd10";
        blockResult.transactions = new Object[0];
        blockResult.transactionsRoot = "0x5aca89320d43c0d547a4cbd545ee942a23131e3be1f440ba12603c99428d2f80";
        blockResult.uncles = new String[0];
        return blockResult;
    }

    @Override
    public BlockResult eth_getBlockByNumber(String bnOrId, Boolean fullTransactionObjects) throws Exception {
        logger.info("bnOrId={}", bnOrId);
        BlockResult blockResult = new BlockResult();
        Protocol.Block reply = null;
        if ("latest".equals(bnOrId)) {
            reply = wallet.getNowBlock();
        } else {
            // transfer bnOrId type from string to long
            long num = Long.parseLong(getHexNo0x(bnOrId), 16);
            // long num = bnOrId;
            reply = wallet.getBlockByNum(num);
        }

        transferBlock2Ether(blockResult, reply, fullTransactionObjects);

        return blockResult;
    }

    private void transferBlock2Ether(BlockResult blockResult, Protocol.Block reply,
                                     Boolean fullTransactionObjects) throws ItemNotFoundException {
        if (reply == null){
            logger.info("transferBlock2Ether, reply is null");
            return;
        }

        Protocol.BlockHeader visionBlockHeader = reply.getBlockHeader();

        Protocol.BlockHeader blockHeader = reply.getBlockHeader();
        Protocol.BlockHeader.raw rawData = blockHeader.getRawData();

        blockResult.difficulty = "0x00000";
        blockResult.extraData = "0x00000000000000000000000000000000000000000000";
        blockResult.gasLimit = "0x0000000";
        blockResult.gasUsed = "0x0000000";
        BlockIndexStore blockIndexStore = chainBaseManager.getBlockIndexStore();
        BlockCapsule.BlockId blockId = blockIndexStore.get(blockHeader.getRawData().getNumber());
        blockResult.hash = "0x" + toHexString(blockId.getByteString().toByteArray());
        blockResult.logsBloom = "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
        byte[] addressTmp = blockHeader.getRawData().getWitnessAddress().toByteArray();
        String hexAddress = getAddrNo46(ByteUtil.toHexString(addressTmp));
        if (hexAddress.length() == 40) {
            blockResult.miner = "0x" + hexAddress;
        } else {
            blockResult.miner = "0x0000000000000000000000000000000000000000";
        }
        logger.info("miner=[{}]", blockResult.miner);
        blockResult.mixHash = "0x0000000000000000000000000000000000000000000000000000000000000000";
        blockResult.nonce = "0x0000000000000000";
        blockResult.number = "0x" + Long.toHexString(rawData.getNumber()); // block height
        blockResult.parentHash = "0x" + toHexString(rawData.getParentHash().toByteArray()); // father block hash

        blockResult.receiptsRoot = "0x0000000000000000000000000000000000000000000000000000000000000000";
        blockResult.sha3Uncles = "0x0000000000000000000000000000000000000000000000000000000000000000";
        blockResult.stateRoot = "0x0000000000000000000000000000000000000000000000000000000000000000";
        blockResult.totalDifficulty = "0x000000";
        blockResult.timestamp = "0x" + Long.toHexString(rawData.getTimestamp() / 1000);
        blockResult.size = Constant.ETH_PRE_FIX_STRING_MAINNET + "0";
        List<Protocol.Transaction> transactionList = reply.getTransactionsList();
        List<String> transHashList = new ArrayList<>();
        List<TransactionResultDTO> tranFullList = new ArrayList<>();
        if (transactionList.size() > 0) {
            long transactionIdx = 0;
            for (Protocol.Transaction trx : transactionList) {
                // eth_getBlockByHash actually get block by txId for vision-core
                String txID = ByteArray.toHexString(Sha256Hash
                        .hash(CommonParameter.getInstance().isECKeyCryptoEngine(),
                                trx.getRawData().toByteArray()));
                String hash = "0x" + txID;
                transHashList.add(hash);

                TransactionResultDTO tranDTO = new TransactionResultDTO();
                Protocol.TransactionInfo trxInfo = wallet.getTransactionInfoById(ByteString.copyFrom(txID.getBytes()));
                transferTransactionInfoToEther(tranDTO, trx, trxInfo);
//                tranDTO.blockHash = blockResult.hash;
                tranDTO.transactionIndex = "0x" + Long.toHexString(transactionIdx++);
                tranFullList.add(tranDTO);
            }
        }
        if (fullTransactionObjects) {
            blockResult.transactions = tranFullList.toArray();
        } else {
            blockResult.transactions = transHashList.toArray();
        }

        blockResult.transactionsRoot = "0x" + toHexString(rawData.getTxTrieRoot().toByteArray());
        blockResult.uncles = new String[]{};
    }


    @Override
    public TransactionResultDTO eth_getTransactionByHash(String txId) throws Exception {
        TransactionResultDTO transactionResultDTO = new TransactionResultDTO();
        ByteString transactionId = ByteString.copyFrom(ByteArray.fromHexString(txId.substring(2, txId.length())));
        Protocol.TransactionInfo transactionInfo = wallet.getTransactionInfoById(transactionId);
        Transaction transaction = wallet.getTransactionById(transactionId);
        transferTransactionInfoToEther(transactionResultDTO, transaction, transactionInfo);
        return transactionResultDTO;
    }

    private void transferTransactionInfoToEther(TransactionResultDTO transactionResultDTO,
                                                Protocol.Transaction transaction,
                                                Protocol.TransactionInfo transactionInfo) {
        if (transaction == null){
            return;
        }
        byte[] txId = new TransactionCapsule(transaction).getTransactionId().getBytes();
        String hash = ByteArray.toJsonHex(txId);

        transactionResultDTO.chainId = eth_chainId();
        transactionResultDTO.condition = null;
        transactionResultDTO.creates = null;
        transactionResultDTO.publicKey = "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
        transactionResultDTO.r = "0x0000000000000000000000000000000000000000000000000000000000000000";
        transactionResultDTO.raw = ByteArray.toJsonHex(transaction.getRawData().toByteArray());
        transactionResultDTO.s = "0x0000000000000000000000000000000000000000000000000000000000000000";
        transactionResultDTO.standardV = "0x0";
        transactionResultDTO.transactionIndex = "0x0";
        transactionResultDTO.v = "0x00";
        transactionResultDTO.value = "0x0";
        transactionResultDTO.gasPrice = eth_gasPrice();
        transactionResultDTO.from = null;
        transactionResultDTO.to = null;

        if (!transaction.getRawData().getContractList().isEmpty()) {
            Contract contract = transaction.getRawData().getContract(0);
            byte[] ownerAddress = TransactionCapsule.getOwner(contract);
            byte[] toAddress = getToAddress(transaction);
            transactionResultDTO.from = ByteArray.toJsonHexAddress(ownerAddress);
            transactionResultDTO.to =  ByteArray.toJsonHexAddress(toAddress);
            transactionResultDTO.value = ByteArray.toJsonHex(getTransactionAmount(contract, hash, wallet, chainBaseManager));
        }
        String txID = ByteArray.toHexString(Sha256Hash
                .hash(CommonParameter.getInstance().isECKeyCryptoEngine(),
                        transaction.getRawData().toByteArray()));
        transactionResultDTO.hash =  "0x" + txID;
        transactionResultDTO.input = ByteArray.toJsonHex(transaction.getRawData().getData().toByteArray());

        if (transactionInfo == null){
            transactionResultDTO.nonce = "0x0";
            transactionResultDTO.gas = "0x0";
            return;
        }
        Block block = wallet.getBlockByNum(transactionInfo.getBlockNumber());
        BlockCapsule blockCapsule = new BlockCapsule(block);
        transactionResultDTO.blockHash = ByteArray.toJsonHex(blockCapsule.getBlockId().getBytes());
        transactionResultDTO.blockNumber = ByteArray.toJsonHex(blockCapsule.getNum());
        long entropyUsageTotal = transactionInfo.getReceipt().getEntropyUsageTotal();
        transactionResultDTO.gas = ByteArray.toJsonHex(entropyUsageTotal);

        try {
            transactionResultDTO.nonce = eth_getTransactionCount(transactionResultDTO.from, blockCapsule.getBlockId().toString());
        }catch (Exception exception){
            transactionResultDTO.nonce = "0x";
        }

        int transactionIndex = -1;

        List<Transaction> txList = block.getTransactionsList();
        for (int index = 0; index < txList.size(); index++) {
            transaction = txList.get(index);
            if (getTxID(transaction).equals(txID)) {
                transactionIndex = index;
                break;
            }
        }

        if (transactionIndex == -1) {
            return;
        }

        transactionResultDTO.transactionIndex = ByteArray.toJsonHex(transactionIndex);

        if (transaction.getSignatureCount() == 0) {
            transactionResultDTO.v = null;
            transactionResultDTO.r = null;
            transactionResultDTO.s = null;
            return;
        }

        ByteString signature = transaction.getSignature(0); // r[32] + s[32] + v[1]
        byte[] signData = signature.toByteArray();
        byte[] rByte = Arrays.copyOfRange(signData, 0, 32);
        byte[] sByte = Arrays.copyOfRange(signData, 32, 64);
        byte vByte = signData[64];
        if (vByte < 27) {
            vByte += 27;
        }
        transactionResultDTO.v = ByteArray.toJsonHex(vByte);
        transactionResultDTO.r = ByteArray.toJsonHex(rByte);
        transactionResultDTO.s = ByteArray.toJsonHex(sByte);
    }

    private void transferTransaction2Ether(TransactionResultDTO transactionResultDTO,
                                           Protocol.Transaction transaction) {
        if (transaction ==null){
            return;
        }
        Protocol.Transaction.raw rawData = transaction.getRawData();
        // Protocol.Transaction.Contract contract = rawData.getContract(0);
        transactionResultDTO.blockHash = "0x" + toHexString(rawData.getRefBlockHash().toByteArray());
        transactionResultDTO.blockNumber = "0x" + Long.toHexString(transaction.getRawData().getRefBlockNum());
        transactionResultDTO.chainId = eth_chainId();
        transactionResultDTO.condition = null;
        transactionResultDTO.creates = null;
        // compatible remix
        transactionResultDTO.gasPrice = eth_gasPrice();
        transactionResultDTO.from = null;
        transactionResultDTO.to = null;

        if (!transaction.getRawData().getContractList().isEmpty()) {
            Contract contract = transaction.getRawData().getContract(0);
            byte[] ownerAddress = TransactionCapsule.getOwner(contract);
            byte[] toAddress = getToAddress(transaction);
            transactionResultDTO.from = ByteArray.toJsonHexAddress(ownerAddress);
            transactionResultDTO.to =  ByteArray.toJsonHexAddress(toAddress);
        }

        String txID = ByteArray.toHexString(Sha256Hash
                .hash(CommonParameter.getInstance().isECKeyCryptoEngine(),
                        transaction.getRawData().toByteArray()));
        transactionResultDTO.hash = "0x" + txID;
        transactionResultDTO.input = "0x0000000000000000000000000000000000000000000000000000000000000000";
        transactionResultDTO.nonce = "0x00000";
        transactionResultDTO.publicKey = "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
        transactionResultDTO.r = "0x0000000000000000000000000000000000000000000000000000000000000000";
        String rawDataHex = ByteArray.toHexString(transaction.getRawData().toByteArray());
        transactionResultDTO.raw = "0x" + rawDataHex;
        transactionResultDTO.s = "0x0000000000000000000000000000000000000000000000000000000000000000";
        transactionResultDTO.standardV = "0x0";
        transactionResultDTO.transactionIndex = "0x0";
        transactionResultDTO.v = "0x00";
        transactionResultDTO.value = "0x0";
    }

    @Override
    public TransactionResultDTO eth_getTransactionByBlockHashAndIndex(String blockHash, String index) throws Exception {
        ByteString blockId = ByteString.copyFrom(ByteArray.fromHexString(blockHash));
        Protocol.Block reply = wallet.getBlockById(blockId);
        int idx = Integer.parseInt(getHexNo0x(index), 16);
        return getTransactionFromBlockIdx(reply, idx);
    }

    @Override
    public TransactionResultDTO eth_getTransactionByBlockNumberAndIndex(String bnOrId, String index) throws Exception {
        long blockNum = Long.parseLong(getHexNo0x(bnOrId), 16);
        Protocol.Block reply = wallet.getBlockByNum(blockNum);
        int idx = Integer.parseInt(getHexNo0x(index), 16);
        return getTransactionFromBlockIdx(reply, idx);
    }

    private TransactionResultDTO getTransactionFromBlockIdx(Protocol.Block reply, int idx) {
        List<Protocol.Transaction> tranList = reply.getTransactionsList();
        int tranSize = null != tranList ? tranList.size() : 0;
        TransactionResultDTO transactionResultDTO = new TransactionResultDTO();
        if (null != tranList && tranSize > 0) {
            if (idx < 0 || idx >= tranSize) {
                return null;
            }
            Protocol.Transaction transaction = tranList.get(idx);
            String txID = ByteArray.toHexString(Sha256Hash
                    .hash(CommonParameter.getInstance().isECKeyCryptoEngine(),
                            transaction.getRawData().toByteArray()));
            Protocol.TransactionInfo transactionInfo = wallet.getTransactionInfoById(ByteString.copyFrom(txID.getBytes()));
            transferTransactionInfoToEther(transactionResultDTO, transaction, transactionInfo);

//            BlockCapsule blockCapsule = new BlockCapsule(reply);
//            String blockID = ByteArray.toHexString(blockCapsule.getBlockId().getBytes());
//            transactionResultDTO.blockHash = "0x" + blockID;
        }
        return transactionResultDTO;
    }

    @Override
    public TransactionReceiptDTO eth_getTransactionReceipt(String transactionHash) throws Exception {
        ByteString transactionId = ByteString.copyFrom(hashToByteArray(transactionHash));
        Protocol.TransactionInfo transactionInfo = wallet.getTransactionInfoById(transactionId);
        if (transactionInfo == null){
            return null;
        }

        Block block = wallet.getBlockByNum(transactionInfo.getBlockNumber());
        if (block == null){
            return null;
        }

        return getTransactionReceipt(transactionInfo, block);
    }

    @Override
    public LogFilterElement[] eth_getLogs(FilterRequest fr) throws JsonRpcInvalidParamsException,
            ExecutionException, InterruptedException, BadItemException, ItemNotFoundException,
            JsonRpcMethodNotFoundException, JsonRpcTooManyResultException {
        disableInPBFT("eth_getLogs");

        long currentMaxBlockNum = wallet.getNowBlock().getBlockHeader().getRawData().getNumber();
        //convert FilterRequest to LogFilterWrapper
        LogFilterWrapper logFilterWrapper = new LogFilterWrapper(fr, currentMaxBlockNum, wallet);

        return getLogsByLogFilterWrapper(logFilterWrapper, currentMaxBlockNum);
    }

    private LogFilterElement[] getLogsByLogFilterWrapper(LogFilterWrapper logFilterWrapper,
                                                         long currentMaxBlockNum) throws JsonRpcTooManyResultException, ExecutionException,
            InterruptedException, BadItemException, ItemNotFoundException {
        //query possible block
        LogBlockQuery logBlockQuery = new LogBlockQuery(logFilterWrapper, chainBaseManager
                .getSectionBloomStore(), currentMaxBlockNum, executorService);
        List<Long> possibleBlockList = logBlockQuery.getPossibleBlock();

        //match event from block one by one exactly
        LogMatch logMatch =
                new LogMatch(logFilterWrapper, possibleBlockList, chainBaseManager);
        return logMatch.matchBlockOneByOne();
    }


    public RequestSource getSource() {
        Chainbase.Cursor cursor = wallet.getCursor();
        switch (cursor) {
            case SOLIDITY:
                return RequestSource.SOLIDITY;
            case PBFT:
                return RequestSource.PBFT;
            default:
                return RequestSource.FULLNODE;
        }
    }

    public void disableInPBFT(String method) throws JsonRpcMethodNotFoundException {
        if (getSource() == RequestSource.PBFT) {
            String msg = String.format("the method %s does not exist/is not available in PBFT", method);
            throw new JsonRpcMethodNotFoundException(msg);
        }
    }

    public enum RequestSource {
        FULLNODE,
        SOLIDITY,
        PBFT
    }

    private TransactionReceiptDTO getTransactionReceipt(Protocol.TransactionInfo transactionInfo, Block block){
        BlockCapsule blockCapsule = new BlockCapsule(block);
        String trxId = ByteArray.toHexString(transactionInfo.getId().toByteArray());
        TransactionReceiptDTO transactionReceiptDTO = new TransactionReceiptDTO();
        transactionReceiptDTO.blockHash = ByteArray.toJsonHex(blockCapsule.getBlockId().getBytes());
        transactionReceiptDTO.blockNumber = ByteArray.toJsonHex(blockCapsule.getNum());
        transactionReceiptDTO.transactionHash = ByteArray.toJsonHex(transactionInfo.getId().toByteArray());
        transactionReceiptDTO.logsBloom = ByteArray.toJsonHex(new byte[256]); // no value
        transactionReceiptDTO.root = null;
        transactionReceiptDTO.type = "0x0";

        transactionReceiptDTO.transactionIndex = "0x0";
        transactionReceiptDTO.cumulativeGasUsed = "0x0";
        transactionReceiptDTO.gasUsed = "0x0";
        transactionReceiptDTO.status = "0x0";

        Transaction transaction = null;
        long cumulativeGas = 0;
        long cumulativeLogCount = 0;
        GrpcAPI.TransactionInfoList infoList = wallet.getTransactionInfoByBlockNum(blockCapsule.getNum());
        for (int index = 0; index < infoList.getTransactionInfoCount(); index++) {
            Protocol.TransactionInfo info = infoList.getTransactionInfo(index);
            Protocol.ResourceReceipt resourceReceipt = info.getReceipt();

            long entropyUsage = resourceReceipt.getEntropyUsageTotal();
            cumulativeGas += entropyUsage;

            if (ByteArray.toHexString(info.getId().toByteArray()).equals(trxId)) {
                transactionReceiptDTO.transactionIndex = ByteArray.toJsonHex(index);
                transactionReceiptDTO.cumulativeGasUsed = ByteArray.toJsonHex(cumulativeGas);
                transactionReceiptDTO.gasUsed = ByteArray.toJsonHex(entropyUsage);
                transactionReceiptDTO.status = resourceReceipt.getResultValue() <= 1 ? "0x1" : "0x0";

                transaction = block.getTransactions(index);
                break;
            } else {
                cumulativeLogCount += info.getLogCount();
            }
        }

        transactionReceiptDTO.from = null;
        transactionReceiptDTO.to = null;
        transactionReceiptDTO.contractAddress = null;
        if (transaction != null && !transaction.getRawData().getContractList().isEmpty()) {
            Contract contract = transaction.getRawData().getContract(0);
            byte[] ownerAddress = TransactionCapsule.getOwner(contract);
            byte[] toAddress = getToAddress(transaction);
            transactionReceiptDTO.from = ByteArray.toJsonHexAddress(ownerAddress);
            transactionReceiptDTO.to = ByteArray.toJsonHexAddress(toAddress);

            if (contract.getType() == ContractType.CreateSmartContract) {
                transactionReceiptDTO.contractAddress = ByteArray.toJsonHexAddress(transactionInfo.getContractAddress().toByteArray());
            }
        }

        // logs
        List<LogFilterElement> logList = new ArrayList<>();
        for (int index = 0; index < transactionInfo.getLogCount(); index++) {
            Protocol.TransactionInfo.Log log = transactionInfo.getLogList().get(index);

            LogFilterElement transactionLog = new LogFilterElement();
            // index is the index in the block
            transactionLog.logIndex = ByteArray.toJsonHex(index + cumulativeLogCount);
            transactionLog.transactionHash = transactionReceiptDTO.transactionHash;
            transactionLog.transactionIndex = transactionReceiptDTO.transactionIndex;
            transactionLog.blockHash = transactionReceiptDTO.blockHash;
            transactionLog.blockNumber = transactionReceiptDTO.blockNumber;
            byte[] addressByte = convertToVisionAddress(log.getAddress().toByteArray());
            transactionLog.address = ByteArray.toJsonHexAddress(addressByte);
            transactionLog.data = ByteArray.toJsonHex(log.getData().toByteArray());

            String[] topics = new String[log.getTopicsCount()];
            for (int i = 0; i < log.getTopicsCount(); i++) {
                topics[i] = ByteArray.toJsonHex(log.getTopics(i).toByteArray());
            }
            transactionLog.topics = topics;

            logList.add(transactionLog);
        }

        transactionReceiptDTO.logs = logList.toArray(new LogFilterElement[0]);
        return transactionReceiptDTO;
    }

    private String getAddrNo46(String addr) {
        return StringUtils.isNotBlank(addr) && addr.startsWith("46") ? addr.substring(2) : addr;
        // return addr;
    }

    private String toHexString(byte[] data) {

        return data == null ? "" : Hex.toHexString(data);
    }

    private String getHexNo0x(String data) {
        if (StringUtils.isNotBlank(data) && data.startsWith("0x")) {
            return data.substring(2);
        }
        throw new IllegalArgumentException("not hex String");
    }

    private String getAddressFromEth(String address){
        return StringUtils.isNotEmpty(address) && address.startsWith("0x") ? "46" + address.substring(2) : address;
    }

    private byte[] hashToByteArray(String hash) throws JsonRpcInvalidParamsException {
        if (!Pattern.matches(HASH_REGEX, hash)) {
            throw new JsonRpcInvalidParamsException("invalid hash value");
        }

        byte[] bHash;
        try {
            bHash = ByteArray.fromHexString(hash);
        } catch (Exception e) {
            throw new JsonRpcInvalidParamsException(e.getMessage());
        }
        return bHash;
    }

    private Block getBlockByJsonHash(String blockHash) throws JsonRpcInvalidParamsException {
        byte[] bHash = hashToByteArray(blockHash);
        return wallet.getBlockById(ByteString.copyFrom(bHash));
    }

//    private BlockResult getBlockResult(Block block, boolean fullTx) throws ItemNotFoundException {
//        if (block == null) {
//            return null;
//        }
//        BlockResult blockResult = new BlockResult();
//        transferBlock2Ether(blockResult, block, fullTx);
//
//        return blockResult;
//    }

    public static byte[] getToAddress(Transaction transaction) {
        List<ByteString> toAddressList = getTo(transaction);
        if (!toAddressList.isEmpty()) {
            return toAddressList.get(0).toByteArray();
        } else {
            return null;
        }
    }

    public static List<ByteString> getTo(Transaction transaction) {
        Transaction.Contract contract = transaction.getRawData().getContract(0);
        List<ByteString> list = new ArrayList<>();
        try {
            Any contractParameter = contract.getParameter();
            switch (contract.getType()) {
                case AccountCreateContract:
                    list.add(contractParameter.unpack(AccountCreateContract.class).getAccountAddress());
                    break;
                case TransferContract:
                    list.add(contractParameter.unpack(TransferContract.class).getToAddress());
                    break;
                case TransferAssetContract:
                    list.add(contractParameter.unpack(TransferAssetContract.class).getToAddress());
                    break;
                case VoteAssetContract:
                    list.addAll(contractParameter.unpack(VoteAssetContract.class).getVoteAddressList());
                    break;
                case VoteWitnessContract:
                    for (Vote vote : contractParameter.unpack(VoteWitnessContract.class).getVotesList()) {
                        list.add(vote.getVoteAddress());
                    }
                    break;
                case ParticipateAssetIssueContract:
                    list.add(contractParameter.unpack(ParticipateAssetIssueContract.class).getToAddress());
                    break;
                case FreezeBalanceContract:
                    FreezeBalanceContract freezeBalanceContract = contractParameter.unpack(FreezeBalanceContract.class);
                    ByteString receiverAddress;
                    if (freezeBalanceContract.getResource() == Common.ResourceCode.SPREAD){
                        receiverAddress = freezeBalanceContract.getParentAddress();
                    }else {
                        receiverAddress = contractParameter.unpack(FreezeBalanceContract.class)
                                .getReceiverAddress();
                    }
                    if (!receiverAddress.isEmpty()) {
                        list.add(receiverAddress);
                    }
                    break;
                case UnfreezeBalanceContract:
                    receiverAddress = contractParameter.unpack(UnfreezeBalanceContract.class)
                            .getReceiverAddress();
                    if (!receiverAddress.isEmpty()) {
                        list.add(receiverAddress);
                    }
                    break;
                case TriggerSmartContract:
                    list.add(contractParameter.unpack(TriggerSmartContract.class).getContractAddress());
                    break;
                case UpdateSettingContract:
                    list.add(contractParameter.unpack(UpdateSettingContract.class).getContractAddress());
                    break;
                case UpdateEntropyLimitContract:
                    list.add(contractParameter.unpack(UpdateEntropyLimitContract.class).getContractAddress());
                    break;
                case ClearABIContract:
                    list.add(contractParameter.unpack(ClearABIContract.class).getContractAddress());
                    break;
                case ShieldedTransferContract:
                    ShieldedTransferContract shieldedTransferContract = contract.getParameter()
                            .unpack(ShieldedTransferContract.class);
                    if (!shieldedTransferContract.getTransparentToAddress().isEmpty()) {
                        list.add(shieldedTransferContract.getTransparentToAddress());
                    }
                    break;
                default:
                    break;
            }
            return list;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return list;
    }
}

