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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.ImmutableApiConfiguration;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.evm.tracing.OpCodeTracerConfigBuilder;

import org.junit.jupiter.api.Test;

/**
 * The block-level debug_trace* paths accumulate one TraceFrame per executed opcode in an unbounded
 * list. On a high-gas chain that is enough to exhaust the heap, so the operator ceiling
 * (--rpc-max-trace-steps) has to reach them, not only the *_call paths.
 */
public class BlockTraceStepLimitTest {

  private static final ApiConfiguration API_CONFIGURATION =
      ImmutableApiConfiguration.builder().build();

  @Test
  public void serverCeilingIsReadFromApiConfiguration() {
    assertThat(TraceStepLimits.serverStepLimit(API_CONFIGURATION))
        .isEqualTo(ApiConfiguration.DEFAULT_DEBUG_TRACE_STEP_LIMIT);
    assertThat(TraceStepLimits.serverStepLimit(null)).isZero();
  }

  @Test
  public void unlimitedCallerRequestIsClampedToTheServerCeiling() {
    final TraceOptions unlimited = TraceOptions.DEFAULT;
    assertThat(unlimited.opCodeTracerConfig().limit()).isZero();

    final TraceOptions clamped =
        TraceStepLimits.apply(unlimited, TraceStepLimits.serverStepLimit(API_CONFIGURATION));

    assertThat(clamped.opCodeTracerConfig().limit())
        .isEqualTo((int) ApiConfiguration.DEFAULT_DEBUG_TRACE_STEP_LIMIT);
  }

  @Test
  public void aStricterCallerLimitIsKept() {
    final TraceOptions callerLimited =
        new TraceOptions(
            TraceOptions.DEFAULT.tracerType(),
            OpCodeTracerConfigBuilder.createFrom(TraceOptions.DEFAULT.opCodeTracerConfig())
                .limit(25)
                .build(),
            TraceOptions.DEFAULT.tracerConfig(),
            TraceOptions.DEFAULT.stateOverrides());

    final TraceOptions clamped =
        TraceStepLimits.apply(callerLimited, TraceStepLimits.serverStepLimit(API_CONFIGURATION));

    assertThat(clamped.opCodeTracerConfig().limit()).isEqualTo(25);
  }

  @Test
  public void anOperatorOptOutLeavesTheCallerRequestAlone() {
    assertThat(TraceStepLimits.apply(TraceOptions.DEFAULT, 0L).opCodeTracerConfig().limit())
        .isZero();
  }

  @Test
  public void debugTraceBlockByHashClampsAnUnlimitedRequest() {
    final DebugTraceBlockByHash method =
        new DebugTraceBlockByHash(null, null, API_CONFIGURATION);

    final TraceOptions effective = method.getTraceOptions(traceRequestWithoutOptions());

    assertThat(effective.opCodeTracerConfig().limit())
        .isEqualTo((int) ApiConfiguration.DEFAULT_DEBUG_TRACE_STEP_LIMIT);
  }

  @Test
  public void debugTraceBlockByHashWithoutAnApiConfigurationIsUnbounded() {
    final DebugTraceBlockByHash method = new DebugTraceBlockByHash(null, null);

    final TraceOptions effective = method.getTraceOptions(traceRequestWithoutOptions());

    assertThat(effective.opCodeTracerConfig().limit()).isZero();
  }

  private static org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext
      traceRequestWithoutOptions() {
    return new org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext(
        new org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest(
            "2.0", "debug_traceBlockByHash", new Object[] {"0x00"}));
  }
}
