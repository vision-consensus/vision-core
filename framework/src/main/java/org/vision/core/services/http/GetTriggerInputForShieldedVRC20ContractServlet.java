package org.vision.core.services.http;

import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.api.GrpcAPI.BytesMessage;
import org.vision.api.GrpcAPI.ShieldedVRC20TriggerContractParameters;
import org.vision.core.Wallet;

@Component
@Slf4j(topic = "API")
public class GetTriggerInputForShieldedVRC20ContractServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      String input = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(input);
      boolean visible = Util.getVisiblePost(input);
      ShieldedVRC20TriggerContractParameters.Builder builder =
          ShieldedVRC20TriggerContractParameters
              .newBuilder();
      JsonFormat.merge(input, builder, visible);
      BytesMessage result = wallet.getTriggerInputForShieldedVRC20Contract(builder.build());
      response.getWriter().println(JsonFormat.printToString(result, visible));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
