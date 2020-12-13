package org.vision.common.overlay.client;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.vision.api.GrpcAPI.AssetIssueList;
import org.vision.api.GrpcAPI.BytesMessage;
import org.vision.api.GrpcAPI.EmptyMessage;
import org.vision.api.GrpcAPI.NodeList;
import org.vision.api.GrpcAPI.NumberMessage;
import org.vision.api.GrpcAPI.Return;
import org.vision.api.WalletGrpc;
import org.vision.protos.Protocol.Account;
import org.vision.protos.Protocol.Block;
import org.vision.protos.Protocol.Transaction;
import org.vision.protos.contract.AssetIssueContractOuterClass.AssetIssueContract;
import org.vision.protos.contract.AssetIssueContractOuterClass.ParticipateAssetIssueContract;
import org.vision.protos.contract.AssetIssueContractOuterClass.TransferAssetContract;
import org.vision.protos.contract.BalanceContract.TransferContract;
import org.vision.protos.contract.WitnessContract.VoteWitnessContract;
import org.vision.protos.contract.WitnessContract.WitnessCreateContract;

public class WalletGrpcClient {

  private final ManagedChannel channel;
  private final WalletGrpc.WalletBlockingStub walletBlockingStub;

  public WalletGrpcClient(String host, int port) {
    channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    walletBlockingStub = WalletGrpc.newBlockingStub(channel);
  }

  public WalletGrpcClient(String host) {
    channel = ManagedChannelBuilder.forTarget(host)
        .usePlaintext(true)
        .build();
    walletBlockingStub = WalletGrpc.newBlockingStub(channel);
  }

  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  public Account queryAccount(byte[] address) {
    ByteString addressByteString = ByteString.copyFrom(address);
    Account request = Account.newBuilder().setAddress(addressByteString).build();
    return walletBlockingStub.getAccount(request);
  }

  public Transaction createTransaction(TransferContract contract) {
    return walletBlockingStub.createTransaction(contract);
  }

  public Transaction createTransferAssetTransaction(TransferAssetContract contract) {
    return walletBlockingStub.transferAsset(contract);
  }

  public Transaction createParticipateAssetIssueTransaction(
      ParticipateAssetIssueContract contract) {
    return walletBlockingStub.participateAssetIssue(contract);
  }

  public Transaction createAssetIssue(AssetIssueContract contract) {
    return walletBlockingStub.createAssetIssue(contract);
  }

  public Transaction voteWitnessAccount(VoteWitnessContract contract) {
    return walletBlockingStub.voteWitnessAccount(contract);
  }

  public Transaction createWitness(WitnessCreateContract contract) {
    return walletBlockingStub.createWitness(contract);
  }

  public boolean broadcastTransaction(Transaction signedTransaction) {
    Return response = walletBlockingStub.broadcastTransaction(signedTransaction);
    return response.getResult();
  }

  public Block getBlock(long blockNum) {
    if (blockNum < 0) {
      return walletBlockingStub.getNowBlock(EmptyMessage.newBuilder().build());
    }
    NumberMessage.Builder builder = NumberMessage.newBuilder();
    builder.setNum(blockNum);
    return walletBlockingStub.getBlockByNum(builder.build());
  }

  public Optional<NodeList> listNodes() {
    NodeList nodeList = walletBlockingStub
        .listNodes(EmptyMessage.newBuilder().build());
    if (nodeList != null) {
      return Optional.of(nodeList);
    }
    return Optional.empty();
  }

  public Optional<AssetIssueList> getAssetIssueByAccount(byte[] address) {
    ByteString addressByteString = ByteString.copyFrom(address);
    Account request = Account.newBuilder().setAddress(addressByteString).build();
    AssetIssueList assetIssueList = walletBlockingStub
        .getAssetIssueByAccount(request);
    if (assetIssueList != null) {
      return Optional.of(assetIssueList);
    }
    return Optional.empty();
  }

  public AssetIssueContract getAssetIssueByName(String assetName) {
    ByteString assetNameBs = ByteString.copyFrom(assetName.getBytes());
    BytesMessage request = BytesMessage.newBuilder().setValue(assetNameBs).build();
    return walletBlockingStub.getAssetIssueByName(request);
  }

  public Optional<AssetIssueList> getAssetIssueListByName(String assetName) {
    ByteString assetNameBs = ByteString.copyFrom(assetName.getBytes());
    BytesMessage request = BytesMessage.newBuilder().setValue(assetNameBs).build();

    AssetIssueList assetIssueList = walletBlockingStub
        .getAssetIssueListByName(request);
    if (assetIssueList != null) {
      return Optional.of(assetIssueList);
    }
    return Optional.empty();
  }

  public AssetIssueContract getAssetIssueById(String assetId) {
    ByteString assetIdBs = ByteString.copyFrom(assetId.getBytes());
    BytesMessage request = BytesMessage.newBuilder().setValue(assetIdBs).build();
    return walletBlockingStub.getAssetIssueById(request);
  }

}
