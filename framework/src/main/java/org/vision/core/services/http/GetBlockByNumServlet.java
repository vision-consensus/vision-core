package org.vision.core.services.http;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.api.GrpcAPI.NumberMessage;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Block;


@Component
@Slf4j(topic = "API")
public class GetBlockByNumServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      fillResponse(Util.getVisible(request), Long.parseLong(request.getParameter("num")), response);
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      NumberMessage.Builder build = NumberMessage.newBuilder();
      JsonFormat.merge(params.getParams(), build, params.isVisible());
      fillResponse(params.isVisible(), build.getNum(), response);
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }

  private void fillResponse(boolean visible, long num, HttpServletResponse response)
      throws IOException {
    Block reply = wallet.getBlockByNum(num);
    if (reply != null) {
      response.getWriter().println(Util.printBlock(reply, visible));
    } else {
      response.getWriter().println("{}");
    }
  }
}