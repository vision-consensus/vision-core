package org.vision.core.services.ratelimiter.adapter;

import org.vision.core.services.ratelimiter.strategy.QpsStrategy;
import org.vision.core.services.ratelimiter.RuntimeData;

public class DefaultBaseQqsAdapter implements IRateLimiter {

  private QpsStrategy strategy;

  public DefaultBaseQqsAdapter(String paramString) {
    this.strategy = new QpsStrategy(paramString);
  }

  @Override
  public boolean acquire(RuntimeData data) {
    return strategy.acquire();
  }
}