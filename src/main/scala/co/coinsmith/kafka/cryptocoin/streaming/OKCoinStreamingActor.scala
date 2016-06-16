package co.coinsmith.kafka.cryptocoin.streaming

import java.net.URI
import java.time._
import javax.websocket.Session

import co.coinsmith.kafka.cryptocoin.KafkaProducer
import org.json4s.DefaultFormats
import org.json4s.JsonAST._
import org.json4s.JsonDSL.WithBigDecimal._
import org.json4s.jackson.JsonMethods._

case class Data(timeCollected: Instant, channel: String, data: JValue)

class OKCoinStreamingActor extends ExchangeStreamingActor {
  implicit val formats = DefaultFormats
  val name = "OKCoin"
  val uri = new URI("wss://real.okcoin.cn:10440/websocket/okcoinapi")

  def subscribe(session: Session) = {
    val channels = List(
      ("event" -> "addChannel") ~ ("channel" -> "ok_sub_spotcny_btc_ticker"),
      ("event" -> "addChannel") ~ ("channel" -> "ok_sub_spotcny_btc_depth_60"),
      ("event" -> "addChannel") ~ ("channel" -> "ok_sub_spotcny_btc_trades")
    )
    val msg = compact(render(channels))
    session.getBasicRemote.sendText(msg)
    log.debug("Sent initialization message: {}", msg)
  }

  def receive = {
    case Connect => connect
    case (t, events: JArray) =>
      // OKCoin websocket responses are an array of multiple events
      events transformField {
        case JField("timestamp", JString(t)) => ("timestamp" -> Instant.ofEpochMilli(t.toLong).toString)
      } match {
        case JArray(arr) => arr.foreach { event => self !(t, event) }
        case _ => new Exception("Message did not contain array.")
      }
    case (t, JObject(JField("channel", JString(channel)) ::
                     JField("success", JString(success)) :: Nil)) =>
      log.info("Added channel {} at time {}.", channel, t)

    case (t, JObject(JField("channel", JString(channel)) ::
                   JField("success", JString(success)) ::
                   JField("error_code", JInt(errorCode)) :: Nil)) =>
      log.error("Adding channel {} failed at time {}. Error code {}.", channel, t, errorCode)

    case (t: Instant, JObject(JField("channel", JString(channel)) :: JField("data", data) :: Nil)) =>
      self ! Data(t, channel, data)

    case Data(t, "ok_sub_spotcny_btc_ticker", data) =>
      val json = data.transformField {
        case JField("sell", v) => JField("ask", v)
        case JField("buy", v) => JField("bid", v)
        case JField("vol", JString(v)) => JField("volume", JDecimal(BigDecimal(v.replace(",", ""))))
        case JField(key, JString(value)) if key != "timestamp" => JField(key, JDecimal(BigDecimal(value)))
      } merge render("time_collected" -> t.toString)
      self ! ("stream_ticks", name, json)

    case Data(t, "ok_sub_spotcny_btc_depth_60", data) =>
      self ! ("stream_orderbooks", name, mergeInstant("time_collected", t, data))

    case Data(t, "ok_sub_spotcny_btc_trades", data: JArray) =>
      val json = data.transform {
        case JArray(JString(id) :: JString(p) :: JString(v) :: JString(time) :: JString(kind) :: Nil) =>
          val zone = ZoneId.of("Asia/Shanghai")
          val collectedZoned = ZonedDateTime.ofInstant(t, ZoneOffset.UTC)
            .withZoneSameInstant(zone)
          var tradeZoned = LocalTime.parse(time).atDate(collectedZoned.toLocalDate).atZone(zone)
          if ((tradeZoned compareTo collectedZoned) > 0) {
            // correct date if time collected happens right after midnight
            tradeZoned = tradeZoned minusDays 1
          }
          val timestamp = tradeZoned.withZoneSameInstant(ZoneOffset.UTC)
          ("timestamp" -> timestamp.toString) ~
            ("time_collected" -> t.toString) ~
            ("id" -> id.toLong) ~
            ("price" -> BigDecimal(p)) ~
            ("volume" -> BigDecimal(v)) ~
            ("type" -> kind)
      }
      self ! ("stream_trades", name, json)

    case (topic: String, key: String, json: JValue) =>
      val msg = compact(render(json))
      KafkaProducer.send(topic, key, msg)
  }
}
