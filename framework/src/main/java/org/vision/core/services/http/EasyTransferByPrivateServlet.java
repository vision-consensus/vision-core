package org.vision.core.services.http;

import com.google.protobuf.ByteString;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.api.GrpcAPI;
import org.vision.api.GrpcAPI.EasyTransferByPrivateMessage;
import org.vision.api.GrpcAPI.EasyTransferResponse;
import org.vision.api.GrpcAPI.Return.response_code;
import org.vision.common.crypto.SignInterface;
import org.vision.common.crypto.SignUtils;
import org.vision.core.Wallet;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.core.config.args.Args;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.contract.BalanceContract.TransferContract;


@Component
@Slf4j(topic = "API")
public class EasyTransferByPrivateServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {

  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    GrpcAPI.Return.Builder returnBuilder = GrpcAPI.Return.newBuilder();
    EasyTransferResponse.Builder responseBuild = EasyTransferResponse.newBuilder();
    boolean visible = false;
    try {
      PostParams params = PostParams.getPostParams(request);
      visible = params.isVisible();
      EasyTransferByPrivateMessage.Builder build = EasyTransferByPrivateMessage.newBuilder();
      JsonFormat.merge(params.getParams(), build, visible);
      byte[] privateKey = build.getPrivateKey().toByteArray();
      SignInterface ecKey = SignUtils.fromPrivate(privateKey, Args.getInstance()
          .isECKeyCryptoEngine());
      byte[] owner = ecKey.getAddress();
      TransferContract.Builder builder = TransferContract.newBuilder();
      builder.setOwnerAddress(ByteString.copyFrom(owner));
      builder.setToAddress(build.getToAddress());
      builder.setAmount(build.getAmount());

      TransactionCapsule transactionCapsule;
      transactionCapsule = wallet
          .createTransactionCapsule(builder.build(), ContractType.TransferContract);
      transactionCapsule.sign(privateKey);
      GrpcAPI.Return result = wallet.broadcastTransaction(transactionCapsule.getInstance());
      responseBuild.setTransaction(transactionCapsule.getInstance());
      responseBuild.setResult(result);
      response.getWriter().println(Util.printEasyTransferResponse(responseBuild.build(), visible));
    } catch (Exception e) {
      returnBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getMessage()));
      responseBuild.setResult(returnBuilder.build());
      try {
        response.getWriter().println(JsonFormat.printToString(responseBuild.build(), visible));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
      return;
    }
  }
}
