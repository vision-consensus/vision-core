package org.vision.core.services.http;

import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.api.GrpcAPI.TransactionApprovedList;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Transaction;


@Component
@Slf4j(topic = "API")
public class GetTransactionApprovedListServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {

  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      Transaction transaction = Util.packTransaction(params.getParams(), params.isVisible());
      TransactionApprovedList reply = wallet.getTransactionApprovedList(transaction);
      if (reply != null) {
        response.getWriter().println(Util.printTransactionApprovedList(reply, params.isVisible()));
      } else {
        response.getWriter().println("{}");
      }
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
