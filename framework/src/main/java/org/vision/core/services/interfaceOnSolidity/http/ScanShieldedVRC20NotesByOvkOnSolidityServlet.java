package org.vision.core.services.interfaceOnSolidity.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.core.services.interfaceOnSolidity.WalletOnSolidity;
import org.vision.core.services.http.ScanShieldedVRC20NotesByOvkServlet;


@Component
@Slf4j(topic = "API")
public class ScanShieldedVRC20NotesByOvkOnSolidityServlet extends ScanShieldedVRC20NotesByOvkServlet {


  @Autowired
  private WalletOnSolidity walletOnSolidity;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    walletOnSolidity.futureGet(() -> super.doGet(request, response));
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    walletOnSolidity.futureGet(() -> super.doPost(request, response));
  }
}
