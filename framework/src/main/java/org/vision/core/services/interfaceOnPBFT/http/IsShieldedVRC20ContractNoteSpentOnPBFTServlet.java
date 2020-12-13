package org.vision.core.services.interfaceOnPBFT.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.core.services.interfaceOnPBFT.WalletOnPBFT;
import org.vision.core.services.http.IsShieldedVRC20ContractNoteSpentServlet;

@Component
@Slf4j(topic = "API")
public class IsShieldedVRC20ContractNoteSpentOnPBFTServlet extends IsShieldedVRC20ContractNoteSpentServlet {

  @Autowired
  private WalletOnPBFT walletOnPBFT;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    walletOnPBFT.futureGet(() -> super.doGet(request, response));
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    walletOnPBFT.futureGet(() -> super.doPost(request, response));
  }
}
