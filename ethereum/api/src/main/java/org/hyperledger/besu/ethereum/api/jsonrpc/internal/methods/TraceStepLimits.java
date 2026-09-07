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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.evm.tracing.OpCodeTracerConfigBuilder;

/**
 * Applies the operator-configured server-side opcode step ceiling ({@code --rpc-max-trace-steps}) to
 * caller-supplied trace options.
 *
 * <p>The tracers accumulate one {@code TraceFrame} per executed opcode in an unbounded list, so an
 * unclamped {@code debug_trace*} on a large block can retain millions of frames and exhaust the
 * heap. Every {@code debug_trace*} entry point must clamp before building a tracer.
 */
public final class TraceStepLimits {

  private TraceStepLimits() {}

  /**
   * Clamps the caller-supplied step limit to the operator-configured server ceiling.
   *
   * <p>If the server limit is 0 (operator opt-out) the caller's value is used as-is. If the caller
   * supplies 0 (unlimited) the server ceiling is applied. Otherwise the minimum of the two is used.
   *
   * @param traceOptions the caller-supplied trace options
   * @param serverStepLimit the operator-configured ceiling, or 0 to opt out
   * @return trace options whose opcode step limit respects the server ceiling
   */
  public static TraceOptions apply(final TraceOptions traceOptions, final long serverStepLimit) {
    if (serverStepLimit <= 0) {
      return traceOptions;
    }
    final int callerLimit = traceOptions.opCodeTracerConfig().limit();
    final int effectiveLimit =
        callerLimit > 0
            ? (int) Math.min(callerLimit, Math.min(serverStepLimit, Integer.MAX_VALUE))
            : (int) Math.min(serverStepLimit, Integer.MAX_VALUE);
    if (effectiveLimit == callerLimit) {
      return traceOptions;
    }
    final var newConfig =
        OpCodeTracerConfigBuilder.createFrom(traceOptions.opCodeTracerConfig())
            .limit(effectiveLimit)
            .build();
    return new TraceOptions(
        traceOptions.tracerType(),
        newConfig,
        traceOptions.tracerConfig(),
        traceOptions.stateOverrides());
  }

  /**
   * Reads the ceiling out of an {@link ApiConfiguration}, tolerating a null configuration the way
   * the existing trace-call constructors do.
   *
   * @param apiConfiguration the API configuration, may be null
   * @return the configured ceiling, or 0 when there is none
   */
  public static long serverStepLimit(final ApiConfiguration apiConfiguration) {
    return apiConfiguration != null ? apiConfiguration.getDebugTraceStepLimit() : 0L;
  }
}
