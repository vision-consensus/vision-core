package org.vision.core.services.jsonrpc.filters;

import lombok.Getter;
import org.vision.common.application.EthereumCompatible.LogFilterElement;
import org.vision.common.application.EthereumCompatible.FilterRequest;
import org.vision.core.Wallet;
import org.vision.core.exception.JsonRpcInvalidParamsException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class LogFilterAndResult extends FilterResult<LogFilterElement> {

  @Getter
  private final LogFilterWrapper logFilterWrapper;

  public LogFilterAndResult(FilterRequest fr, long currentMaxBlockNum, Wallet wallet)
      throws JsonRpcInvalidParamsException {
    this.logFilterWrapper = new LogFilterWrapper(fr, currentMaxBlockNum, wallet);
    result = new LinkedBlockingQueue<>();
    this.updateExpireTime();
  }

  @Override
  public void add(LogFilterElement logFilterElement) {
    result.add(logFilterElement);
  }

  @Override
  public List<LogFilterElement> popAll() {
    List<LogFilterElement> elements = new ArrayList<>();
    result.drainTo(elements);
    return elements;
  }
}
