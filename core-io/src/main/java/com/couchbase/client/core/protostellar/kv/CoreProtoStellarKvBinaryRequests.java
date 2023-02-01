/*
 * Copyright 2023 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.couchbase.client.core.protostellar.kv;

import static com.couchbase.client.core.api.kv.CoreKvBinaryParamValidators.validateAppendPrependArgs;
import static com.couchbase.client.core.api.kv.CoreKvBinaryParamValidators.validateIncrementDecrementArgs;
import static com.couchbase.client.core.protostellar.CoreProtostellarUtil.createSpan;

import java.time.Duration;
import java.util.Optional;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.CoreKeyspace;
import com.couchbase.client.core.annotation.Stability;
import com.couchbase.client.core.api.kv.CoreDurability;
import com.couchbase.client.core.cnc.TracingIdentifiers;
import com.couchbase.client.core.deps.com.google.protobuf.ByteString;
import com.couchbase.client.core.endpoint.http.CoreCommonOptions;
import com.couchbase.client.core.protostellar.CoreProtostellarUtil;
import com.couchbase.client.core.protostellar.ProtostellarKeyValueRequest;
import com.couchbase.client.core.protostellar.ProtostellarRequest;
import com.couchbase.client.protostellar.kv.v1.AppendRequest;
import com.couchbase.client.protostellar.kv.v1.DecrementRequest;
import com.couchbase.client.protostellar.kv.v1.IncrementRequest;
import com.couchbase.client.protostellar.kv.v1.PrependRequest;

@Stability.Internal
public class CoreProtoStellarKvBinaryRequests {

  /**
   * For creating Protostellar GRPC requests.
   */

  public static ProtostellarRequest<AppendRequest> appendRequest(Core core, String key, CoreKeyspace keyspace,
      CoreCommonOptions opts, byte[] content, long cas, CoreDurability durability) {
    validateAppendPrependArgs(core, key, keyspace, opts, content, cas, durability);
    Duration timeout =  CoreProtostellarUtil.kvDurableTimeout(opts.timeout(), durability, core);

    ProtostellarRequest<AppendRequest> out = new ProtostellarKeyValueRequest<>(core,
      keyspace,
      key,
      durability,
      TracingIdentifiers.SPAN_REQUEST_KV_APPEND,
      createSpan(core, TracingIdentifiers.SPAN_REQUEST_KV_APPEND, durability, opts.parentSpan().orElse(null)),
      timeout,
      false,
      opts.retryStrategy().orElse(core.context().environment().retryStrategy()),
      opts.clientContext());

    AppendRequest.Builder request = com.couchbase.client.protostellar.kv.v1.AppendRequest.newBuilder()
      .setBucketName(keyspace.bucket())
      .setScopeName(keyspace.scope())
      .setCollectionName(keyspace.collection())
      .setKey(key)
      .setContent(ByteString.copyFrom(content))
      .setCas(cas);

    if (!durability.isNone()) {
      request.setDurabilityLevel(CoreProtostellarUtil.convert(durability));
    }

    return out.request(request.build());
  }

  public static ProtostellarRequest<PrependRequest> prependRequest(Core core, String key, CoreKeyspace keyspace,
      CoreCommonOptions opts, byte[] content, long cas, CoreDurability durability) {

    validateAppendPrependArgs(core, key, keyspace, opts, content, cas, durability);
    Duration timeout =  CoreProtostellarUtil.kvDurableTimeout(opts.timeout(), durability, core);

    ProtostellarRequest<PrependRequest> out = new ProtostellarKeyValueRequest<>(core,
      keyspace,
      key,
      durability,
      TracingIdentifiers.SPAN_REQUEST_KV_PREPEND,
      createSpan(core, TracingIdentifiers.SPAN_REQUEST_KV_PREPEND, durability, opts.parentSpan().orElse(null)),
      timeout,
      false,
      opts.retryStrategy().orElse(core.context().environment().retryStrategy()),
      opts.clientContext());

    PrependRequest.Builder request = com.couchbase.client.protostellar.kv.v1.PrependRequest.newBuilder()
      .setBucketName(keyspace.bucket())
      .setScopeName(keyspace.scope())
      .setCollectionName(keyspace.collection())
      .setKey(key)
      .setContent(ByteString.copyFrom(content))
      .setCas(cas);

    if (!durability.isNone()) {
      request.setDurabilityLevel(CoreProtostellarUtil.convert(durability));
    }

    return out.request(request.build());
  }

  public static ProtostellarRequest<IncrementRequest> incrementRequest(Core core, String key, CoreKeyspace keyspace,
      CoreCommonOptions opts, long expiry, long delta, Optional<Long> initial, CoreDurability durability) {

    validateIncrementDecrementArgs(core, key, keyspace, opts, expiry,delta,initial , durability);
    Duration timeout = CoreProtostellarUtil.kvDurableTimeout(opts.timeout(), durability, core);

    ProtostellarRequest<IncrementRequest> out = new ProtostellarKeyValueRequest<>(core,
      keyspace,
      key,
      durability,
      TracingIdentifiers.SPAN_REQUEST_KV_INCREMENT,
      createSpan(core, TracingIdentifiers.SPAN_REQUEST_KV_INCREMENT, durability, opts.parentSpan().orElse(null)),
      timeout,
      false,
      opts.retryStrategy().orElse(core.context().environment().retryStrategy()),
      opts.clientContext());

    IncrementRequest.Builder request = com.couchbase.client.protostellar.kv.v1.IncrementRequest.newBuilder()
        .setBucketName(keyspace.bucket()).setScopeName(keyspace.scope()).setCollectionName(keyspace.collection())
        .setKey(key).setDelta(delta).setInitial(initial.orElse(0L));

    if (expiry != 0) {
      request.setExpiry(CoreProtostellarUtil.convertExpiry(expiry));
    }
    if (!durability.isNone()) {
      request.setDurabilityLevel(CoreProtostellarUtil.convert(durability));
    }

    return out.request(request.build());
  }

  public static ProtostellarRequest<DecrementRequest> decrementRequest(Core core, String key, CoreKeyspace keyspace,
      CoreCommonOptions opts, long expiry, long delta, Optional<Long> initial, CoreDurability durability) {

    validateIncrementDecrementArgs(core, key, keyspace, opts, expiry,delta,initial , durability);
    Duration timeout =  CoreProtostellarUtil.kvDurableTimeout(opts.timeout(), durability, core);

    ProtostellarRequest<DecrementRequest> out = new ProtostellarKeyValueRequest<>(core,
      keyspace,
      key,
      durability,
      TracingIdentifiers.SPAN_REQUEST_KV_DECREMENT,
      createSpan(core, TracingIdentifiers.SPAN_REQUEST_KV_DECREMENT, durability, opts.parentSpan().orElse(null)),
      timeout,
      false,
      opts.retryStrategy().orElse(core.context().environment().retryStrategy()),
      opts.clientContext());

    DecrementRequest.Builder request = com.couchbase.client.protostellar.kv.v1.DecrementRequest.newBuilder()
      .setBucketName(keyspace.bucket())
      .setScopeName(keyspace.scope())
      .setCollectionName(keyspace.collection())
      .setKey(key)
      .setDelta(delta)
      .setInitial(initial.orElse(0L));

    if (expiry != 0) {
      request.setExpiry(CoreProtostellarUtil.convertExpiry(expiry));
    }
    if (!durability.isNone()) {
      request.setDurabilityLevel(CoreProtostellarUtil.convert(durability));
    }

    return out.request(request.build());
  }

}