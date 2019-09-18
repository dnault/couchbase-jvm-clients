/*
 * Copyright (c) 2019 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.couchbase.client.java;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.CoreContext;
import com.couchbase.client.core.env.Authenticator;
import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.env.ClusterEnvironment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the basic functionality of the async bucket.
 */
class AsyncBucketTest {

  @Test
  void preventsDefaultScopeFromBeingOpened() {
    Core core = mock(Core.class);
    when(core.context()).thenReturn(new CoreContext(core, 1, null, mock(Authenticator.class)));
    AsyncBucket bucket = new AsyncBucket("bar", core, mock(ClusterEnvironment.class));
    assertThrows(IllegalArgumentException.class, () -> bucket.scope(CollectionIdentifier.DEFAULT_SCOPE));
  }

  @Test
  void shouldNotAcceptEmptyOrNullScopeName() {
    Core core = mock(Core.class);
    when(core.context()).thenReturn(new CoreContext(core, 1, null, mock(Authenticator.class)));
    AsyncBucket bucket = new AsyncBucket("bar", core, mock(ClusterEnvironment.class));
    assertThrows(IllegalArgumentException.class, () -> bucket.scope(null));
    assertThrows(IllegalArgumentException.class, () -> bucket.scope(""));
  }

}