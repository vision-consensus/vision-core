package org.vision.core.services.ratelimiter.adapter;

import org.vision.core.services.ratelimiter.RuntimeData;

public interface IRateLimiter {

  boolean acquire(RuntimeData data);

}
