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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.AccountChanges;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Summarises where two block access lists disagree.
 *
 * <p>A BAL hash mismatch reports two 32-byte hashes, which establishes that the lists differ but
 * not how. The existing {@code --Xbal-log-bals-on-mismatch} switch dumps both lists in full, which
 * is off by default and can run to megabytes on a large block. This names the first entries that
 * differ so the mismatch is diagnosable straight from the error.
 */
public final class BlockAccessListDiff {

  /** Entries reported before the summary is truncated. */
  private static final int MAX_ENTRIES = 12;

  private BlockAccessListDiff() {}

  /**
   * Describes where the executed and supplied access lists differ.
   *
   * @param computed the access list built while executing the block
   * @param supplied the access list carried by the block, if any
   * @return a one-line summary, or a note that no summary is available
   */
  public static String describe(
      final BlockAccessList computed, final Optional<BlockAccessList> supplied) {
    if (supplied.isEmpty()) {
      return "no access list supplied with the block to compare against";
    }
    final Map<Address, AccountChanges> local = index(computed);
    final Map<Address, AccountChanges> remote = index(supplied.get());

    final StringJoiner out = new StringJoiner("; ");
    int reported = 0;

    for (final Address address : new TreeSet<>(local.keySet())) {
      if (reported >= MAX_ENTRIES) {
        return out + "; (truncated)";
      }
      if (!remote.containsKey(address)) {
        out.add("account " + address + " present locally but not in the block");
        reported++;
      }
    }
    for (final Address address : new TreeSet<>(remote.keySet())) {
      if (reported >= MAX_ENTRIES) {
        return out + "; (truncated)";
      }
      if (!local.containsKey(address)) {
        out.add("account " + address + " present in the block but not locally");
        reported++;
      }
    }
    for (final Address address : new TreeSet<>(local.keySet())) {
      final AccountChanges r = remote.get(address);
      if (r == null) {
        continue;
      }
      final AccountChanges l = local.get(address);
      reported += describeSlots(out, address, "storage_read", reads(l), reads(r), reported);
      reported += describeSlots(out, address, "storage_change", writes(l), writes(r), reported);
      reported += describeCount(out, address, "balance_changes", l.balanceChanges().size(), r.balanceChanges().size(), reported);
      reported += describeCount(out, address, "nonce_changes", l.nonceChanges().size(), r.nonceChanges().size(), reported);
      reported += describeCount(out, address, "code_changes", l.codeChanges().size(), r.codeChanges().size(), reported);
      if (reported >= MAX_ENTRIES) {
        return out + "; (truncated)";
      }
    }

    if (reported == 0) {
      return String.format(
          "no per-account difference found (local %d accounts, block %d); the lists differ in ordering or in per-transaction values",
          local.size(), remote.size());
    }
    return out.toString();
  }

  private static int describeSlots(
      final StringJoiner out,
      final Address address,
      final String kind,
      final Map<StorageSlotKey, ?> local,
      final Map<StorageSlotKey, ?> remote,
      final int reportedSoFar) {
    int added = 0;
    for (final StorageSlotKey slot : local.keySet()) {
      if (reportedSoFar + added >= MAX_ENTRIES) {
        return added;
      }
      if (!remote.containsKey(slot)) {
        out.add(
            "account " + address + ": " + kind + " for slot " + slot
                + " present locally but not in the block");
        added++;
      }
    }
    for (final StorageSlotKey slot : remote.keySet()) {
      if (reportedSoFar + added >= MAX_ENTRIES) {
        return added;
      }
      if (!local.containsKey(slot)) {
        out.add(
            "account " + address + ": " + kind + " for slot " + slot
                + " present in the block but never happened locally");
        added++;
      }
    }
    return added;
  }

  private static int describeCount(
      final StringJoiner out,
      final Address address,
      final String field,
      final int local,
      final int remote,
      final int reportedSoFar) {
    if (local == remote || reportedSoFar >= MAX_ENTRIES) {
      return 0;
    }
    out.add("account " + address + ": " + field + " local " + local + ", block " + remote);
    return 1;
  }

  private static Map<Address, AccountChanges> index(final BlockAccessList list) {
    final Map<Address, AccountChanges> out = new LinkedHashMap<>();
    for (final AccountChanges changes : list.accountChanges()) {
      out.put(changes.address(), changes);
    }
    return out;
  }

  private static Map<StorageSlotKey, ?> reads(final AccountChanges changes) {
    return changes.storageReads().stream()
        .collect(
            Collectors.toMap(
                BlockAccessList.SlotRead::slot, Function.identity(), (a, b) -> a, TreeMap::new));
  }

  private static Map<StorageSlotKey, ?> writes(final AccountChanges changes) {
    return changes.storageChanges().stream()
        .collect(
            Collectors.toMap(
                BlockAccessList.SlotChanges::slot, Function.identity(), (a, b) -> a, TreeMap::new));
  }
}
