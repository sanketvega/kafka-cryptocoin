package streaming

import java.time.Instant

import akka.actor.ActorSystem
import akka.testkit.TestActorRef
import co.coinsmith.kafka.cryptocoin.{Order, OrderBook, Tick, Trade}
import co.coinsmith.kafka.cryptocoin.streaming.{Data, OKCoinWebsocketProtocol}
import org.json4s.JsonAST.JArray
import org.json4s.JsonDSL.WithBigDecimal._


class OKCoinWebsocketProtocolSpec extends ExchangeProtocolActorSpec(ActorSystem("OKCoinWebsocketProtocolSpecSystem")) {
  val actorRef = TestActorRef[OKCoinWebsocketProtocol]

  "OKCoinWebsocketProtocol" should "process a ticker message" in {
    val timeCollected = Instant.ofEpochSecond(10L)
    val timestamp = 1463444493398L
    val json = ("buy" -> "2984.41") ~
      ("high" -> "3004.07") ~
      ("last" -> "2984.40") ~
      ("low" -> "2981.0") ~
      ("sell" -> "2984.42") ~
      ("timestamp" -> timestamp.toString) ~
      ("vol" -> "639,976.04")
    val data = Data(timeCollected, "ok_sub_spotcny_btc_ticker", json)

    val expected = Tick(
      2984.40, 2984.41, 2984.42, timeCollected,
      Some(3004.07), Some(2981.0), None,
      volume = Some(639976.04),
      timestamp = Some(Instant.ofEpochMilli(timestamp))
    )

    actorRef ! data
    expectMsg(("ticks", Tick.format.to(expected)))
  }

  it should "process an orderbook message" in {
    val timeCollected = Instant.ofEpochSecond(10L)
    val json = ("bids" -> List(
      List(3841.52, 0.372),
      List(3841.46, 0.548),
      List(3841.4, 0.812)
    )) ~ ("asks" -> List(
      List(3844.75, 0.04),
      List(3844.71, 5.181),
      List(3844.63, 3.143)
    )) ~ ("timestamp" -> "1465496881515")
    val data = Data(timeCollected, "ok_sub_spotcny_btc_depth_60", json)

    val bids = List(
      Order(3841.52, 0.372),
      Order(3841.46, 0.548),
      Order(3841.4, 0.812)
    )
    val asks = List(
      Order(3844.75, 0.04),
      Order(3844.71, 5.181),
      Order(3844.63, 3.143)
    )
    val timestamp = Instant.ofEpochMilli(1465496881515L)
    val expected = OrderBook(bids, asks, Some(timestamp), Some(timeCollected))

    actorRef ! data
    expectMsg(("orderbook", OrderBook.format.to(expected)))
  }

  it should "process a trade message" in {
    // OKCoin only returns the time of the trade
    // timestamp should pull date from time collected
    val timeCollected = Instant.ofEpochSecond(1464117326L)
    val json = JArray(List(
      "2949439265"
      ,"2968.55"
      ,"0.02",
      "03:15:24",
      "ask"
    ))
    val data = Data(timeCollected, "ok_sub_spotcny_btc_trades", JArray(List(json)))

    val timestamp = Instant.ofEpochSecond(1464117324L)
    val expected = Trade(2968.55, 0.02, timestamp, timeCollected, Some("ask"), Some(2949439265L))

    actorRef ! data
    expectMsg(("trades", Trade.format.to(expected)))
  }
}
