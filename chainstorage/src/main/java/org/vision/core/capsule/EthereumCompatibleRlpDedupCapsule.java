/*
 * vision-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * vision-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.vision.core.capsule;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.Sha256Hash;

import java.util.Arrays;

@Slf4j(topic = "capsule")
public class EthereumCompatibleRlpDedupCapsule implements ProtoCapsule<byte[]> {

  @Getter
  @Setter
  private byte[] rlpDataKey;

  @Getter
  private byte[] rlpDataValue;

  public EthereumCompatibleRlpDedupCapsule(EthereumCompatibleRlpDedupCapsule ethRlpCapsule){
    this.rlpDataKey = ethRlpCapsule.rlpDataKey;
    this.rlpDataValue = ethRlpCapsule.rlpDataValue;
  }

  public EthereumCompatibleRlpDedupCapsule(byte[] rlpDataKey, byte[] rlpDataValue){
    this.rlpDataKey = rlpDataKey;
    this.rlpDataValue = rlpDataValue;
  }

  public EthereumCompatibleRlpDedupCapsule(byte[] rlpDataValue) {
    this.rlpDataValue = rlpDataValue;
  }

  public Sha256Hash getHash() {
    return Sha256Hash.of(CommonParameter.getInstance().isECKeyCryptoEngine(),
            this.rlpDataValue);
  }

  public byte[] getValue() {
    return this.rlpDataValue;
  }

  public void setValue(byte[] value) {
    this.rlpDataValue = value;
  }

  @Override
  public byte[] getData() {
    return this.rlpDataValue;
  }

  @Override
  public byte[] getInstance() {
    return this.rlpDataValue;
  }

  @Override
  public String toString() {
    return Arrays.toString(rlpDataValue);
  }
}
