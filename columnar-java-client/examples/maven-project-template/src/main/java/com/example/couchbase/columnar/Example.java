/*
 * Copyright 2024 Couchbase, Inc.
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

package com.example.couchbase.columnar;

import com.couchbase.columnar.client.java.Cluster;
import com.couchbase.columnar.client.java.Credential;
import com.couchbase.columnar.client.java.QueryResult;
import com.couchbase.columnar.client.java.internal.Certificates;

import static java.util.Collections.singletonList;

public class Example {
  public static void main(String[] args) {
    String connectionString = "couchbases://...";
    String username = "...";
    String password = "...";

    try (Cluster cluster = Cluster.newInstance(
      connectionString,
      Credential.of(username, password),
      clusterOptions -> clusterOptions
        // Configure a secure connection to Couchbase internal pre-production cluster.
        // (Not required when connecting to a production cluster!)
        .security(it -> it.trustOnlyCertificates(Certificates.getNonProdCertificates()))
    )) {

      // Buffered query. All rows must fit in memory.
      QueryResult result = cluster.executeQuery(
        "select ?=1",
        options -> options
          .readOnly(true)
          .parameters(singletonList(1))
      );
      result.rows().forEach(row -> System.out.println("Got row: " + row));

      // Alternatively --

      // Streaming query. Rows are processed one-by-one
      // as they arrive from server.
      cluster.executeStreamingQuery(
        "select ?=1",
        row -> System.out.println("Got row: " + row),
        options -> options
          .readOnly(true)
          .parameters(singletonList(1))
      );
    }
  }
}

