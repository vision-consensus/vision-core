package org.vision.core.services.http;

import com.google.protobuf.ByteString;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.api.GrpcAPI.BytesMessage;
import org.vision.api.GrpcAPI.ExpandedSpendingKeyMessage;
import org.vision.common.utils.ByteArray;
import org.vision.core.Wallet;

@Component
@Slf4j(topic = "API")
public class GetExpandedSpendingKeyServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      String sk = request.getParameter("value");
      fillResponse(visible, ByteString.copyFrom(ByteArray.fromHexString(sk)), response);
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      BytesMessage.Builder build = BytesMessage.newBuilder();
      JsonFormat.merge(params.getParams(), build);
      fillResponse(params.isVisible(), build.getValue(), response);
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }

  private void fillResponse(boolean visible, ByteString spendingKey, HttpServletResponse response)
      throws Exception {
    ExpandedSpendingKeyMessage reply = wallet.getExpandedSpendingKey(spendingKey);
    if (reply != null) {
      response.getWriter().println(JsonFormat.printToString(reply, visible));
    } else {
      response.getWriter().println("{}");
    }
  }
}