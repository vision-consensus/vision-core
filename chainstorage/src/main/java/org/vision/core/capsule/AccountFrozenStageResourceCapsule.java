package org.vision.core.capsule;

import lombok.extern.slf4j.Slf4j;
import org.vision.protos.Protocol.AccountFrozenStageResource;

@Slf4j(topic = "capsule")
public class AccountFrozenStageResourceCapsule implements ProtoCapsule<AccountFrozenStageResource> {

    private AccountFrozenStageResource accountFrozenStageResource;

    public AccountFrozenStageResourceCapsule(final AccountFrozenStageResource accountFrozenStageResource){
        this.accountFrozenStageResource = accountFrozenStageResource;
    }

    @Override
    public byte[] getData() {
        return this.accountFrozenStageResource.toByteArray();
    }

    @Override
    public AccountFrozenStageResource getInstance() {
        return this.accountFrozenStageResource;
    }
}
