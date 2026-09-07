/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.mainnet.block.access.list;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.AccountChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.SlotRead;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

/**
 * A BAL hash mismatch reports two 32-byte hashes and nothing else. On a devnet a builder repeatedly
 * won slots with a BAL claiming a storage read execution never performed; the hash pair alone did
 * not say which entry was wrong.
 */
public class BlockAccessListDiffTest {

  private static final Address VICTIM =
      Address.fromHexString("0xba1ba1ba1ba1ba1ba1ba1ba1ba1ba1ba1ba1ba1b");
  private static final Address OTHER =
      Address.fromHexString("0x0000000000000000000000000000000000001234");

  private static AccountChanges account(final Address address, final long... readSlots) {
    final List<SlotRead> reads =
        java.util.Arrays.stream(readSlots)
            .mapToObj(s -> new SlotRead(new StorageSlotKey(UInt256.valueOf(s))))
            .toList();
    return new AccountChanges(address, List.of(), reads, List.of(), List.of(), List.of());
  }

  private static BlockAccessList bal(final AccountChanges... changes) {
    return new BlockAccessList(List.of(changes));
  }

  @Test
  public void namesAPhantomStorageRead() {
    final String diff =
        BlockAccessListDiff.describe(
            bal(account(VICTIM, 1)), Optional.of(bal(account(VICTIM, 1, 42))));

    assertThat(diff).contains(VICTIM.toHexString());
    assertThat(diff).contains("never happened locally");
  }

  @Test
  public void namesAReadTheBlockOmitted() {
    final String diff =
        BlockAccessListDiff.describe(bal(account(OTHER, 7)), Optional.of(bal(account(OTHER))));

    assertThat(diff).contains("present locally but not in the block");
  }

  @Test
  public void namesAccountsPresentOnOneSideOnly() {
    final String diff =
        BlockAccessListDiff.describe(bal(account(VICTIM)), Optional.of(bal(account(OTHER))));

    assertThat(diff).contains("present locally but not in the block");
    assertThat(diff).contains("present in the block but not locally");
  }

  @Test
  public void isHonestWhenItFindsNoDifference() {
    final String diff =
        BlockAccessListDiff.describe(bal(account(VICTIM, 1)), Optional.of(bal(account(VICTIM, 1))));

    assertThat(diff).contains("no per-account difference found");
  }

  @Test
  public void saysSoWhenTheBlockCarriedNoAccessList() {
    assertThat(BlockAccessListDiff.describe(bal(account(VICTIM, 1)), Optional.empty()))
        .contains("no access list supplied");
  }
}
