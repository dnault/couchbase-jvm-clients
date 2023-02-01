/*
 * Copyright (c) 2023 Couchbase, Inc.
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
package com.couchbase.client.scala.util

import com.couchbase.client.core.api.kv._
import com.couchbase.client.core.api.query.{
  CoreQueryMetaData,
  CoreQueryMetrics,
  CoreQueryResult,
  CoreQueryStatus,
  CoreReactiveQueryResult
}
import com.couchbase.client.core.cnc.RequestSpan
import com.couchbase.client.core.endpoint.http.CoreCommonOptions
import com.couchbase.client.core.msg.kv.{DurabilityLevel, MutationToken}
import com.couchbase.client.core.retry.RetryStrategy
import com.couchbase.client.core.service.kv.Observe.{ObservePersistTo, ObserveReplicateTo}
import com.couchbase.client.scala.codec._
import com.couchbase.client.scala.durability.Durability
import com.couchbase.client.scala.env.ClusterEnvironment
import com.couchbase.client.scala.kv._
import com.couchbase.client.scala.query.{
  QueryMetaData,
  QueryMetrics,
  QueryResult,
  QueryStatus,
  QueryWarning,
  ReactiveQueryResult
}
import com.couchbase.client.scala.util.DurationConversions._
import reactor.core.publisher.{Flux, Mono}
import reactor.core.scala.publisher.{SFlux, SMono}

import java.util.function.Supplier
import scala.compat.java8.OptionConverters._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

private[scala] object CoreCommonConverters {
  type HasCommonOptions = {
    val timeout: Duration
    val parentSpan: Option[RequestSpan]
    val retryStrategy: Option[RetryStrategy]
  }

  def convert[T <: HasCommonOptions](options: T): CoreCommonOptions = {
    CoreCommonOptions.of(
      if (options.timeout == Duration.MinusInf) null
      else java.time.Duration.ofNanos(options.timeout.toNanos),
      options.retryStrategy.orNull,
      options.parentSpan.orNull
    )
  }

  def makeCommonOptions(
      timeout: Duration,
      retryStrategy: RetryStrategy = null
  ): CoreCommonOptions = {
    CoreCommonOptions.of(
      if (timeout == Duration.MinusInf) null
      else java.time.Duration.ofNanos(timeout.toNanos),
      retryStrategy,
      null
    )
  }

  def convert(
      in: CoreGetResult,
      env: ClusterEnvironment,
      transcoder: Option[Transcoder]
  ): GetResult = {
    GetResult(
      in.key(),
      Left(in.content()),
      in.flags(),
      in.cas(),
      Option(in.expiry()),
      transcoder.getOrElse(env.transcoder)
    )
  }

  def convertReplica(
      in: CoreGetResult,
      env: ClusterEnvironment,
      transcoder: Option[Transcoder]
  ): GetReplicaResult = {
    new GetReplicaResult(
      in.key(),
      Left(in.content()),
      in.flags(),
      in.cas(),
      Option(in.expiry()),
      in.replica(),
      transcoder.getOrElse(env.transcoder)
    )
  }

  def convert(in: CoreMutationResult): MutationResult = {
    MutationResult(
      in.cas(),
      in.mutationToken()
        .asScala
        .map(
          mt =>
            new MutationToken(mt.partitionID, mt.partitionUUID, mt.sequenceNumber, mt.bucketName)
        )
    )
  }

  def convert(in: CoreSubdocMutateResult): MutateInResult = {
    MutateInResult(
      in.key(),
      in,
      in.cas(),
      in.mutationToken()
        .asScala
        .map(
          mt =>
            new MutationToken(mt.partitionID, mt.partitionUUID, mt.sequenceNumber, mt.bucketName)
        )
    )
  }

  def convert(in: CoreExistsResult): ExistsResult = {
    ExistsResult(in.exists(), in.cas())
  }

  def convert(in: CoreQueryResult): QueryResult = {
    QueryResult(
      in.collectRows().asScala.toSeq, // toSeq for 2.13
      convert(in.metaData)
    )
  }

  def convert(in: CoreReactiveQueryResult): ReactiveQueryResult = {
    ReactiveQueryResult(
      FutureConversions.javaFluxToScalaFlux(in.rows),
      FutureConversions
        .javaMonoToScalaMono(in.metaData)
        .map(md => convert(md))
    )
  }

  def convert(in: CoreQueryMetaData): QueryMetaData = {
    QueryMetaData(
      in.requestId,
      in.clientContextId,
      in.signature.asScala,
      in.metrics.asScala.map(v => convert(v)),
      in.warnings.asScala.map(v => QueryWarning(v.code, v.message)),
      convert(in.status),
      in.profile.asScala
    )
  }

  def convert(in: CoreQueryMetrics): QueryMetrics = {
    QueryMetrics(
      in.elapsedTime,
      in.executionTime,
      in.resultCount,
      in.resultSize,
      in.mutationCount,
      in.sortCount,
      in.errorCount,
      in.warningCount
    )
  }

  def convert(in: CoreQueryStatus): QueryStatus = {
    in match {
      case CoreQueryStatus.RUNNING   => QueryStatus.Running
      case CoreQueryStatus.SUCCESS   => QueryStatus.Success
      case CoreQueryStatus.ERRORS    => QueryStatus.Errors
      case CoreQueryStatus.COMPLETED => QueryStatus.Completed
      case CoreQueryStatus.STOPPED   => QueryStatus.Stopped
      case CoreQueryStatus.TIMEOUT   => QueryStatus.Timeout
      case CoreQueryStatus.CLOSED    => QueryStatus.Closed
      case CoreQueryStatus.FATAL     => QueryStatus.Fatal
      case CoreQueryStatus.ABORTED   => QueryStatus.Aborted
      case CoreQueryStatus.UNKNOWN   => QueryStatus.Unknown
    }
  }

  def convert[T](in: CoreAsyncResponse[T]): Future[T] = {
    FutureConversions.javaCFToScalaFuture(in.toFuture)
  }

  def convert[T](in: Mono[T]): SMono[T] = {
    FutureConversions.javaMonoToScalaMono(in)
  }

  def convert[T](in: Flux[T]): SFlux[T] = {
    FutureConversions.javaFluxToScalaFlux(in)
  }

  def convert(in: Durability): CoreDurability = {
    in match {
      case Durability.Disabled => CoreDurability.NONE
      case Durability.ClientVerified(replicateTo, persistTo) =>
        CoreDurability.of(
          persistTo match {
            case com.couchbase.client.scala.durability.PersistTo.None  => ObservePersistTo.NONE
            case com.couchbase.client.scala.durability.PersistTo.One   => ObservePersistTo.ONE
            case com.couchbase.client.scala.durability.PersistTo.Two   => ObservePersistTo.TWO
            case com.couchbase.client.scala.durability.PersistTo.Three => ObservePersistTo.THREE
          },
          replicateTo match {
            case com.couchbase.client.scala.durability.ReplicateTo.None  => ObserveReplicateTo.NONE
            case com.couchbase.client.scala.durability.ReplicateTo.One   => ObserveReplicateTo.ONE
            case com.couchbase.client.scala.durability.ReplicateTo.Two   => ObserveReplicateTo.TWO
            case com.couchbase.client.scala.durability.ReplicateTo.Three => ObserveReplicateTo.THREE
          }
        )
      case Durability.Majority => CoreDurability.of(DurabilityLevel.MAJORITY)
      case Durability.MajorityAndPersistToActive =>
        CoreDurability.of(DurabilityLevel.MAJORITY_AND_PERSIST_TO_ACTIVE)
      case Durability.PersistToMajority => CoreDurability.of(DurabilityLevel.PERSIST_TO_MAJORITY)
    }
  }

  def convertExpiry(in: Duration): Long = {
    in.toSeconds
  }

  def convert(in: Duration): java.time.Duration = {
    java.time.Duration.ofMillis(in.toMillis)
  }

  def convert(in: StoreSemantics): CoreStoreSemantics = {
    in match {
      case StoreSemantics.Replace => CoreStoreSemantics.REPLACE
      case StoreSemantics.Insert  => CoreStoreSemantics.INSERT
      case StoreSemantics.Upsert  => CoreStoreSemantics.UPSERT
    }
  }

  def encoder[T](
      transcoder: Transcoder,
      serializer: JsonSerializer[T],
      content: T
  ): Supplier[CoreEncodedContent] = { () =>
    {
      val value: EncodedValue = (transcoder match {
        case x: TranscoderWithSerializer    => x.encode(content, serializer)
        case x: TranscoderWithoutSerializer => x.encode(content)
      }).get

      new CoreEncodedContent {
        override def encoded(): Array[Byte] = value.encoded

        override def flags(): Int = value.flags
      }
    }
  }
}