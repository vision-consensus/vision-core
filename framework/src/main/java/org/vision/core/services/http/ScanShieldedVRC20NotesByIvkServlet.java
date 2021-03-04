package org.vision.core.services.http;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.api.GrpcAPI;
import org.vision.api.GrpcAPI.IvkDecryptVRC20Parameters;
import org.vision.common.utils.ByteArray;
import org.vision.core.Wallet;

@Component
@Slf4j(topic = "API")
public class ScanShieldedVRC20NotesByIvkServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  public static String convertOutput(GrpcAPI.DecryptNotesVRC20 notes, boolean visible) {
    String resultString = JsonFormat.printToString(notes, visible);
    if (notes.getNoteTxsCount() == 0) {
      return resultString;
    } else {
      JSONObject jsonNotes = JSONObject.parseObject(resultString);
      JSONArray array = jsonNotes.getJSONArray("noteTxs");
      for (int index = 0; index < array.size(); index++) {
        JSONObject item = array.getJSONObject(index);
        item.put("index", notes.getNoteTxs(index).getIndex());
      }
      return jsonNotes.toJSONString();
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      IvkDecryptVRC20Parameters.Builder ivkDecryptVRC20Parameters = IvkDecryptVRC20Parameters
          .newBuilder();
      JsonFormat.merge(params.getParams(), ivkDecryptVRC20Parameters, params.isVisible());

      GrpcAPI.DecryptNotesVRC20 notes = wallet
          .scanShieldedVRC20NotesByIvk(ivkDecryptVRC20Parameters.getStartBlockIndex(),
              ivkDecryptVRC20Parameters.getEndBlockIndex(),
              ivkDecryptVRC20Parameters.getShieldedVRC20ContractAddress().toByteArray(),
              ivkDecryptVRC20Parameters.getIvk().toByteArray(),
              ivkDecryptVRC20Parameters.getAk().toByteArray(),
              ivkDecryptVRC20Parameters.getNk().toByteArray(),
              ivkDecryptVRC20Parameters.getEventsList());
      response.getWriter().println(convertOutput(notes, visible));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      long startNum = Long.parseLong(request.getParameter("start_block_index"));
      long endNum = Long.parseLong(request.getParameter("end_block_index"));
      String ivk = request.getParameter("ivk");

      String contractAddress = request.getParameter("shielded_VRC20_contract_address");
      if (visible) {
        contractAddress = Util.getHexAddress(contractAddress);
      }

      String ak = request.getParameter("ak");
      String nk = request.getParameter("nk");
      GrpcAPI.DecryptNotesVRC20 notes = wallet
          .scanShieldedVRC20NotesByIvk(startNum, endNum,
              ByteArray.fromHexString(contractAddress), ByteArray.fromHexString(ivk),
              ByteArray.fromHexString(ak), ByteArray.fromHexString(nk), null);
      response.getWriter().println(convertOutput(notes, visible));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
