package org.vision.core.services.ratelimiter.adapter;

import org.vision.core.services.ratelimiter.strategy.IPQpsStrategy;
import org.vision.core.services.ratelimiter.RuntimeData;

public class IPQPSRateLimiterAdapter implements IRateLimiter {

  private IPQpsStrategy strategy;

  public IPQPSRateLimiterAdapter(String paramString) {
    strategy = new IPQpsStrategy(paramString);
  }

  @Override
  public boolean acquire(RuntimeData data) {
    return strategy.acquire(data.getRemoteAddr());
  }

}