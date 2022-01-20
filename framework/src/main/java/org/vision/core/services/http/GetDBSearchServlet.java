package org.vision.core.services.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.common.utils.ByteArray;
import org.vision.core.ChainBaseManager;
import org.vision.core.Wallet;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.AccountTraceCapsule;
import org.vision.core.capsule.AssetIssueCapsule;
import org.vision.core.capsule.BlockBalanceTraceCapsule;
import org.vision.core.capsule.BlockCapsule;
import org.vision.core.capsule.BytesCapsule;
import org.vision.core.capsule.CodeCapsule;
import org.vision.core.capsule.ContractCapsule;
import org.vision.core.capsule.DelegatedResourceAccountIndexCapsule;
import org.vision.core.capsule.DelegatedResourceCapsule;
import org.vision.core.capsule.EthereumCompatibleRlpDedupCapsule;
import org.vision.core.capsule.ExchangeCapsule;
import org.vision.core.capsule.IncrementalMerkleTreeCapsule;
import org.vision.core.capsule.MarketAccountOrderCapsule;
import org.vision.core.capsule.MarketOrderCapsule;
import org.vision.core.capsule.MarketOrderIdListCapsule;
import org.vision.core.capsule.PbftSignCapsule;
import org.vision.core.capsule.ProposalCapsule;
import org.vision.core.capsule.SpreadRelationShipCapsule;
import org.vision.core.capsule.StorageRowCapsule;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.core.capsule.TransactionInfoCapsule;
import org.vision.core.capsule.TransactionRetCapsule;
import org.vision.core.capsule.VotesCapsule;
import org.vision.core.capsule.WitnessCapsule;
import org.vision.core.db.BlockIndexStore;
import org.vision.core.db.BlockStore;
import org.vision.core.db.CommonStore;
import org.vision.core.db.EthereumCompatibleRlpDedupStore;
import org.vision.core.db.PbftSignDataStore;
import org.vision.core.db.RecentBlockStore;
import org.vision.core.db.TransactionStore;
import org.vision.core.store.AccountIdIndexStore;
import org.vision.core.store.AccountIndexStore;
import org.vision.core.store.AccountStore;
import org.vision.core.store.AccountTraceStore;
import org.vision.core.store.AssetIssueStore;
import org.vision.core.store.AssetIssueV2Store;
import org.vision.core.store.BalanceTraceStore;
import org.vision.core.store.CodeStore;
import org.vision.core.store.ContractStore;
import org.vision.core.store.DelegatedResourceAccountIndexStore;
import org.vision.core.store.DelegatedResourceStore;
import org.vision.core.store.DelegationStore;
import org.vision.core.store.DynamicPropertiesStore;
import org.vision.core.store.ExchangeStore;
import org.vision.core.store.ExchangeV2Store;
import org.vision.core.store.IncrementalMerkleTreeStore;
import org.vision.core.store.MarketAccountStore;
import org.vision.core.store.MarketOrderStore;
import org.vision.core.store.MarketPairPriceToOrderStore;
import org.vision.core.store.MarketPairToPriceStore;
import org.vision.core.store.NullifierStore;
import org.vision.core.store.ProposalStore;
import org.vision.core.store.SpreadRelationShipStore;
import org.vision.core.store.StorageRowStore;
import org.vision.core.store.TransactionHistoryStore;
import org.vision.core.store.TransactionRetStore;
import org.vision.core.store.TreeBlockIndexStore;
import org.vision.core.store.VotesStore;
import org.vision.core.store.WitnessScheduleStore;
import org.vision.core.store.WitnessStore;
import org.vision.core.store.ZKProofStore;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;


@Component
@Slf4j(topic = "API")
public class GetDBSearchServlet extends RateLimiterServlet {

    @Autowired
    private Wallet wallet;
    @Autowired
    private ChainBaseManager chainBaseManager;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        String db = request.getParameter("db");
        String key = request.getParameter("key");
        searchDB(db, key, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        String db = request.getParameter("db");
        String key = request.getParameter("key");
        searchDB(db, key, response);
    }

    private void searchDB(String db, String key, HttpServletResponse response) {
        try {
            switch (db) {
                case "accountIdIndex":
                    AccountIdIndexStore accountIdIndexStore = chainBaseManager.getAccountIdIndexStore();
                    BytesCapsule accountIdIndexCapsule = accountIdIndexStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(Arrays.toString(accountIdIndexCapsule.getData()));
                    break;
                case "accountIndex":
                    AccountIndexStore accountIndexStore = chainBaseManager.getAccountIndexStore();
                    BytesCapsule accountIndexCapsule = accountIndexStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(Arrays.toString(accountIndexCapsule.getData()));
                    break;
                case "account":
                    AccountStore accountStore = chainBaseManager.getAccountStore();
                    AccountCapsule accountCapsule = accountStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(accountCapsule.getInstance(), true));
                    break;
                case "accountTrace":
                    AccountTraceStore accountTraceStore = chainBaseManager.getAccountTraceStore();
                    AccountTraceCapsule accountTraceCapsule = accountTraceStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(accountTraceCapsule.getInstance(), true));
                    break;
                case "assetIssue":
                    AssetIssueStore assetIssueStore = chainBaseManager.getAssetIssueStore();
                    AssetIssueCapsule assetIssueCapsule = assetIssueStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(assetIssueCapsule.getInstance(), true));
                    break;
                case "assetIssueV2":
                    AssetIssueV2Store assetIssueV2Store = chainBaseManager.getAssetIssueV2Store();
                    AssetIssueCapsule assetIssueV2Capsule = assetIssueV2Store.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(assetIssueV2Capsule.getInstance(), true));
                    break;
                case "BalanceTrace":
                    BalanceTraceStore balanceTraceStore = chainBaseManager.getBalanceTraceStore();
                    BlockBalanceTraceCapsule blockBalanceTraceCapsule = balanceTraceStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(blockBalanceTraceCapsule.getInstance(), true));
                    break;
                case "Code":
                    CodeStore codeStore = chainBaseManager.getCodeStore();
                    CodeCapsule codeCapsule = codeStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(codeCapsule.toString());
                    break;
                case "Contract":
                    ContractStore contractStore = chainBaseManager.getContractStore();
                    ContractCapsule contractCapsule = contractStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(contractCapsule.getInstance(), true));
                    break;
                case "DelegatedResourceAccountIndex":
                    DelegatedResourceAccountIndexStore delegatedResourceAccountIndexStore = chainBaseManager.getDelegatedResourceAccountIndexStore();
                    DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = delegatedResourceAccountIndexStore.get(ByteArray.fromHexString(Util.getHexAddress(key)));
                    response.getWriter().println(JsonFormat.printToString(delegatedResourceAccountIndexCapsule.getInstance(), true));
                    break;
                case "DelegatedResource":
                    DelegatedResourceStore delegatedResourceStore = chainBaseManager.getDelegatedResourceStore();
                    DelegatedResourceCapsule delegatedResourceCapsule = delegatedResourceStore.get(ByteArray.fromHexString(Util.getHexAddress(key)));
                    response.getWriter().println(JsonFormat.printToString(delegatedResourceCapsule.getInstance(), true));
                    break;
                case "Delegation":
                    DelegationStore delegationStore = chainBaseManager.getDelegationStore();
                    BytesCapsule delegationCapsule = delegationStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(Arrays.toString(delegationCapsule.getData()));
                    break;
                case "DynamicProperties":
                    DynamicPropertiesStore dynamicPropertiesStore = chainBaseManager.getDynamicPropertiesStore();
                    BytesCapsule dynamicPropertiesCapsule = dynamicPropertiesStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(Arrays.toString(dynamicPropertiesCapsule.getData()));
                    break;
                case "Exchange":
                    ExchangeStore exchangeStore = chainBaseManager.getExchangeStore();
                    ExchangeCapsule exchangeCapsule = exchangeStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(exchangeCapsule.getInstance(), true));
                    break;
                case "ExchangeV2":
                    ExchangeV2Store exchangeV2Store = chainBaseManager.getExchangeV2Store();
                    ExchangeCapsule exchangeV2Capsule = exchangeV2Store.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(exchangeV2Capsule.getInstance(), true));
                    break;
                case "IncrementalMerkleTree":
                    IncrementalMerkleTreeStore incrementalMerkleTreeStore = chainBaseManager.getMerkleTreeStore();
                    IncrementalMerkleTreeCapsule incrementalMerkleTreeCapsule = incrementalMerkleTreeStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(incrementalMerkleTreeCapsule.getInstance(), true));
                    break;
                case "MarketAccountOrder":
                    MarketAccountStore marketAccountStore = chainBaseManager.getMarketAccountStore();
                    MarketAccountOrderCapsule marketAccountOrderCapsule = marketAccountStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(marketAccountOrderCapsule.getInstance(), true));
                    break;
                case "MarketOrder":
                    MarketOrderStore marketOrderStore = chainBaseManager.getMarketOrderStore();
                    MarketOrderCapsule marketOrderCapsule = marketOrderStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(marketOrderCapsule.getInstance(), true));
                    break;
                case "MarketPairPriceToOrder":
                    MarketPairPriceToOrderStore marketPairPriceToOrderStore = chainBaseManager.getMarketPairPriceToOrderStore();
                    MarketOrderIdListCapsule marketOrderIdListCapsule = marketPairPriceToOrderStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(marketOrderIdListCapsule.getInstance(), true));
                    break;
                case "MarketPairToPrice":
                    MarketPairToPriceStore marketPairToPriceStore = chainBaseManager.getMarketPairToPriceStore();
                    BytesCapsule marketPairToPriceCapsule = marketPairToPriceStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(Arrays.toString(marketPairToPriceCapsule.getData()));
                    break;
                case "Nullifier":
                    NullifierStore nullifierStore = chainBaseManager.getNullifierStore();
                    BytesCapsule nullifierCapsule = nullifierStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(Arrays.toString(nullifierCapsule.getData()));
                    break;
                case "Proposal":
                    ProposalStore proposalStore = chainBaseManager.getProposalStore();
                    ProposalCapsule proposalCapsule = proposalStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(proposalCapsule.getInstance(), true));
                    break;
                case "SpreadRelationShip":
                    SpreadRelationShipStore spreadRelationShipStore = chainBaseManager.getSpreadRelationShipStore();
                    SpreadRelationShipCapsule spreadRelationShipCapsule = spreadRelationShipStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(spreadRelationShipCapsule.getInstance(), true));
                    break;
                case "StorageRow":
                    StorageRowStore storageRowStore = chainBaseManager.getStorageRowStore();
                    StorageRowCapsule storageRowCapsule = storageRowStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(storageRowCapsule.toString());
                    break;
                case "TransactionHistory":
                    TransactionHistoryStore transactionHistoryStore = chainBaseManager.getTransactionHistoryStore();
                    TransactionInfoCapsule transactionInfoCapsule = transactionHistoryStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(transactionInfoCapsule.getInstance(), true));
                    break;
                case "TransactionRet":
                    TransactionRetStore transactionRetStore = chainBaseManager.getTransactionRetStore();
                    TransactionRetCapsule transactionRetCapsule = transactionRetStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(transactionRetCapsule.getInstance(), true));
                    break;
                case "TreeBlockIndex":
                    TreeBlockIndexStore treeBlockIndexStore = chainBaseManager.getMerkleTreeIndexStore();
                    BytesCapsule treeBlockIndexCapsule = treeBlockIndexStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(Arrays.toString(treeBlockIndexCapsule.getData()));
                    break;
                case "Votes":
                    VotesStore votesStore = chainBaseManager.getVotesStore();
                    VotesCapsule votesCapsule = votesStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(votesCapsule.getInstance(), true));
                    break;
                case "WitnessSchedule":
                    WitnessScheduleStore witnessScheduleStore = chainBaseManager.getWitnessScheduleStore();
                    BytesCapsule witnessScheduleCapsule = witnessScheduleStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(Arrays.toString(witnessScheduleCapsule.getData()));
                    break;
                case "Witness":
                    WitnessStore witnessStore = chainBaseManager.getWitnessStore();
                    WitnessCapsule witnessCapsule = witnessStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(witnessCapsule.getInstance(), true));
                    break;
                case "ZKProof":
                    ZKProofStore zkProofStore = chainBaseManager.getProofStore();
                    boolean zkProof = zkProofStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(zkProof);
                    break;
                case "BlockIndex":
                    BlockIndexStore blockIndexStore = chainBaseManager.getBlockIndexStore();
                    BytesCapsule blockIndexCapsule = blockIndexStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(Arrays.toString(blockIndexCapsule.getData()));
                    break;
                case "Block":
                    BlockStore blockStore = chainBaseManager.getBlockStore();
                    BlockCapsule blockCapsule = blockStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(blockCapsule.getInstance(), true));
                    break;
                case "Common":
                    CommonStore commonStore = chainBaseManager.getCommonStore();
                    BytesCapsule commonCapsule = commonStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(Arrays.toString(commonCapsule.getData()));
                    break;
                case "EthereumCompatibleRlpDedup":
                    EthereumCompatibleRlpDedupStore ethereumCompatibleRlpDedupStore = chainBaseManager.getEthereumCompatibleRlpDedupStore();
                    EthereumCompatibleRlpDedupCapsule ethereumCompatibleRlpDedupCapsule = ethereumCompatibleRlpDedupStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(ethereumCompatibleRlpDedupCapsule.toString());
                    break;
                case "PbftSignData":
                    PbftSignDataStore pbftSignDataStore = chainBaseManager.getPbftSignDataStore();
                    PbftSignCapsule pbftSignCapsule = pbftSignDataStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(pbftSignCapsule.getInstance(), true));
                    break;
                case "RecentBlock":
                    RecentBlockStore recentBlockStore = chainBaseManager.getRecentBlockStore();
                    BytesCapsule recentBlockCapsule = recentBlockStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(Arrays.toString(recentBlockCapsule.getData()));
                    break;
                case "Transaction":
                    TransactionStore transactionStore = chainBaseManager.getTransactionStore();
                    TransactionCapsule transactionCapsule = transactionStore.get(ByteArray.fromHexString(key));
                    response.getWriter().println(JsonFormat.printToString(transactionCapsule.getInstance(), true));
                    break;
            }
        } catch (Exception e) {
            Util.processError(e, response);
        }

    }
}