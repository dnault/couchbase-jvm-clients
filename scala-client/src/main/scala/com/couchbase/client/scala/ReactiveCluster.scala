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

package com.couchbase.client.scala

import com.couchbase.client.core.env.Credentials
import com.couchbase.client.core.error.QueryServiceException
import com.couchbase.client.scala.env.ClusterEnvironment
import com.couchbase.client.scala.query._
import com.couchbase.client.scala.util.FutureConversions
import reactor.core.publisher.{Mono => JavaMono}
import reactor.core.scala.publisher.{Mono, Flux => ScalaFlux, Mono => ScalaMono}

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/** Represents a connection to a Couchbase cluster.
  *
  * This is the reactive version of the [[Cluster]] API.
  *
  * These can be created through the functions in the companion object, or through [[Cluster.reactive]].
  *
  * @param async an asynchronous version of this API
  * @param ec    an ExecutionContext to use for any Future.  Will be supplied automatically as long as resources are
  *              opened in the normal way, starting from functions in [[Cluster]]
  *
  * @author Graham Pople
  * @since 1.0.0
  */
class ReactiveCluster(val async: AsyncCluster)
                     (implicit ec: ExecutionContext) {
  private val env = async.env

  /** Performs a N1QL query against the cluster.
    *
    * This is a reactive API.  See [[Cluster.async]] for an asynchronous version of this API, and
    * [[Cluster]] for a blocking version.
    *
    * @param statement the N1QL statement to execute
    * @param options   any query options - see [[QueryOptions]] for documentation
    *
    * @return a `Mono` containing a [[ReactiveQueryResult]] which includes a Flux giving streaming access to any
    *         returned rows
    * */
  def query(statement: String, options: QueryOptions = QueryOptions()): ScalaMono[ReactiveQueryResult] = {
    async.queryHandler.request(statement, options, async.core, async.env) match {
      case Success(request) =>

        async.core.send(request)

        val ret: JavaMono[ReactiveQueryResult] = FutureConversions.javaCFToJavaMono(request, request.response(), false)
          .map(response => {
            val rows: ScalaFlux[QueryRow] = FutureConversions.javaFluxToScalaFlux(response.rows())
              .map[QueryRow](bytes => {
              QueryRow(bytes)
            }).onErrorResume(err => {
              err match {
                case e: QueryServiceException => ScalaMono.error(QueryError(e.content))
                case _ => ScalaMono.error(err)
              }
            })

            val additional: ScalaMono[QueryAdditional] = FutureConversions.javaMonoToScalaMono(response.additional())
              .map(addl => {
                QueryAdditional(QueryMetrics.fromBytes(addl.metrics),
                  addl.warnings.asScala.map(QueryError),
                  addl.status,
                  addl.profile.asScala.map(v => QueryProfile(v))
                )
              })

            ReactiveQueryResult(
              rows,
              response.requestId(),
              response.clientContextId().asScala,
              QuerySignature(response.signature().asScala),
              additional
            )
          })

        FutureConversions.javaMonoToScalaMono(ret)

      case Failure(err) =>
        ScalaMono.error(err)
    }
  }

  /** Opens and returns a Couchbase bucket resource that exists on this cluster.
    *
    * @param name the name of the bucket to open
    */
  def bucket(name: String): Mono[ReactiveBucket] = {
    Mono.fromFuture(async.bucket(name)).map(v => new ReactiveBucket(v))
  }

  /** Shutdown all cluster resources.
    *
    * This should be called before application exit.
    */
  def shutdown(): Mono[Unit] = {
    Mono.fromFuture(async.shutdown())
  }
}

/** Functions to allow creating a `ReactiveCluster`, which represents a connection to a Couchbase cluster.
  *
  * @define DeferredErrors Note that during opening of resources, all errors will be deferred until the first
  *                        attempted operation.
  */
object ReactiveCluster {
  private implicit val ec = Cluster.ec

  /**
    * Connect to a Couchbase cluster with a username and a password as credentials.
    *
    * $DeferredErrors
    *
    * @param connectionString connection string used to locate the Couchbase cluster.
    * @param username         the name of a user with appropriate permissions on the cluster.
    * @param password         the password of a user with appropriate permissions on the cluster.
    * @return a Mono[ReactiveCluster] representing a connection to the cluster
    */
  def connect(connectionString: String, username: String, password: String): Mono[ReactiveCluster] = {
    Mono.fromFuture(AsyncCluster.connect(connectionString, username, password)
      .map(cluster => new ReactiveCluster(cluster)))
  }

  /**
    * Connect to a Couchbase cluster with custom [[Credentials]].
    *
    * $DeferredErrors
    *
    * @param connectionString connection string used to locate the Couchbase cluster.
    * @param credentials      custom credentials used when connecting to the cluster.
    * @return a Mono[ReactiveCluster] representing a connection to the cluster
    */
  def connect(connectionString: String, credentials: Credentials): Mono[ReactiveCluster] = {
    Mono.fromFuture(AsyncCluster.connect(connectionString, credentials)
      .map(cluster => new ReactiveCluster(cluster)))
  }

  /**
    * Connect to a Couchbase cluster with a custom [[ClusterEnvironment]].
    *
    * $DeferredErrors
    *
    * @param environment the custom environment with its properties used to connect to the cluster.
    *
    * @return a Mono[ReactiveCluster] representing a connection to the cluster
    */
  def connect(environment: ClusterEnvironment): Mono[ReactiveCluster] = {
    Mono.fromFuture(AsyncCluster.connect(environment)
      .map(cluster => new ReactiveCluster(cluster)))
  }
}