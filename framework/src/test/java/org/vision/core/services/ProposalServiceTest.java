package org.vision.core.services;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.vision.common.utils.FileUtil;
import org.vision.core.Constant;
import org.vision.core.capsule.ProposalCapsule;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.args.Args;
import org.vision.core.consensus.ProposalService;
import org.vision.core.db.Manager;
import org.vision.core.utils.ProposalUtil;
import org.vision.common.application.VisionApplicationContext;
import org.vision.protos.Protocol.Proposal;

public class ProposalServiceTest {

  private VisionApplicationContext context;
  private Manager manager;
  private String dbPath = "output_proposal_test";

  @Before
  public void init() {
    Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
    context = new VisionApplicationContext(DefaultConfig.class);
    manager = context.getBean(Manager.class);
    manager.getDynamicPropertiesStore().saveLatestBlockHeaderNumber(5);
  }

  @Test
  public void test() {
    Set<Long> set = new HashSet<>();
    for (ProposalUtil.ProposalType proposalType : ProposalUtil.ProposalType.values()) {
      Assert.assertTrue(set.add(proposalType.getCode()));
    }

    Proposal proposal = Proposal.newBuilder().putParameters(1, 1).build();
    ProposalCapsule proposalCapsule = new ProposalCapsule(proposal);
    boolean result = ProposalService.process(manager, proposalCapsule);
    Assert.assertTrue(result);
    //
    proposal = Proposal.newBuilder().putParameters(1000, 1).build();
    proposalCapsule = new ProposalCapsule(proposal);
    result = ProposalService.process(manager, proposalCapsule);
    Assert.assertFalse(result);
    //
    for (ProposalUtil.ProposalType proposalType : ProposalUtil.ProposalType.values()) {
      if (proposalType == ProposalUtil.ProposalType.WITNESS_127_PAY_PER_BLOCK) {
        proposal = Proposal.newBuilder().putParameters(proposalType.getCode(), 16160).build();
      } else {
        proposal = Proposal.newBuilder().putParameters(proposalType.getCode(), 1).build();
      }
      proposalCapsule = new ProposalCapsule(proposal);
      result = ProposalService.process(manager, proposalCapsule);
      Assert.assertTrue(result);
    }
  }


  @After
  public void removeDb() {
    Args.clearParam();
    context.destroy();
    FileUtil.deleteDir(new File(dbPath));
  }
}
