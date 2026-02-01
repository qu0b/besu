/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.core.encoding.receipt;

import org.hyperledger.besu.datatypes.LogsBloomFilter;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.receipt.FrontierTransactionReceiptDecoder.ReceiptComponents;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/**
 * Decoder for Amsterdam+ transaction receipts (EIP-7778).
 *
 * <p>EIP-7778 makes no receipt format changes. The gasSpent field was removed from the spec.
 * This decoder reads gasSpent optionally for backward compatibility with previously stored data.
 */
public class AmsterdamTransactionReceiptDecoder {

  /**
   * Creates a transaction receipt for the given RLP, expecting mandatory gasSpent (EIP-7778).
   *
   * @param rlpInput the RLP-encoded transaction receipt
   * @param revertReasonAllowed whether the rlp input is allowed to have a revert reason
   * @return the transaction receipt with gasSpent field populated
   */
  public static TransactionReceipt readFrom(
      final RLPInput rlpInput, final boolean revertReasonAllowed) {
    // The first byte indicates whether the receipt is typed (eth/68) or flat (eth/69).
    if (!rlpInput.nextIsList()) {
      return decodeTypedReceipt(rlpInput, revertReasonAllowed);
    } else {
      return decodeFlatReceipt(rlpInput, revertReasonAllowed);
    }
  }

  private static TransactionReceipt decodeTypedReceipt(
      final RLPInput rlpInput, final boolean revertReasonAllowed) {
    final ReceiptComponents components =
        FrontierTransactionReceiptDecoder.decodeTypedReceiptComponents(rlpInput);
    // EIP-7778: gasSpent removed from receipt RLP per spec update (no receipt format changes)
    // Read gasSpent only if present (backward compat with previously stored receipts)
    Optional<Long> gasSpent = Optional.empty();
    if (!components.input().isEndOfCurrentList()) {
      // Check if next item could be gasSpent (a scalar) vs revertReason (bytes)
      gasSpent = Optional.of(components.input().readLongScalar());
    }
    Optional<Bytes> revertReason =
        FrontierTransactionReceiptDecoder.readMaybeRevertReason(
            components.input(), revertReasonAllowed);
    components.input().leaveList();
    return FrontierTransactionReceiptDecoder.createReceipt(
        components, gasSpent, revertReason);
  }

  private static TransactionReceipt decodeFlatReceipt(
      final RLPInput rlpInput, final boolean revertReasonAllowed) {
    rlpInput.enterList();
    // Flat receipts can be either legacy or eth/69 receipts.
    final RLPInput firstElement = rlpInput.readAsRlp();
    final RLPInput secondElement = rlpInput.readAsRlp();
    final boolean isCompacted = FrontierTransactionReceiptDecoder.isNextNotBloomFilter(rlpInput);
    LogsBloomFilter bloomFilter = null;
    if (!isCompacted) {
      bloomFilter = LogsBloomFilter.readFrom(rlpInput);
    }
    // eth/69 receipts don't have gasSpent in the same format - for now, only handle legacy
    boolean isEth69Receipt = isCompacted && !rlpInput.nextIsList();
    TransactionReceipt receipt;
    if (isEth69Receipt) {
      // eth/69 format doesn't include gasSpent - throw or handle appropriately
      throw new IllegalStateException(
          "eth/69 receipt format is not supported for Amsterdam+ receipts with mandatory gasSpent");
    } else {
      receipt =
          decodeLegacyReceipt(
              rlpInput, firstElement, secondElement, bloomFilter, revertReasonAllowed);
    }
    rlpInput.leaveList();
    return receipt;
  }

  private static TransactionReceipt decodeLegacyReceipt(
      final RLPInput input,
      final RLPInput statusOrStateRootRlpInput,
      final RLPInput cumulativeGasRlpInput,
      final LogsBloomFilter bloomFilter,
      final boolean revertReasonAllowed) {
    final ReceiptComponents components =
        FrontierTransactionReceiptDecoder.decodeLegacyReceiptComponents(
            input, statusOrStateRootRlpInput, cumulativeGasRlpInput, bloomFilter);
    // EIP-7778: gasSpent removed from receipt RLP per spec update (no receipt format changes)
    Optional<Long> gasSpent = Optional.empty();
    if (!components.input().isEndOfCurrentList()) {
      gasSpent = Optional.of(components.input().readLongScalar());
    }
    Optional<Bytes> revertReason =
        FrontierTransactionReceiptDecoder.readMaybeRevertReason(
            components.input(), revertReasonAllowed);
    return FrontierTransactionReceiptDecoder.createReceipt(
        components, gasSpent, revertReason);
  }
}
