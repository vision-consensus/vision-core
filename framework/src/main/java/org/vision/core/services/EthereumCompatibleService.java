package org.vision.core.services;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.api.GrpcAPI;
import org.vision.common.application.EthereumCompatible;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.Bloom;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Sha256Hash;
import org.vision.core.ChainBaseManager;
import org.vision.core.Constant;
import org.vision.core.Wallet;
import org.vision.core.actuator.TransactionFactory;
import org.vision.core.capsule.BlockCapsule;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.core.config.args.Args;
import org.vision.core.db.BlockIndexStore;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.exception.ItemNotFoundException;
import org.vision.core.services.http.JsonFormat;
import org.vision.core.services.http.Util;
import org.vision.protos.Protocol;
import org.vision.protos.contract.SmartContractOuterClass;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;


@Slf4j
@Component
public class EthereumCompatibleService implements EthereumCompatible {
    private static final int LOGS_LIMIT_RET = 1000;
    @Autowired
    private Wallet wallet;

    @Autowired
    private ChainBaseManager chainBaseManager;

    @Override
    public String eth_chainId() {
        CommonParameter parameter = Args.getInstance();
        return "0x" + Integer.toHexString(parameter.nodeP2pVersion);
        // return "0x42";
    }

    @Override
    public Object[] eth_getLogs(FilterRequest filterRequest) throws Exception {
        // deal parameters
        String fromBlock = getHexNo0x(filterRequest.fromBlock);
        String toBlock = getHexNo0x(filterRequest.toBlock);
        String blockHash = getHexNo0x(filterRequest.blockHash);
        long fromBlockNumber;
        long toBlockNumber;
        if (StringUtils.isNotBlank(blockHash)) {
            blockHash = getHexNo0x(blockHash);
            ByteString blockId = ByteString.copyFrom(ByteArray.fromHexString(blockHash));
            Protocol.Block reply = wallet.getBlockById(blockId);
            if (null == reply) {
                throw new RuntimeException("cannot find block by blockHash");
            }
            fromBlockNumber = toBlockNumber = reply.getBlockHeader().getRawData().getNumber();
        } else {
            fromBlockNumber = parseBlockNum(fromBlock);
            toBlockNumber = parseBlockNum(toBlock);
        }

        // get Bloom by filterRequest
        Bloom filterBloom = new Bloom();

        String contractAddress = getHexNo0x((String) filterRequest.address);
        if (StringUtils.isNotBlank(contractAddress)) {
            filterBloom.add(hexTobytes(contractAddress));
        }

        Object[] topics =  filterRequest.topics;
        List<String> filterTopicList = new ArrayList<>();
        int topicSize = null != topics ? topics.length : 0;
        if (topicSize > 0 && topicSize < 10) {
            for (Object topic : topics) {
                String topicStr = getHexNo0x(topic.toString());
                filterBloom.add(hexTobytes(topicStr));
                filterTopicList.add(topicStr);
            }
        } else {
            throw new RuntimeException("the topic size must >0 and < 10");
        }
        int newLogNum = 0;
        JSONArray logsArray = new JSONArray();
        for (long num = fromBlockNumber; num <= toBlockNumber; num++) {
            // find block by num
            Protocol.Block block = wallet.getBlockByNum(num);
            // find by logsBloom
            if (null == block) {
                break;
            }
            // find logs
            ByteString logsBloom = block.getBlockHeader().getRawData().getLogsBloom();
            Bloom blockLogsBloom = new Bloom(logsBloom.toByteArray());
            boolean isMatch = blockLogsBloom.matches(filterBloom);
            if (!isMatch) {
                continue;
            }
            // get transactions from block
            GrpcAPI.TransactionInfoList transactionInfoList = wallet.getTransactionInfoByBlockNum(num);
            List<Protocol.TransactionInfo> transactionInfos = transactionInfoList.getTransactionInfoList();
            if (CollectionUtils.isNotEmpty(transactionInfos)) {
                int tranSize = transactionInfos.size();
                for (int transactionIndex = 0; transactionIndex < tranSize; transactionIndex++) {
                    Protocol.TransactionInfo transactionInfo = transactionInfos.get(transactionIndex);

                    ByteString logsBloomInfo = transactionInfo.getLogsBloom();
                    Bloom infoLogsBloom = new Bloom(logsBloomInfo.toByteArray());
                    boolean isMatchInfo = infoLogsBloom.matches(filterBloom);
                    if (!isMatchInfo) {
                        continue;
                    }

                    List<Protocol.TransactionInfo.Log> logs = transactionInfo.getLogList();
                    if (CollectionUtils.isNotEmpty(logs)) {
                        for (Protocol.TransactionInfo.Log log : logs) {
                            if (StringUtils.isNotBlank(contractAddress) && !toHexString(log.getAddress().toByteArray()).equals(contractAddress)) {
                                continue;
                            }
                            List<ByteString> logTopicInBlock = log.getTopicsList();
                            boolean flag = false;
                            if (CollectionUtils.isNotEmpty(logTopicInBlock)) {
                                for (ByteString bs : logTopicInBlock) {
                                    if (filterTopicList.contains(toHexString(bs.toByteArray()))) {
                                        flag = true;
                                        break;
                                    }
                                }
                            }
                            newLogNum++;
                            checkLogsLimit(newLogNum);
                            if (flag) {
                                JSONObject jsonObject = new JSONObject();
                                jsonObject.put("address", "0x" + Hex.toHexString(log.getAddress().toByteArray()));
                                List<String> topicList = new ArrayList<>();
                                for (ByteString bs : logTopicInBlock) {
                                    topicList.add(toHexString(bs.toByteArray()));
                                }
                                jsonObject.put("topics", topicList.toArray());
                                jsonObject.put("blockNum", "0x" + Long.toHexString(num));
                                jsonObject.put("transactionHash", "0x" + Hex.toHexString(transactionInfo.getId().toByteArray()));
                                jsonObject.put("transactionIndex", "0x" + Integer.toHexString(transactionIndex));
                                logsArray.add(jsonObject);
                            }

                        }
                    }
                }
            }
        }
        return logsArray.toArray();
    }


    @Override
    public String web3_clientVersion() {
        return null;
    }

    @Override
    public String web3_sha3(String data) throws Exception {
        return null;
    }

    @Override
    public String net_version() {
        return "1.0.1";
    }

    @Override
    public String net_peerCount() {
        return null;
    }

    @Override
    public boolean net_listening() {
        return false;
    }

    @Override
    public String eth_protocolVersion() {
        return null;
    }

    @Override
    public Object eth_syncing() {
        return null;
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
        return "0x" + Long.toHexString(8);
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
    public String eth_getStorageAt(String address, String storageIdx, String blockId) throws Exception {
        return null;
    }

    @Override
    public String eth_getTransactionCount(String address, String blockId) throws Exception {
        Protocol.Block reply = wallet.getNowBlock();
        return Constant.ETH_PRE_FIX_STRING_MAINNET + Long.toHexString(reply.getBlockHeader().getRawData().getNumber()).toLowerCase();
    }

    @Override
    public String eth_getBlockTransactionCountByHash(String blockHash) throws Exception {
        return null;
    }

    @Override
    public String eth_getBlockTransactionCountByNumber(String bnOrId) throws Exception {
        return null;
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
        return null;
    }

    @Override
    public String eth_sign(String addr, String data) throws Exception {
        return null;
    }

    @Override
    public String eth_sendTransaction(CallArguments transactionArgs) throws Exception {
        return null;
    }

    public static void main(String[] args) {
        TransactionCapsule.EthTrx ethTrx = new TransactionCapsule.EthTrx(ByteArray.fromHexString("0xf8a802847735940082d0c8945442b1eb46e8844c52d9d95774f4779f64fc055580b844a9059cbb0000000000000000000000001f45212b81773a02f67545e510cba43e3c5cc5e6000000000000000000000000000000000000000000000000000000003b9aca0029a06a9d8223e794458529a643f0a74575920e7e12caf7a1533eddc1eb353b776a19a02317c972f03cbb4d5c7a1bfa12d59260580a9e4accf43843759a2f8462610d8b"));
        ethTrx.rlpParse();
        System.out.println(ByteArray.toHexString(ethTrx.getSender()));//0x28E2Fc69A14Abe85AAC11778FA0637FE659664b8
        System.out.println(ByteArray.toHexString(ethTrx.getReceiveAddress()));//
    }

    @Override
    public String eth_sendRawTransaction(String rawData) throws Exception {
        TransactionCapsule.EthTrx ethTrx = new TransactionCapsule.EthTrx(ByteArray.fromHexString(rawData));
        ethTrx.rlpParse();
        GrpcAPI.TransactionExtention.Builder trxExtBuilder = GrpcAPI.TransactionExtention.newBuilder();
        GrpcAPI.Return.Builder retBuilder = GrpcAPI.Return.newBuilder();
        Protocol.Transaction trx = null;
        TransactionCapsule transactionCapsule = null;
        try {
            byte[] receiveAddressStr = ethTrx.getReceiveAddress();
            boolean isDeployContract = (null == receiveAddressStr || receiveAddressStr.length == 0);
            if (isDeployContract) {
                String data = ByteArray.toHexString(ethTrx.getData());
                if (StringUtils.isBlank(data)) {
                    throw new IllegalArgumentException("no data!");
                }
            }
            byte[] receiveAddress = ByteArray.fromHexString(Constant.ADD_PRE_FIX_STRING_MAINNET + ByteArray.toHexString(ethTrx.getReceiveAddress()));
            int accountType = wallet.getAccountType(receiveAddress);
            logger.info("accountType={}", accountType);
            if (1 == accountType || isDeployContract) { //
                // long feeLimit = 210000000;
                // feeLimit unit is vdt for vision(1VS = 1,000,000VDT)
                long gasPrice = Long.parseLong(
                        toHexString(ethTrx.getGasPrice()), 16
                );
                long gasLimit = Long.parseLong(toHexString(ethTrx.getGasLimit()), 16);
                logger.info("gasPrice={},gasLimit={}", gasPrice, gasLimit);
                long feeLimit = gasPrice * gasLimit * 2;
                Message message = null;
                Protocol.Transaction.Contract.ContractType contractType = null;
                if (isDeployContract) {
                    message = ethTrx.rlpParseToDeployContract();
                    contractType = Protocol.Transaction.Contract.ContractType.CreateSmartContract;
                } else {
                    message = ethTrx.rlpParseToTriggerSmartContract();
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
                    trx = wallet.triggerContract(ethTrx.rlpParseToTriggerSmartContract(), new TransactionCapsule(txBuilder.build()), trxExtBuilder,
                            retBuilder);
                }

            } else {
                TransactionCapsule transactionCapsule1 = wallet.createTransactionCapsule(ethTrx.rlpParseToTransferContract(), Protocol.Transaction.Contract.ContractType.TransferContract);
                trx = transactionCapsule1.getInstance().toBuilder().addSignature(ByteString.copyFrom(ethTrx.getSignature().toByteArray())).build();
            }

            GrpcAPI.Return result = wallet.broadcastTransaction(trx);
            transactionCapsule = new TransactionCapsule(trx);
            if (GrpcAPI.Return.response_code.SUCCESS != result.getCode()) {
                logger.error("Broadcast transaction {} has failed, {}.", transactionCapsule.getTransactionId(), result.getMessage().toStringUtf8());
                String errMsg = result.getMessage().toString();
                return "broadcast trx failed:" + errMsg;
            }
        } catch (Exception e) {
            logger.error("sendRawTransaction error", e);
            String errString = null;
            if (e.getMessage() != null) {
                errString = e.getMessage().replaceAll("[\"]", "\'");
            }
            return errString;
        }
        String trxHash = Constant.ETH_PRE_FIX_STRING_MAINNET + ByteArray.toHexString(transactionCapsule.getTransactionId().getBytes());
        logger.info("trxHash={}", trxHash);
        return trxHash;
    }

    @Override
    public String eth_call(CallArguments args, String bnOrId) throws Exception {
        SmartContractOuterClass.TriggerSmartContract.Builder build = SmartContractOuterClass.TriggerSmartContract.newBuilder();
        GrpcAPI.TransactionExtention.Builder trxExtBuilder = GrpcAPI.TransactionExtention.newBuilder();
        GrpcAPI.Return.Builder retBuilder = GrpcAPI.Return.newBuilder();
        try {
            build.setData(ByteString.copyFrom(ByteArray.fromHexString(args.data)));
            build.setContractAddress(ByteString.copyFrom(ByteArray.fromHexString(args.to.replace(Constant.ETH_PRE_FIX_STRING_MAINNET, Constant.ADD_PRE_FIX_STRING_MAINNET).toLowerCase())));
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
        return "0x" + ByteArray.toHexString(trxExtBuilder.build().getConstantResult(0).toByteArray());
    }

    @Override
    public String eth_estimateGas(CallArguments args) throws Exception {
        return null;
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
        Protocol.BlockHeader visionBlockHeader = reply.getBlockHeader();

        Protocol.BlockHeader blockHeader = reply.getBlockHeader();
        Protocol.BlockHeader.raw rawData = blockHeader.getRawData();

        // blockResult.difficulty = "0x2";
        blockResult.difficulty = "0x20000";
        //blockResult.extraData = "0xd883010003846765746888676f312e31332e34856c696e75780000000000000049116d665d92e4581c19cd8a67a316739ec2faa4d3e8d3fc518ad6c9e02dc51154bcd4ffbf3156d9d8265500c6bc775ff05b5a54650397fdd057f1d9cb98f6a501";
        blockResult.extraData = "0xd5830105048650617269747986312e31352e31826c69";
        blockResult.gasLimit = "0x1ca35b8";
        blockResult.gasUsed = "0x1ca0e7f";
        BlockIndexStore blockIndexStore = chainBaseManager.getBlockIndexStore();
        BlockCapsule.BlockId blockId = blockIndexStore.get(blockHeader.getRawData().getNumber());
        blockResult.hash = "0x" + toHexString(blockId.getByteString().toByteArray());
        // blockResult.hash = blockHeader.getWitnessSignature();
        blockResult.logsBloom = "0x00000000000000000200800000000000000000000000000000000000000000000000004000004000000000000000000000000000001008000000000000000000000000000000000000000020000000000000000000000000000008000000000000000080000000000000000000000000000002000000000000000020000000100000000000000000000000000000200000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000020000000000004000000000000000000001000000000000000000000000000000000000000000000000001000000000000000000000000000000000000";
        blockResult.miner = "0x0ed0d1f46391e08f0ceab98fc3700e5d9c1c7b19";
        blockResult.mixHash = "0x0000000000000000000000000000000000000000000000000000000000000000";
        blockResult.nonce = "0x0000000000000000";
        //blockResult.number = "0x2d6c2e";
        blockResult.number = "0x" + Long.toHexString(rawData.getNumber()); // block height
        blockResult.parentHash = "0x" + toHexString(rawData.getParentHash().toByteArray()); // father block hash
        blockResult.receiptsRoot = "0x5f695898d27a2e25c2ae05c436be37a38bc3f8993386bf409f0ce40d6292298c";
        blockResult.sha3Uncles = "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347";
        //blockResult.stateRoot = "0x" + toHexString(rawData.getAccountStateRoot().toByteArray());
        blockResult.stateRoot = "0x8740447201cb72ea26aa8a0e5b846d334163355ad3c892a151786f2c3115a131";
        blockResult.timestamp = "0x" + Long.toHexString(rawData.getTimestamp());
        blockResult.totalDifficulty = "0x5abd10";
        List<Protocol.Transaction> transactionList = reply.getTransactionsList();
        List<String> transHashList = new ArrayList<>();
        List<TransactionResultDTO> tranFullList = new ArrayList<>();
        if (null != transactionList && transactionList.size() > 0) {
            long transactionIdx = 0;
            for (Protocol.Transaction trx : transactionList) {
                // eth_getBlockByHash actually get block by txId for vision-core
                String txID = ByteArray.toHexString(Sha256Hash
                        .hash(CommonParameter.getInstance().isECKeyCryptoEngine(),
                                trx.getRawData().toByteArray()));
                String hash = "0x" + txID;
                transHashList.add(hash);

                TransactionResultDTO tranDTO = new TransactionResultDTO();
                transferTransaction2Ether(tranDTO, trx);
                tranDTO.blockHash = blockResult.hash;
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
        // blockResult.uncles = new String[]{"0x8740447201cb72ea26aa8a0e5b846d334163355ad3c892a151786f2c3115a131"};
        blockResult.uncles = new String[]{};
    }


    @Override
    public TransactionResultDTO eth_getTransactionByHash(String transactionHash) throws Exception {
        TransactionResultDTO transactionResultDTO = new TransactionResultDTO();
        ByteString transactionId = ByteString.copyFrom(ByteArray.fromHexString(transactionHash.substring(2, transactionHash.length())));
        Protocol.Transaction transaction = wallet.getTransactionById(transactionId);
        transferTransaction2Ether(transactionResultDTO, transaction);

        return transactionResultDTO;
    }

    private void transferTransaction2Ether(TransactionResultDTO transactionResultDTO,
                                           Protocol.Transaction transaction) {
        Protocol.Transaction.raw rawData = transaction.getRawData();
        // Protocol.Transaction.Contract contract = rawData.getContract(0);
        transactionResultDTO.blockHash = "0x" + toHexString(rawData.getRefBlockHash().toByteArray());
        transactionResultDTO.blockNumber = "0x" + Long.toHexString(transaction.getRawData().getRefBlockNum());
        transactionResultDTO.chainId = eth_chainId();
        transactionResultDTO.condition = null;
        transactionResultDTO.creates = null;
        // compatible remix
        transactionResultDTO.gasPrice = eth_gasPrice();
        boolean selfType = false;
        transaction.getRawData().getContractList().stream().forEach(contract -> {
            try {
                JSONObject contractJson = null;
                Any contractParameter = contract.getParameter();
                switch (contract.getType()) {
                    case CreateSmartContract:
                        SmartContractOuterClass.CreateSmartContract deployContract = contractParameter
                                .unpack(SmartContractOuterClass.CreateSmartContract.class);
                        contractJson = JSONObject
                                .parseObject(JsonFormat.printToString(deployContract, selfType));
                        byte[] ownerAddress = deployContract.getOwnerAddress().toByteArray();
                        byte[] contractAddress = Util.generateContractAddress(transaction, ownerAddress);
                        // jsonTransaction.put("contract_address", ByteArray.toHexString(contractAddress));
                        transactionResultDTO.from = "0x" + getAddrNo46(toHexString(ownerAddress));
                        transactionResultDTO.to = "0x" + getAddrNo46(toHexString(contractAddress));
                        break;
                    default:
                        Class clazz = TransactionFactory.getContract(contract.getType());
                        if (clazz != null) {
                            contractJson = JSONObject
                                    .parseObject(JsonFormat.printToString(contractParameter.unpack(clazz), selfType));
                        }
                        transactionResultDTO.from = "0x" + getAddrNo46(contractJson.getString("owner_address"));
                        transactionResultDTO.to = "0x" + getAddrNo46(contractJson.getString("account_address"));
                        break;
                }

            } catch (InvalidProtocolBufferException e) {
                logger.debug("InvalidProtocolBufferException: {}", e.getMessage());
            }
        });
        // transactionResultDTO.gas = "0x1011f";
        // transactionResultDTO.gasPrice = "0x6fc23ac00";
        String txID = ByteArray.toHexString(Sha256Hash
                .hash(CommonParameter.getInstance().isECKeyCryptoEngine(),
                        transaction.getRawData().toByteArray()));
        transactionResultDTO.hash = "0x" + txID;
        transactionResultDTO.input = "0x1fe927cf0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000046a0382046607ff830175c180835b8d80941fc313a8e56c86855e85c6fd40162ae990c3e8ff80418d78d40000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000022000000000000000000000000000000000000000000000000000000000000002c000000000000000000000000000000000000000000000000000000000000003600000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000014000000000000000000000000000000000000000000000000000000000000000034d4b5200000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000005544845544100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000035a525800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000003555050000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000050d05e35be0000000000000000000000000000000000000000000000000000000027961ff0000000000000000000000000000000000000000000000000000000000671ae9a8000000000000000000000000000000000000000000000000000000000983b4c80000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000006091571d000000000000000000000000000000000000000000000000000000006091570e000000000000000000000000000000000000000000000000000000006091570e00000000000000000000000000000000000000000000000000000000609157180000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000046661c000000000000000000000000000000000000000000000000000000000046660e000000000000000000000000000000000000000000000000000000000046660e000000000000000000000000000000000000000000000000000000000046661643e176ddde646f8e94c878a74472d9e9af1d8eb9e58cd647844fd3cd9eb53a4b0a1e245b5e3307519589c715ec4229f90361e8ed3a0599c629da78ebf73404280000000000000000000000000000000000000000000000";
        transactionResultDTO.nonce = "0x152a0";
        transactionResultDTO.publicKey = "0xbd48aced94ccdfb27f22682ae3308234389280d5560741aa6c62dcbb93fdf6ff72f853c86ea54e721f369229eacf64d3550c35ce219bf3e8c40be053694972be";
        transactionResultDTO.r = "0x489ee11e816e3f05318e954cc721d20a8297d0294f6512ee2384f034e0dbf895";
        String rawDataHex = ByteArray.toHexString(transaction.getRawData().toByteArray());
        transactionResultDTO.raw = "0x" + rawDataHex;
        transactionResultDTO.s = "0x265ea4adf5bdb248baa6bbd2224ad8c2e733bd79e78137bc07723cf93e592a94";
        transactionResultDTO.standardV = "0x1";
        transactionResultDTO.transactionIndex = "0x0";
        transactionResultDTO.v = "0x78";
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
            transferTransaction2Ether(transactionResultDTO, transaction);

            BlockCapsule blockCapsule = new BlockCapsule(reply);
            String blockID = ByteArray.toHexString(blockCapsule.getBlockId().getBytes());
            transactionResultDTO.blockHash = "0x" + blockID;
        }
        return transactionResultDTO;
    }

    @Override
    public TransactionReceiptDTO eth_getTransactionReceipt(String transactionHash) throws Exception {
        TransactionReceiptDTO transactionReceiptDTO = new TransactionReceiptDTO();

        ByteString transactionId = ByteString.copyFrom(ByteArray.fromHexString(transactionHash.substring(2, transactionHash.length())));
        Protocol.Transaction transaction = wallet.getTransactionById(transactionId);
        if (null != transaction) {
            int retCount = transaction.getRetCount();
            logger.info("retCount={}", retCount);
            if (retCount > 0) {
                Protocol.Transaction.Result.contractResult result = transaction.getRet(0).getContractRet();
                if (result == Protocol.Transaction.Result.contractResult.DEFAULT) {
                    return null;
                } else if (result == Protocol.Transaction.Result.contractResult.SUCCESS) {
                    setTransactionReceipt(transactionReceiptDTO, transactionId);
                } else {
                    transactionReceiptDTO.status = "0x0";
                    transactionReceiptDTO.root = null;
                }

            } else {
                return null;
            }


            Protocol.Transaction.raw rawData = transaction.getRawData();
            transactionReceiptDTO.cumulativeGasUsed = "0x41145";
            transactionReceiptDTO.gasUsed = "0x5208";
            transactionReceiptDTO.logs = null;
            transactionReceiptDTO.logsBloom = "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
            transactionReceiptDTO.transactionHash = transactionHash;

            boolean selfType = false;
            rawData.getContractList().stream().forEach(contract -> {
                try {
                    JSONObject contractJson = null;
                    Any contractParameter = contract.getParameter();
                    switch (contract.getType()) {
                        case CreateSmartContract:
                            SmartContractOuterClass.CreateSmartContract deployContract = contractParameter
                                    .unpack(SmartContractOuterClass.CreateSmartContract.class);
                            contractJson = JSONObject
                                    .parseObject(JsonFormat.printToString(deployContract, selfType));
                            byte[] ownerAddress = deployContract.getOwnerAddress().toByteArray();
                            byte[] contractAddress = Util.generateContractAddress(transaction, ownerAddress);
                            transactionReceiptDTO.from = "0x" + getAddrNo46(ByteArray.toHexString(ownerAddress));
                            transactionReceiptDTO.contractAddress =  "0x" + getAddrNo46(ByteArray.toHexString(contractAddress));
                            break;
                        default:
                            Class clazz = TransactionFactory.getContract(contract.getType());
                            if (clazz != null) {
                                contractJson = JSONObject
                                        .parseObject(JsonFormat.printToString(contractParameter.unpack(clazz), selfType));
                            }
                            transactionReceiptDTO.from = "0x" + getAddrNo46(contractJson.getString("owner_address"));
                            transactionReceiptDTO.to = "0x" + getAddrNo46(contractJson.getString("account_address"));
                            break;
                    }

                } catch (InvalidProtocolBufferException e) {
                    logger.debug("InvalidProtocolBufferException: {}", e.getMessage());
                }
            });
        }

        return transactionReceiptDTO;
    }

    private void setTransactionReceipt(TransactionReceiptDTO transactionReceiptDTO, ByteString transactionId) throws ItemNotFoundException {
        Protocol.TransactionInfo transactionInfo = wallet.getTransactionInfoById(transactionId);

        if (null != transactionInfo) {
            transactionReceiptDTO.blockNumber = Constant.ETH_PRE_FIX_STRING_MAINNET + Long.toHexString(transactionInfo.getBlockNumber()).toLowerCase();
            transactionReceiptDTO.status = "0x1";
            // transactionReceiptDTO.root = "0x2b5ca80d30787fcc6c1c5bc221f3f0a08fd676480c5803b5c472a83acfcb0345";
            transactionReceiptDTO.gasUsed = "0x" + Long.toHexString(transactionInfo.getFee());

            transactionReceiptDTO.transactionIndex = "0x2";

            Protocol.Block block = wallet.getBlockByNum(transactionInfo.getBlockNumber());
            Protocol.BlockHeader blockHeader = block.getBlockHeader();
            Protocol.BlockHeader.raw rawData = blockHeader.getRawData();
            BlockIndexStore blockIndexStore = chainBaseManager.getBlockIndexStore();
            BlockCapsule.BlockId blockId = blockIndexStore.get(blockHeader.getRawData().getNumber());
            transactionReceiptDTO.blockHash = "0x" + toHexString(blockId.getByteString().toByteArray());
            transactionReceiptDTO.root = toHexString(rawData.getTxTrieRoot().toByteArray());
        } else {
            transactionReceiptDTO.status = "0x0";
            transactionReceiptDTO.root = null;
        }
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
        return data;
        // throw new IllegalArgumentException("not hex String");
    }

    private byte[] hexTobytes(String hex) {
        if (hex.length() < 1) {
            return null;
        } else {
            byte[] result = new byte[hex.length() / 2];
            int j = 0;
            for(int i = 0; i < hex.length(); i+=2) {
                result[j++] = (byte)Integer.parseInt(hex.substring(i,i+2), 16);
            }
            return result;
        }
    }

    private boolean checkLogsLimit(int nums) {
        if (nums > LOGS_LIMIT_RET) {
            throw new RuntimeException("query returned more than "+ LOGS_LIMIT_RET +" results");
        }
        return true;
    }

    private long parseBlockNum(String blockNum) {
        if ("latest".equals(blockNum) || "pending".equals(blockNum) || "earliest".equals(blockNum)) {
            return wallet.getNowBlock().getBlockHeader().getRawData().getNumber();
        } else {
            return Long.parseLong(blockNum);
        }
    }
}
