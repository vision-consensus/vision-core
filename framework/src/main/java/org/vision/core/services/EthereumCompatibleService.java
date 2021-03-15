package org.vision.core.services;

import org.springframework.stereotype.Component;
import org.vision.common.application.EthereumCompatible;

@Component
public class EthereumCompatibleService implements EthereumCompatible {
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
        return null;
    }

    @Override
    public String eth_getBalance(String address, String block) throws Exception {
        return "0x10";
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

    @Override
    public BlockResult eth_getBlockByNumber(String bnOrId, Boolean fullTransactionObjects) throws Exception {
        return null;
    }
}
