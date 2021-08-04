package org.vision.core.services.http;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.api.GrpcAPI.SpreadRelationShipList;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Account;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@Component
@Slf4j(topic = "API")
public class GetSpreadMintParentServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      String address = request.getParameter("address");
      String level = request.getParameter("level");
      Account.Builder build = Account.newBuilder();
      JSONObject jsonObject = new JSONObject();
      jsonObject.put("address", address);
      jsonObject.put("level", level);
      JsonFormat.merge(jsonObject.toJSONString(), build, visible);
      fillResponseList(visible, build.build(), Integer.parseInt(level), response);
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      Account.Builder build = Account.newBuilder();
      JsonFormat.merge(params.getParams(), build, params.isVisible());
      JSONObject jsonObject = JSONObject.parseObject(params.getParams());
      fillResponseList(params.isVisible(), build.build(), jsonObject.getInteger("level"),response);
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }

  private void fillResponseList(boolean visible, Account account, int level, HttpServletResponse response)
          throws Exception {
    SpreadRelationShipList reply = wallet.getSpreadMintParentList(account.getAddress().toByteArray(), level);
    if (reply != null) {
      response.getWriter().println(JsonFormat.printToString(reply, visible));
    } else {
      response.getWriter().println("{}");
    }
  }
}
