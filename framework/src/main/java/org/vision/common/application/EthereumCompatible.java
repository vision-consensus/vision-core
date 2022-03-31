package org.vision.common.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.stereotype.Component;
import org.vision.common.runtime.vm.DataWord;
import org.vision.common.utils.ByteArray;
import org.vision.core.exception.*;
import org.vision.protos.Protocol;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
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
        public long timestamp;

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
        public String mixHash;

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

    class LogFilterElement {
        public String logIndex;
        public String transactionIndex;
        public String transactionHash;
        public String blockHash;
        public String blockNumber;
        public String address;
        public String data;
        public String[] topics;
        public boolean removed;

        public LogFilterElement() {
        }

        public LogFilterElement(String blockHash, Long blockNum, String txId, Integer txIndex,
                                String contractAddress, List<DataWord> topicList, String logData, int logIdx,
                                boolean removed) {
            logIndex = ByteArray.toJsonHex(logIdx);
            this.blockNumber = blockNum == null ? null : ByteArray.toJsonHex(blockNum);
            this.blockHash = blockHash == null ? null : ByteArray.toJsonHex(blockHash);
            transactionIndex = txIndex == null ? null : ByteArray.toJsonHex(txIndex);
            transactionHash = ByteArray.toJsonHex(txId);
            address = ByteArray.toJsonHex(contractAddress);
            data = logData == null ? "0x" : ByteArray.toJsonHex(logData);
            topics = new String[topicList.size()];
            for (int i = 0; i < topics.length; i++) {
                topics[i] = ByteArray.toJsonHex(topicList.get(i).getData());
            }
            this.removed = removed;
        }

        @Override
        public String toString() {
            return "LogFilterElement{" +
                    "logIndex='" + logIndex + '\'' +
                    ", blockNumber='" + blockNumber + '\'' +
                    ", blockHash='" + blockHash + '\'' +
                    ", transactionHash='" + transactionHash + '\'' +
                    ", transactionIndex='" + transactionIndex + '\'' +
                    ", address='" + address + '\'' +
                    ", data='" + data + '\'' +
                    ", topics=" + Arrays.toString(topics) +
                    '}';
        }
    }

    class TransactionResultDTO {
        public String blockHash;
        public String blockNumber;
        public String chainId;
        public String condition;
        public String creates;
        public String from;
        public String gas;
        public String gasPrice;
        public String hash;
        public String input;
        public String nonce;
        public String publicKey;
        public String r;
        public String raw;
        public String s;
        public String standardV;
        public String to;
        public String transactionIndex;
        public String v;
        public String value;
    }

    class TransactionReceiptDTO{
        public String transactionHash;          // hash of the transaction.
        public String transactionIndex;         // integer of the transactions index position in the block.
        public String blockHash;                // hash of the block where this transaction was in.
        public String blockNumber;              // block number where this transaction was in.
        public String from;                     // 20 Bytes - address of the sender.
        public String to;                       // 20 Bytes - address of the receiver. null when its a contract creation transaction.
        public String cumulativeGasUsed;        // The total amount of gas used when this transaction was executed in the block.
        public String gasUsed;                  // The amount of gas used by this specific transaction alone.
        public String contractAddress;          // The contract address created, if the transaction was a contract creation, otherwise  null .
        public LogFilterElement[] logs;         // Array of log objects, which this transaction generated.
        public String logsBloom;                       // 256 Bytes - Bloom filter for light clients to quickly retrieve related logs.
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String root;  // 32 bytes of post-transaction stateroot (pre Byzantium)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String status;  //  either 1 (success) or 0 (failure) (post Byzantium)
        public String type;  //  either 1 (success) or 0 (failure) (post Byzantium)

        public TransactionReceiptDTO(){

        }
        public TransactionReceiptDTO(Protocol.Block block, Protocol.TransactionInfo transactionInfo){

        }
    }

    @NoArgsConstructor
    @AllArgsConstructor
    class FilterRequest {

        @Getter
        @Setter
        private String fromBlock;
        @Getter
        @Setter
        private String toBlock;
        @Getter
        @Setter
        private Object address;
        @Getter
        @Setter
        private Object[] topics;
        @Getter
        @Setter
        private String blockHash;  // EIP-234: makes fromBlock = toBlock = blockHash

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

    TransactionResultDTO eth_getTransactionByHash(String transactionHash) throws Exception;
    TransactionResultDTO eth_getTransactionByBlockHashAndIndex(String blockHash, String index) throws Exception;
    TransactionResultDTO eth_getTransactionByBlockNumberAndIndex(String bnOrId, String index) throws Exception;
    TransactionReceiptDTO eth_getTransactionReceipt(String transactionHash) throws Exception;

    LogFilterElement[] eth_getLogs(FilterRequest fr) throws JsonRpcInvalidParamsException,
            ExecutionException, InterruptedException, BadItemException, ItemNotFoundException,
            JsonRpcTooManyResultException, JsonRpcMethodNotFoundException;
}
