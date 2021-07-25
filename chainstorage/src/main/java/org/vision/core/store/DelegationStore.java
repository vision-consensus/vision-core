package org.vision.core.store;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.common.utils.ByteArray;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.BytesCapsule;
import org.vision.core.db.VisionStoreWithRevoking;

@Slf4j
@Component
public class DelegationStore extends VisionStoreWithRevoking<BytesCapsule> {

  public static final long REMARK = -1L;
  public static final int DEFAULT_BROKERAGE = 20;

  @Autowired
  public DelegationStore(@Value("delegation") String dbName) {
    super(dbName);
  }

  @Override
  public BytesCapsule get(byte[] key) {
    byte[] value = revokingDB.getUnchecked(key);
    return ArrayUtils.isEmpty(value) ? null : new BytesCapsule(value);
  }

  public void addReward(long cycle, byte[] address, long value) {
    byte[] key = buildRewardKey(cycle, address);
    BytesCapsule bytesCapsule = get(key);
    if (bytesCapsule == null) {
      put(key, new BytesCapsule(ByteArray.fromLong(value)));
    } else {
      put(key, new BytesCapsule(ByteArray
          .fromLong(ByteArray.toLong(bytesCapsule.getData()) + value)));
    }
  }

  public long getReward(long cycle, byte[] address) {
    BytesCapsule bytesCapsule = get(buildRewardKey(cycle, address));
    if (bytesCapsule == null) {
      return 0L;
    } else {
      return ByteArray.toLong(bytesCapsule.getData());
    }
  }

  public void addCyclePledgeRate(long cycle, long value) {
    byte[] key = buildCyclePledgeRateKey(cycle);
    put(key, new BytesCapsule(ByteArray.fromLong(value)));
  }

  public long getCyclePledgeRate(long cycle) {
    BytesCapsule bytesCapsule = get(buildCyclePledgeRateKey(cycle));
    if (bytesCapsule == null) {
      return 0L;
    } else {
      return ByteArray.toLong(bytesCapsule.getData());
    }
  }

  public void addSpreadMintReward(long cycle, long value) {
    byte[] key = buildSpreadMintRewardKey(cycle);
    BytesCapsule bytesCapsule = get(key);
    if (bytesCapsule == null) {
      put(key, new BytesCapsule(ByteArray.fromLong(value)));
    } else {
      put(key, new BytesCapsule(ByteArray
              .fromLong(ByteArray.toLong(bytesCapsule.getData()) + value)));
    }
  }

  public long getSpreadMintReward(long cycle) {
    BytesCapsule bytesCapsule = get(buildSpreadMintRewardKey(cycle));
    if (bytesCapsule == null) {
      return 0L;
    } else {
      return ByteArray.toLong(bytesCapsule.getData());
    }
  }

  public void setBeginCycle(byte[] address, long number) {
    put(address, new BytesCapsule(ByteArray.fromLong(number)));
  }

  public long getBeginCycle(byte[] address) {
    BytesCapsule bytesCapsule = get(address);
    return bytesCapsule == null ? 0 : ByteArray.toLong(bytesCapsule.getData());
  }

  public void setEndCycle(byte[] address, long number) {
    put(buildEndCycleKey(address), new BytesCapsule(ByteArray.fromLong(number)));
  }

  public long getEndCycle(byte[] address) {
    BytesCapsule bytesCapsule = get(buildEndCycleKey(address));
    return bytesCapsule == null ? REMARK : ByteArray.toLong(bytesCapsule.getData());
  }

  public void setSpreadMintBeginCycle(byte[] address, long number) {
    put(buildSpreadMintStartCycleKey(address), new BytesCapsule(ByteArray.fromLong(number)));
  }

  public long getSpreadMintBeginCycle(byte[] address) {
    BytesCapsule bytesCapsule = get(buildSpreadMintStartCycleKey(address));
    return bytesCapsule == null ? 0 : ByteArray.toLong(bytesCapsule.getData());
  }

  public void setSpreadMintEndCycle(byte[] address, long number) {
    put(buildSpreadMintEndCycleKey(address), new BytesCapsule(ByteArray.fromLong(number)));
  }

  public long getSpreadMintEndCycle(byte[] address) {
    BytesCapsule bytesCapsule = get(buildSpreadMintEndCycleKey(address));
    return bytesCapsule == null ? REMARK : ByteArray.toLong(bytesCapsule.getData());
  }

  public void setWitnessVote(long cycle, byte[] address, long value) {
    put(buildVoteKey(cycle, address), new BytesCapsule(ByteArray.fromLong(value)));
  }

  public long getWitnessVote(long cycle, byte[] address) {
    BytesCapsule bytesCapsule = get(buildVoteKey(cycle, address));
    if (bytesCapsule == null) {
      return REMARK;
    } else {
      return ByteArray.toLong(bytesCapsule.getData());
    }
  }

  public void setWitnessVoteWeight(long cycle, byte[] address, long value) {
    put(buildVoteWeightKey(cycle, address), new BytesCapsule(ByteArray.fromLong(value)));
  }

  public long getWitnessVoteWeight(long cycle, byte[] address) {
    BytesCapsule bytesCapsule = get(buildVoteWeightKey(cycle, address));
    if (bytesCapsule == null) {
      logger.info("patch voteWeight");
      return getWitnessVote(cycle, address);
      // return REMARK;
    } else {
      return ByteArray.toLong(bytesCapsule.getData());
    }
  }

  public void setAccountVote(long cycle, byte[] address, AccountCapsule accountCapsule) {
    put(buildAccountVoteKey(cycle, address), new BytesCapsule(accountCapsule.getData()));
  }

  public AccountCapsule getAccountVote(long cycle, byte[] address) {
    BytesCapsule bytesCapsule = get(buildAccountVoteKey(cycle, address));
    if (bytesCapsule == null) {
      return null;
    } else {
      return new AccountCapsule(bytesCapsule.getData());
    }
  }

  public void setTotalFreezeBalanceForSpreadMint(long cycle, long balance) {
    put(buildSpreadMintFreezeBalanceKey(cycle), new BytesCapsule(ByteArray.fromLong(balance)));
  }

  public long getTotalFreezeBalanceForSpreadMint(long cycle) {
    BytesCapsule bytesCapsule = get(buildSpreadMintFreezeBalanceKey(cycle));
    if (bytesCapsule == null) {
      return 0L;
    } else {
      return ByteArray.toLong(bytesCapsule.getData());
    }
  }

  public void setBrokerage(long cycle, byte[] address, int brokerage) {
    put(buildBrokerageKey(cycle, address), new BytesCapsule(ByteArray.fromInt(brokerage)));
  }

  public int getBrokerage(long cycle, byte[] address) {
    BytesCapsule bytesCapsule = get(buildBrokerageKey(cycle, address));
    if (bytesCapsule == null) {
      return DEFAULT_BROKERAGE;
    } else {
      return ByteArray.toInt(bytesCapsule.getData());
    }
  }

  public void setBrokerage(byte[] address, int brokerage) {
    setBrokerage(-1, address, brokerage);
  }

  public int getBrokerage(byte[] address) {
    return getBrokerage(-1, address);
  }

  private byte[] buildVoteKey(long cycle, byte[] address) {
    return (cycle + "-" + Hex.toHexString(address) + "-vote").getBytes();
  }

  private byte[] buildVoteWeightKey(long cycle, byte[] address) {
    return (cycle + "-" + Hex.toHexString(address) + "-voteWeight").getBytes();
  }

  private byte[] buildRewardKey(long cycle, byte[] address) {
    return (cycle + "-" + Hex.toHexString(address) + "-reward").getBytes();
  }

  private byte[] buildCyclePledgeRateKey(long cycle) {
    return (cycle + "-cyclePledgeRate").getBytes();
  }

  private byte[] buildAccountVoteKey(long cycle, byte[] address) {
    return (cycle + "-" + Hex.toHexString(address) + "-account-vote").getBytes();
  }

  private byte[] buildEndCycleKey(byte[] address) {
    return ("end-" + Hex.toHexString(address)).getBytes();
  }

  private byte[] buildBrokerageKey(long cycle, byte[] address) {
    return (cycle + "-" + Hex.toHexString(address) + "-brokerage").getBytes();
  }

  private byte[] buildSpreadMintRewardKey(long cycle) {
    return (cycle + "-" + Hex.toHexString(ByteArray.fromLong(cycle)) + "-spread-mint-reward").getBytes();
  }

  private byte[] buildSpreadMintFreezeBalanceKey(long cycle) {
    return (cycle + "-" + Hex.toHexString(ByteArray.fromLong(cycle)) + "-spread-mint-freeze-balance").getBytes();
  }

  private byte[] buildSpreadMintStartCycleKey(byte[] address) {
    return ("start-spread-mint-cycle-" + Hex.toHexString(address)).getBytes();
  }

  private byte[] buildSpreadMintEndCycleKey(byte[] address) {
    return ("end-spread-mint-cycle-" + Hex.toHexString(address)).getBytes();
  }
}
