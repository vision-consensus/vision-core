package org.vision.common.application;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.Value;

import java.util.Arrays;

public interface EthereumCompatible {
    @Value
    @AllArgsConstructor
    @ToString
    class SyncingResult {
        private final String startingBlock;
        private final String currentBlock;
        private final String highestBlock;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    class CallArguments {
        public String from;
        public String to;
        public String gas;
        public String gasPrice;
        public String value;
        public String data; // compiledCode
        public String nonce;

        @Override
        public String toString() {
            return "CallArguments{" +
                    "from='" + from + '\'' +
                    ", to='" + to + '\'' +
                    ", gas='" + gas + '\'' +
                    ", gasPrice='" + gasPrice + '\'' +
                    ", value='" + value + '\'' +
                    ", data='" + data + '\'' +
                    ", nonce='" + nonce + '\'' +
                    '}';
        }
    }

    class BlockResult {
        public String number; // QUANTITY - the block number. null when its pending block.
        public String hash; // DATA, 32 Bytes - hash of the block. null when its pending block.
        public String parentHash; // DATA, 32 Bytes - hash of the parent block.
        public String nonce; // DATA, 8 Bytes - hash of the generated proof-of-work. null when its pending block.
        public String sha3Uncles; // DATA, 32 Bytes - SHA3 of the uncles data in the block.
        public String logsBloom; // DATA, 256 Bytes - the bloom filter for the logs of the block. null when its pending block.
        public String transactionsRoot; // DATA, 32 Bytes - the root of the transaction trie of the block.
        public String stateRoot; // DATA, 32 Bytes - the root of the final state trie of the block.
        public String receiptsRoot; // DATA, 32 Bytes - the root of the receipts trie of the block.
        public String miner; // DATA, 20 Bytes - the address of the beneficiary to whom the mining rewards were given.
        public String difficulty; // QUANTITY - integer of the difficulty for this block.
        public String totalDifficulty; // QUANTITY - integer of the total difficulty of the chain until this block.
        public String extraData; // DATA - the "extra data" field of this block
        public String size;//QUANTITY - integer the size of this block in bytes.
        public String gasLimit;//: QUANTITY - the maximum gas allowed in this block.
        public String gasUsed; // QUANTITY - the total used gas by all transactions in this block.
        public String timestamp; //: QUANTITY - the unix timestamp for when the block was collated.
        public Object[] transactions; //: Array - Array of transaction objects, or 32 Bytes transaction hashes depending on the last given parameter.
        public String[] uncles; //: Array - Array of uncle hashes.

        @Override
        public String toString() {
            return "BlockResult{" +
                    "number='" + number + '\'' +
                    ", hash='" + hash + '\'' +
                    ", parentHash='" + parentHash + '\'' +
                    ", nonce='" + nonce + '\'' +
                    ", sha3Uncles='" + sha3Uncles + '\'' +
                    ", logsBloom='" + logsBloom + '\'' +
                    ", transactionsRoot='" + transactionsRoot + '\'' +
                    ", stateRoot='" + stateRoot + '\'' +
                    ", receiptsRoot='" + receiptsRoot + '\'' +
                    ", miner='" + miner + '\'' +
                    ", difficulty='" + difficulty + '\'' +
                    ", totalDifficulty='" + totalDifficulty + '\'' +
                    ", extraData='" + extraData + '\'' +
                    ", size='" + size + '\'' +
                    ", gas='" + gasLimit + '\'' +
                    ", gasUsed='" + gasUsed + '\'' +
                    ", timestamp='" + timestamp + '\'' +
                    ", transactions=" + Arrays.toString(transactions) +
                    ", uncles=" + Arrays.toString(uncles) +
                    '}';
        }
    }

    String eth_chainId();
    String web3_clientVersion();
    String web3_sha3(String data) throws Exception;
    String net_version();
    String net_peerCount();
    boolean net_listening();
    String eth_protocolVersion();
    Object eth_syncing();
    String eth_coinbase();
    boolean eth_mining();
    String eth_hashrate();
    String eth_gasPrice();
    String[] eth_accounts();
    String eth_blockNumber();
    String eth_getBalance(String address, String block) throws Exception;
    String eth_getLastBalance(String address) throws Exception;

    String eth_getStorageAt(String address, String storageIdx, String blockId) throws Exception;

    String eth_getTransactionCount(String address, String blockId) throws Exception;

    String eth_getBlockTransactionCountByHash(String blockHash)throws Exception;
    String eth_getBlockTransactionCountByNumber(String bnOrId)throws Exception;
    String eth_getUncleCountByBlockHash(String blockHash)throws Exception;
    String eth_getUncleCountByBlockNumber(String bnOrId)throws Exception;
    String eth_getCode(String addr, String bnOrId)throws Exception;
    String eth_sign(String addr, String data) throws Exception;
    String eth_sendTransaction(CallArguments transactionArgs) throws Exception;
    String eth_sendRawTransaction(String rawData) throws Exception;
    String eth_call(CallArguments args, String bnOrId) throws Exception;
    String eth_estimateGas(CallArguments args) throws Exception;
    BlockResult eth_getBlockByHash(String blockHash, Boolean fullTransactionObjects) throws Exception;
    BlockResult eth_getBlockByNumber(String bnOrId, Boolean fullTransactionObjects) throws Exception;
}
