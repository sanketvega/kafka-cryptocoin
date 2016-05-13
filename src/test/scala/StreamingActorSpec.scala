import java.time.Instant

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit}
import co.coinsmith.kafka.cryptocoin.streaming.BitstampStreamingActor
import net.manub.embeddedkafka.EmbeddedKafka
import org.json4s.JsonAST.{JDecimal, JInt, JString}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike}
import org.json4s.JsonDSL.WithBigDecimal._
import org.json4s.jackson.JsonMethods._

class BitstampStreamingActorSpec extends TestKit(ActorSystem("BitstampStreamingActorSpecSystem"))
  with FlatSpecLike with BeforeAndAfterAll with EmbeddedKafka {
  val actorRef = TestActorRef[BitstampStreamingActor]
  val actor = actorRef.underlyingActor

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "BitstampStreamingActor" should "process a trade message" in {
    val timeCollected = Instant.ofEpochSecond(10L)
    val json = ("price" -> 451.78) ~
      ("timestamp" -> "1463025517") ~
      ("amount" -> 0.17786403) ~
      ("type" -> 1) ~
      ("id" -> 11151677)
    val expected = ("time_collected" -> timeCollected.toString) ~
      ("price" -> 451.78) ~
      ("timestamp" -> "2016-05-12T03:58:37Z") ~
      ("volume" -> 0.17786403) ~
      ("type" -> 1) ~
      ("id" -> 11151677)
    withRunningKafka {
      actorRef ! ("live_trades", "trade", timeCollected, json)
      val msg = consumeFirstStringMessageFrom("stream_trades")
      val result = parse(msg, true)
      assert(result == expected)
    }
  }

  it should "process an orderbook message" in {
    val bids = List(
      List("452.50000000", "5.00000000"),
      List("452.07000000", "6.63710000"),
      List("452.00000000", "3.75000000")
    )
    val asks = List(
      List("452.97000000", "12.10000000"),
      List("452.98000000", "6.58530000"),
      List("453.00000000", "12.54279453")
    )
    val json = ("bids" -> bids) ~ ("asks" -> asks)

    val timeCollected = Instant.ofEpochSecond(10L)
    val bidsDecimal = List(
      List(BigDecimal("452.50000000"), BigDecimal("5.00000000")),
      List(BigDecimal("452.07000000"), BigDecimal("6.63710000")),
      List(BigDecimal("452.00000000"), BigDecimal("3.75000000"))
    )
    val asksDecimal = List(
      List(BigDecimal("452.97000000"), BigDecimal("12.10000000")),
      List(BigDecimal("452.98000000"), BigDecimal("6.58530000")),
      List(BigDecimal("453.00000000"), BigDecimal("12.54279453"))
    )
    val expected = ("time_collected" -> timeCollected.toString) ~
      ("bids" -> bidsDecimal) ~
      ("asks" -> asksDecimal)

    withRunningKafka {
      actorRef ! ("order_book", "data", timeCollected, json)
      val msg = consumeFirstStringMessageFrom("stream_orderbooks")
      val result = parse(msg, true)
      assert(result == expected)
    }
  }

  it should "process an orderbook diff message" in {
    val bids = List(
      List("451.89000000", "6.57270000"),
      List("451.84000000", "0")
    )
    val asks = List(
      List("453.32000000", "8.77550000"),
      List("453.68000000", "0.25324645"),
      List("458.90000000", "0")
    )
    val json = ("timestamp" -> "1463009242") ~
      ("bids" -> bids) ~
      ("asks" -> asks)

    val timeCollected = Instant.ofEpochSecond(10L)
    val bidsDecimal = List(
      List(BigDecimal("451.89000000"), BigDecimal("6.57270000")),
      List(BigDecimal("451.84000000"), BigDecimal("0"))
    )
    val asksDecimal = List(
      List(BigDecimal("453.32000000"), BigDecimal("8.77550000")),
      List(BigDecimal("453.68000000"), BigDecimal("0.25324645")),
      List(BigDecimal("458.90000000"), BigDecimal("0"))
    )
    val expected = ("time_collected" -> timeCollected.toString) ~
      ("timestamp" -> "2016-05-11T23:27:22Z") ~
      ("bids" -> bidsDecimal) ~
      ("asks" -> asksDecimal)

    withRunningKafka {
      actorRef ! ("diff_order_book", "data", timeCollected, json)
      val msg = consumeFirstStringMessageFrom("stream_orderbook_diffs")
      val result = parse(msg, true) transform {
        case JInt(v) => JDecimal(BigDecimal(v))
      }
      assert(result == expected)
    }
  }
}