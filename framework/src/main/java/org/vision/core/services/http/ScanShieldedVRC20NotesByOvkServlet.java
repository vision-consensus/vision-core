package org.vision.core.services.http;

import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.api.GrpcAPI;
import org.vision.api.GrpcAPI.OvkDecryptVRC20Parameters;
import org.vision.common.utils.ByteArray;
import org.vision.core.Wallet;

@Component
@Slf4j(topic = "API")
public class ScanShieldedVRC20NotesByOvkServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      String input = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(input);
      boolean visible = Util.getVisiblePost(input);
      OvkDecryptVRC20Parameters.Builder ovkDecryptVRC20Parameters = OvkDecryptVRC20Parameters
          .newBuilder();
      JsonFormat.merge(input, ovkDecryptVRC20Parameters, visible);

      GrpcAPI.DecryptNotesVRC20 notes = wallet
          .scanShieldedVRC20NotesByOvk(ovkDecryptVRC20Parameters.getStartBlockIndex(),
              ovkDecryptVRC20Parameters.getEndBlockIndex(),
              ovkDecryptVRC20Parameters.getOvk().toByteArray(),
              ovkDecryptVRC20Parameters.getShieldedVRC20ContractAddress().toByteArray(),
              ovkDecryptVRC20Parameters.getEventsList()
          );
      response.getWriter()
          .println(ScanShieldedVRC20NotesByIvkServlet.convertOutput(notes, visible));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      long startBlockIndex = Long.parseLong(request.getParameter("start_block_index"));
      long endBlockIndex = Long.parseLong(request.getParameter("end_block_index"));
      String ovk = request.getParameter("ovk");
      String contractAddress = request.getParameter("shielded_VRC20_contract_address");
      if (visible) {
        contractAddress = Util.getHexAddress(contractAddress);
      }
      GrpcAPI.DecryptNotesVRC20 notes = wallet
          .scanShieldedVRC20NotesByOvk(startBlockIndex, endBlockIndex,
              ByteArray.fromHexString(ovk), ByteArray.fromHexString(contractAddress), null);

      response.getWriter()
          .println(ScanShieldedVRC20NotesByIvkServlet.convertOutput(notes, visible));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
