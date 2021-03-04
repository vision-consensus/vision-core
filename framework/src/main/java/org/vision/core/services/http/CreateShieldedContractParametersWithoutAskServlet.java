package org.vision.core.services.http;

import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.api.GrpcAPI.PrivateShieldedVRC20ParametersWithoutAsk;
import org.vision.api.GrpcAPI.ShieldedVRC20Parameters;
import org.vision.core.Wallet;

@Component
@Slf4j(topic = "API")
public class CreateShieldedContractParametersWithoutAskServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      PrivateShieldedVRC20ParametersWithoutAsk.Builder build =
          PrivateShieldedVRC20ParametersWithoutAsk.newBuilder();
      JsonFormat.merge(params.getParams(), build, params.isVisible());
      ShieldedVRC20Parameters shieldedVRC20Parameters = wallet
          .createShieldedContractParametersWithoutAsk(build.build());
      response.getWriter().println(JsonFormat
              .printToString(shieldedVRC20Parameters, params.isVisible()));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
