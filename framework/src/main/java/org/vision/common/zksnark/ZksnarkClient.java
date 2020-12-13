package org.vision.common.zksnark;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannelBuilder;
import org.vision.api.VisionZksnarkGrpc;
import org.vision.api.ZksnarkGrpcAPI.ZksnarkRequest;
import org.vision.api.ZksnarkGrpcAPI.ZksnarkResponse.Code;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.protos.Protocol.Transaction;

public class ZksnarkClient {

  public static final ZksnarkClient instance = new ZksnarkClient();

  private VisionZksnarkGrpc.VisionZksnarkBlockingStub blockingStub;

  public ZksnarkClient() {
    blockingStub = VisionZksnarkGrpc.newBlockingStub(ManagedChannelBuilder
        .forTarget("127.0.0.1:60051")
        .usePlaintext()
        .build());
  }

  public static ZksnarkClient getInstance() {
    return instance;
  }

  public boolean checkZksnarkProof(Transaction transaction, byte[] sighash, long valueBalance) {
    String txId = new TransactionCapsule(transaction).getTransactionId().toString();
    ZksnarkRequest request = ZksnarkRequest.newBuilder()
        .setTransaction(transaction)
        .setTxId(txId)
        .setSighash(ByteString.copyFrom(sighash))
        .setValueBalance(valueBalance)
        .build();
    return blockingStub.checkZksnarkProof(request).getCode() == Code.SUCCESS;
  }
}
