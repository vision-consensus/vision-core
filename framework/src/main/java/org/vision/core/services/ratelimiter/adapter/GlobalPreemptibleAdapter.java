package org.vision.core.services.ratelimiter.adapter;

import org.vision.core.services.ratelimiter.strategy.GlobalPreemptibleStrategy;
import org.vision.core.services.ratelimiter.RuntimeData;

public class GlobalPreemptibleAdapter implements IPreemptibleRateLimiter {

  private GlobalPreemptibleStrategy strategy;

  public GlobalPreemptibleAdapter(String paramString) {

    strategy = new GlobalPreemptibleStrategy(paramString);
  }

  @Override
  public void release() {
    strategy.release();
  }

  @Override
  public boolean acquire(RuntimeData data) {
    return strategy.acquire();
  }

}