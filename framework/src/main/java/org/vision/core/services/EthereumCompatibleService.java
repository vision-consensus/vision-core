package org.vision.core.services;

import com.google.protobuf.ByteString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.common.application.EthereumCompatible;
import org.vision.common.utils.ByteArray;
import org.vision.core.Wallet;
import org.vision.protos.Protocol;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

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
        return "0x" + Long.toHexString(reply.getBlockHeader().getRawData().getNumber()).toLowerCase();
    }

    @Override
    public String eth_getBalance(String address, String block) throws Exception {
        Protocol.Account.Builder build = Protocol.Account.newBuilder();
        build.setAddress(ByteString.copyFrom(ByteArray.fromHexString(address.replace("0x", "46").toLowerCase())));
        Protocol.Account reply = wallet.getAccount(build.build());
        return "0x" + new BigInteger(reply.getBalance() + "").multiply(new BigInteger("1000000000000")).toString(16);
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
        return null;
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

    @Override
    public String eth_sendRawTransaction(String rawData) throws Exception {
        return null;
    }

    @Override
    public String eth_call(CallArguments args, String bnOrId) throws Exception {
        return null;
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
}
