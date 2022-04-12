package org.vision.core.services.http;

import com.googlecode.jsonrpc4j.JsonRpcServer;
import com.googlecode.jsonrpc4j.ProxyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.core.ChainBaseManager;
import org.vision.core.Wallet;
import org.vision.core.services.EthereumCompatibleService;
import org.vision.core.services.NodeInfoService;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@Slf4j(topic = "API")
public class EthereumCompatibleServlet extends RateLimiterServlet {

    @Autowired
    private NodeInfoService nodeInfoService;
    @Autowired
    private Wallet wallet;
    @Autowired
    private ChainBaseManager chainBaseManager;

    private JsonRpcServer jsonRpcServer;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try {
            //PostParams params = PostParams.getPostParams(request);
            jsonRpcServer.handle(request, response);
        } catch (Exception e) {
            Util.processError(e, response);
        }
    }

    @Override
    public void init() throws ServletException {
        super.init();
        EthereumCompatibleService service = new EthereumCompatibleService(nodeInfoService, wallet, chainBaseManager);
        this.jsonRpcServer = new JsonRpcServer(service, EthereumCompatibleService.class);
    }
}
