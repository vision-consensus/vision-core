package org.vision.core.services.http;

import com.alibaba.fastjson.JSONObject;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.api.GrpcAPI;
import org.vision.common.utils.ByteArray;
import org.vision.core.Wallet;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.protos.Protocol.Transaction;

@Component
@Slf4j(topic = "API")
public class BroadcastHexServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      String input = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      String trx = JSONObject.parseObject(input).getString("transaction");
      Transaction transaction = Transaction.parseFrom(ByteArray.fromHexString(trx));
      TransactionCapsule transactionCapsule = new TransactionCapsule(transaction);
      String transactionID = ByteArray
          .toHexString(transactionCapsule.getTransactionId().getBytes());
      GrpcAPI.Return result = wallet.broadcastTransaction(transaction);
      JSONObject json = new JSONObject();
      json.put("result", result.getResult());
      json.put("code", result.getCode().toString());
      json.put("message", result.getMessage().toStringUtf8());
      json.put("transaction", JsonFormat.printToString(transaction, true));
      json.put("txid", transactionID);

      response.getWriter().println(json.toJSONString());
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
