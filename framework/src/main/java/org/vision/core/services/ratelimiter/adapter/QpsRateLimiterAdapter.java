package org.vision.core.services.ratelimiter.adapter;

import org.vision.core.services.ratelimiter.strategy.QpsStrategy;
import org.vision.core.services.ratelimiter.RuntimeData;

public class QpsRateLimiterAdapter implements IRateLimiter {

  private QpsStrategy strategy;

  public QpsRateLimiterAdapter(String paramString) {
    strategy = new QpsStrategy(paramString);
  }

  @Override
  public boolean acquire(RuntimeData data) {
    return strategy.acquire();
  }

}