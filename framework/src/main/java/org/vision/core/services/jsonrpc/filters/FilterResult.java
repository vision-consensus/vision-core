package org.vision.core.services.jsonrpc.filters;

import lombok.Getter;
import org.vision.core.services.EthereumCompatibleService;

import java.util.List;
import java.util.concurrent.BlockingQueue;

public abstract class FilterResult<T> {

  private long expireTimeStamp;

  @Getter
  protected BlockingQueue<T> result;

  public void updateExpireTime() {
    expireTimeStamp = System.currentTimeMillis() + EthereumCompatibleService.EXPIRE_SECONDS * 1000;
  }

  public boolean isExpire() {
    return expireTimeStamp < System.currentTimeMillis();
  }

  public abstract void add(T t);

  public abstract List<T> popAll();
}
