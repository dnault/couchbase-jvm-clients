package com.couchbase.client.scala.query

import java.util.concurrent.atomic.AtomicReference

import com.couchbase.client.core.error.{DecodingFailedException, QueryServiceException, QueryStreamException}
import com.couchbase.client.scala.json.JsonObject
import com.couchbase.client.scala.{Cluster, TestUtils}
import org.scalatest.FunSuite
import reactor.core.scala.publisher.Flux

import scala.concurrent.duration._
import scala.util.{Failure, Success}

class QuerySpec extends FunSuite {

  val cluster = Cluster.connect("localhost", "Administrator", "password")
  val bucket = cluster.bucket("default")
  val coll = bucket.defaultCollection


  def getContent(docId: String): ujson.Obj = {
    coll.get(docId) match {
      case Success(result) =>
        result.contentAs[ujson.Obj] match {
          case Success(content) =>
            content
          case Failure(err) =>
            assert(false, s"unexpected error $err")
            null
        }
      case Failure(err) =>
        assert(false, s"unexpected error $err")
        null
    }
  }

  private def prepare(content: ujson.Value) = {
    val docId = TestUtils.docId()
    val insertResult = coll.insert(docId, content).get
    (docId, insertResult.mutationToken)
  }

  test("hello world") {
    cluster.query("""select 'hello world' as Greeting""") match {
      case Success(result) =>
        assert(result.rows.size == 1)
        val rows = result.allRowsAs[String].get
        assert(rows.head == """{"Greeting":"hello world"}""")
      case Failure(err) => throw err
    }
  }

  test("hello world content as JsonObject") {
    cluster.query("""select 'hello world 2' as Greeting""") match {
      case Success(result) =>
        assert(result.meta.clientContextId.isEmpty)
        assert(result.meta.requestId != null)
        assert(result.rows.size == 1)
        val rows = result.allRowsAs[JsonObject].get
        assert(rows.head.str("Greeting") == "hello world 2")
        val signature = result.meta.signature.get.contentAs[JsonObject].get
        assert(signature.size > 0)

        val out = result.meta
        assert(out.metrics.get.errorCount == 0)
        assert(out.metrics.get.warningCount == 0)
        assert(out.metrics.get.mutationCount == 0)
        assert(out.warnings.size == 0)
        assert(out.status == "success")
        assert(out.profile.isEmpty)

      case Failure(err) => throw err
    }
  }

  test("hello world content as JsonObject for-comp") {
    (for {
      result <- cluster.query("""select 'hello world 2' as Greeting""")
      rows <- result.allRowsAs[JsonObject]
    } yield (result, rows)) match {
      case Success((result, rows)) =>
        assert(result.rows.size == 1)
        val rows = result.allRowsAs[JsonObject].get
        assert(rows.head.str("Greeting") == "hello world 2")
        val signature = result.meta.signature.get.contentAs[JsonObject].get
        assert(signature.size > 0)

        val out = result.meta
        assert(out.metrics.get.errorCount == 0)
        assert(out.metrics.get.warningCount == 0)
        assert(out.metrics.get.mutationCount == 0)
        assert(out.warnings.size == 0)
        assert(out.status == "success")
        assert(out.profile.isEmpty)

      case Failure(err) => throw err
    }
  }

  test("hello world with quotes") {
    cluster.query("""select "hello world" as Greeting""") match {
      case Success(result) =>
        assert(result.rows.size == 1)
        val rows = result.allRowsAs[String].get
        assert(rows.head == """{"Greeting":"hello world"}""")
      case Failure(err) => throw err
    }
  }

  test("read 2 docs use keys") {
    val (docId1, _) = prepare(ujson.Obj("name" -> "Andy"))
    val (docId2, _) = prepare(ujson.Obj("name" -> "Beth"))

    val statement =s"""select name from default use keys ['$docId1', '$docId2'];"""
    //    val statement = s"""SELECT * FROM default USE KEYS '$docId1';"""
    cluster.query(statement) match {
      case Success(result) =>
        val rows = result.allRowsAs[ujson.Obj].get
        assert(rows.size == 2)
        assert(rows.head("name").str == """Andy""")
        assert(rows.last("name").str == """Beth""")
      case Failure(err) =>
        throw err
    }
  }

  test("error due to bad syntax") {
    val x = cluster.query("""select*from""")
    x match {
      case Success(result) =>
        assert(false)
      case Failure(err: QueryError) =>
        val msg = err.msg
        // TODO recheck after David's query changes are merged
//        assert(msg == "syntax error - at end of input")
//        assert(err.code == Success(3000))
      case Failure(err) =>
        throw err
    }
  }

  test("reactive hello world") {

    cluster.reactive.query("""select 'hello world' as Greeting""")
      .flatMap(result => {
        result.rowsAs[String].doOnNext(v => {
          println("GOT A ROW!!" + v)
        }).collectSeq()

          .doOnNext(rows => assert(rows.size == 1))

          .flatMap(_ => result.meta)

          .doOnNext(meta => {
            assert (meta.clientContextId.isEmpty)
          })
      })
      .block()
  }

  test("reactive additional") {

    val rowsKeeper = new AtomicReference[Seq[JsonObject]]()

    val out: QueryMeta = cluster.reactive.query("""select 'hello world' as Greeting""")
      .flatMapMany(result => {
        result.rowsAs[JsonObject]
          .collectSeq()
          .doOnNext(rows => {
            rowsKeeper.set(rows)
          })
          .flatMap(_ => result.meta)
      })
      .blockLast().get

    assert(rowsKeeper.get.size == 1)
    assert(out.metrics.get.errorCount == 0)
    assert(out.metrics.get.warningCount == 0)
    assert(out.metrics.get.mutationCount == 0)
    assert(out.warnings.size == 0)
    assert(out.status == "success")
    assert(out.profile.isEmpty)
  }

  test("reactive error due to bad syntax") {
    assertThrows[QueryServiceException](
      cluster.reactive.query("""sselect*from""")
        .flatMapMany(result => {
          result.rowsAs[String]
            .doOnNext(v => assert(false))
            .doOnError(err => println("expected ERR: " + err))
        })
        .blockLast())
  }

  test("options - profile") {
    cluster.query("""select 'hello world' as Greeting""", QueryOptions().profile(N1qlProfile.Timings)) match {
      case Success(result) =>
        assert(result.meta.profile.nonEmpty)
        val profile = result.meta.profile.get.contentAs[JsonObject].get
        assert(result.meta.profile.size > 0)
        assert(result.meta.clientContextId.isEmpty)
        assert(result.meta.requestId != null)
        assert(result.rows.size == 1)
        val rows = result.allRowsAs[JsonObject].get
        assert(rows.head.str("Greeting") == "hello world")

      case Failure(err) => throw err
    }
  }

  test("options - named params") {
    coll.insert(TestUtils.docId(), JsonObject("name" -> "Eric Wimp"))

    cluster.query(
      """select * from default where name=$nval""",
      QueryOptions().namedParameter("nval", "Eric Wimp")
    .scanConsistency(ScanConsistency.RequestPlus())) match {
      case Success(result) => assert(result.rows.size > 0)
      case Failure(err) => throw err
    }
  }

  test("options - positional params") {
    coll.insert(TestUtils.docId(), JsonObject("name" -> "Eric Wimp"))

    cluster.query(
      """select * from default where name=$1""",
      QueryOptions().positionalParameters("Eric Wimp")) match {
      case Success(result) => assert(result.rows.size > 0)
      case Failure(err) => throw err
    }
  }

  test("options - clientContextId") {
    cluster.query(
      """select 'hello world' as Greeting""",
      QueryOptions().clientContextId("test")) match {
      case Success(result) => assert(result.meta.clientContextId.contains("test"))
      case Failure(err) => throw err
    }
  }

  test("options - disableMetrics") {
    cluster.query(
      """select 'hello world' as Greeting""",
      QueryOptions().disableMetrics(true)) match {
      case Success(result) =>
        assert(result.meta.metrics.get.errorCount == 0)
      case Failure(err) => throw err
    }
  }

  // Can't really test these so just make sure the server doesn't barf on our encodings
  test("options - unusual") {
    cluster.query(
      """select 'hello world' as Greeting""",
      QueryOptions().maxParallelism(5)
        .pipelineCap(3)
        .pipelineBatch(6)
        .scanCap(8)
        .serverSideTimeout(30.seconds)
        .timeout(30.seconds)
        .readonly(true)) match {
      case Success(result) =>
        assert(result.rows.size == 1)
      case Failure(err) => throw err
    }
  }


}
