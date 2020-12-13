package org.vision.core.store;

import com.google.common.collect.Streams;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.core.capsule.ProposalCapsule;
import org.vision.core.db.VisionStoreWithRevoking;
import org.vision.core.exception.ItemNotFoundException;

@Component
public class ProposalStore extends VisionStoreWithRevoking<ProposalCapsule> {

  @Autowired
  public ProposalStore(@Value("proposal") String dbName) {
    super(dbName);
  }

  @Override
  public ProposalCapsule get(byte[] key) throws ItemNotFoundException {
    byte[] value = revokingDB.get(key);
    return new ProposalCapsule(value);
  }

  /**
   * get all proposals.
   */
  public List<ProposalCapsule> getAllProposals() {
    return Streams.stream(iterator())
        .map(Map.Entry::getValue)
        .sorted(
            (ProposalCapsule a, ProposalCapsule b) -> a.getCreateTime() <= b.getCreateTime() ? 1
                : -1)
        .collect(Collectors.toList());
  }
}