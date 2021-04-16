package org.vision.core.services;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.api.GrpcAPI;
import org.vision.common.application.EthereumCompatible;
import org.vision.common.crypto.ECKey;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Sha256Hash;
import org.vision.core.Constant;
import org.vision.core.Wallet;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.core.capsule.TransactionInfoCapsule;
import org.vision.core.exception.ContractValidateException;
import org.vision.protos.Protocol;
import org.vision.protos.contract.SmartContractOuterClass;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class EthereumCompatibleService implements EthereumCompatible {

    @Autowired
    private Wallet wallet;

    @Override
    public String eth_chainId() {
        return "0x42";
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
        return null;
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
        ECKey.ECDSASignature ecdsaSignature = ethTrx.getSignature();
        GrpcAPI.TransactionExtention.Builder trxExtBuilder = GrpcAPI.TransactionExtention.newBuilder();
        GrpcAPI.Return.Builder retBuilder = GrpcAPI.Return.newBuilder();
        Protocol.Transaction trx = null;
        TransactionCapsule transactionCapsule = null;
        try {
            byte[] receiveAddress = ByteArray.fromHexString(Constant.ADD_PRE_FIX_STRING_MAINNET + ByteArray.toHexString(ethTrx.getReceiveAddress()));
            int accountType = wallet.getAccountType(receiveAddress);
            if (1 == accountType) {
                //todo 计算
                long feeLimit = 210000000;
                TransactionCapsule trxCap = wallet
                        .createTransactionCapsule(ethTrx.rlpParseToTriggerSmartContract(), Protocol.Transaction.Contract.ContractType.TriggerSmartContract);
                Protocol.Transaction.Builder txBuilder = trxCap.getInstance().toBuilder();
                Protocol.Transaction.raw.Builder rawBuilder = trxCap.getInstance().getRawData().toBuilder();
                rawBuilder.setFeeLimit(feeLimit);
                txBuilder.setRawData(rawBuilder);
                txBuilder.setSignature(0, ByteString.copyFrom(ethTrx.getEncodedRaw()));
                trx = wallet.triggerContract(ethTrx.rlpParseToTriggerSmartContract(), new TransactionCapsule(txBuilder.build()), trxExtBuilder,
                        retBuilder);
            } else if (2 == accountType) {
                trx = wallet.createTransactionCapsule(ethTrx.rlpParseToTransferContract(), Protocol.Transaction.Contract.ContractType.TransferContract)
                        .getInstance();
            } else {
                return null;
            }
            GrpcAPI.Return result = wallet.broadcastTransaction(trx);
            System.out.println(result.toString());
//            transactionCapsule = new TransactionCapsule(trx);
//            if (GrpcAPI.Return.response_code.SUCCESS != result.getCode()) {
//                logger.error("Broadcast transaction {} has failed, {}.", transactionCapsule.getTransactionId(), result.getMessage().toStringUtf8());
//                return null;
//            }
        } catch (Exception e) {
            String errString = null;
            if (e.getMessage() != null) {
                errString = e.getMessage().replaceAll("[\"]", "\'");
            }
            return errString;
        }
        //return Constant.ETH_PRE_FIX_STRING_MAINNET + ByteArray.toHexString(transactionCapsule.getTransactionId().getBytes());
        return Constant.ETH_PRE_FIX_STRING_MAINNET + ByteArray.toHexString(ethTrx.getHash());
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
        return ByteArray.toHexString(trxExtBuilder.build().getConstantResult(0).toByteArray());
    }

    @Override
    public String eth_estimateGas(CallArguments args) throws Exception {
        return null;
    }

    @Override
    public BlockResult eth_getBlockByHash(String blockHash, Boolean fullTransactionObjects) throws Exception {
        return null;
    }

    private final AtomicLong counter = new AtomicLong();

    @Override
    public BlockResult eth_getBlockByNumber(String bnOrId, Boolean fullTransactionObjects) throws Exception {
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
    public TransactionResultDTO eth_getTransactionByHash(String transactionHash) throws Exception {
        return null;
    }

    @Override
    public TransactionResultDTO eth_getTransactionByBlockHashAndIndex(String blockHash, String index) throws Exception {
        return null;
    }

    @Override
    public TransactionResultDTO eth_getTransactionByBlockNumberAndIndex(String bnOrId, String index) throws Exception {
        return null;
    }

    @Override
    public TransactionReceiptDTO eth_getTransactionReceipt(String transactionHash) throws Exception {
//        Protocol.Transaction transaction = wallet.getTransactionById(ByteString.copyFrom(ByteArray.fromHexString(transactionHash.substring(2, transactionHash.length()))));
//        TransactionCapsule transactionCapsule = new TransactionCapsule(transaction);
//        Protocol.TransactionInfo transactionInfo = wallet.getTransactionInfoById(ByteString.copyFrom(ByteArray.fromHexString(transactionHash.substring(2, transactionHash.length()))));
//        Protocol.Block block = wallet.getBlockById(ByteString.copyFrom(ByteArray.fromHexString(new Long(transactionCapsule.getBlockNum()).toString())));
//        TransactionReceiptDTO transactionReceiptDTO = new TransactionReceiptDTO(block, transactionInfo);
        TransactionReceiptDTO transactionReceiptDTO = new TransactionReceiptDTO();
        transactionReceiptDTO.blockNumber = "0x1111";
        return transactionReceiptDTO;
    }
}
