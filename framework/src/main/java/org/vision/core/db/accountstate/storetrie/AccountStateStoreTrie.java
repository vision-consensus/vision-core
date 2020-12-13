package org.vision.core.db.accountstate.storetrie;

import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.core.trie.TrieImpl;
import org.vision.common.crypto.Hash;
import org.vision.core.capsule.BytesCapsule;
import org.vision.core.db.VisionStoreWithRevoking;
import org.vision.core.db.accountstate.AccountStateEntity;
import org.vision.core.db.accountstate.TrieService;
import org.vision.core.db2.common.DB;

@Slf4j(topic = "AccountState")
@Component
public class AccountStateStoreTrie extends VisionStoreWithRevoking<BytesCapsule> implements
    DB<byte[], BytesCapsule> {

  @Autowired
  private TrieService trieService;

  @Autowired
  private AccountStateStoreTrie(@Value("accountTrie") String dbName) {
    super(dbName);
  }

  @PostConstruct
  public void init() {
    trieService.setAccountStateStoreTrie(this);
  }

  public AccountStateEntity getAccount(byte[] key) {
    return getAccount(key, trieService.getFullAccountStateRootHash());
  }

  public AccountStateEntity getAccount(byte[] key, byte[] rootHash) {
    TrieImpl trie = new TrieImpl(this, rootHash);
    byte[] value = trie.get(Hash.encodeElement(key));
    return ArrayUtils.isEmpty(value) ? null : AccountStateEntity.parse(value);
  }

  public AccountStateEntity getSolidityAccount(byte[] key) {
    return getAccount(key, trieService.getSolidityAccountStateRootHash());
  }

  @Override
  public boolean isEmpty() {
    return super.size() <= 0;
  }

  @Override
  public void remove(byte[] bytes) {
    super.delete(bytes);
  }

  @Override
  public BytesCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  @Override
  public void put(byte[] key, BytesCapsule item) {
    super.put(key, item);
  }

  @Override
  public DB<byte[], BytesCapsule> newInstance() {
    return null;
  }
}
