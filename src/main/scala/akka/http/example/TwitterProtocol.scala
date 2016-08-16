package akka.http.example

import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, _}
import akka.http.scaladsl.model.{ContentTypeRange, ContentTypes, MediaTypes}
import akka.util.ByteString
import spray.json.{CompactPrinter, DefaultJsonProtocol, JsValue, JsonPrinter, RootJsonWriter}

trait TwitterProtocol extends DefaultJsonProtocol with SprayJsonSupport {

  // FIXME only needed until 2.4.9 is out
  implicit def sprayJsValueByteStringMarshaller(implicit printer: JsonPrinter = CompactPrinter): ToByteStringMarshaller[JsValue] =
    Marshaller.strict(js => Marshalling.WithFixedContentType(ContentTypes.`application/json`, () => ByteString(printer(js))))
  implicit def sprayJsonByteStringMarshaller[T](implicit writer: RootJsonWriter[T], printer: JsonPrinter = CompactPrinter): ToByteStringMarshaller[T] =
    sprayJsValueByteStringMarshaller compose writer.write
  
  case class DeleteTweet(
    status: TweetStatus, 
    timestamp_ms: String)

  case class TweetStatus(
    id: Long, 
    user_id: Long)

  case class Tweet(
    text:       String,
    user:       TwitterUser,
    created_at: String,
    lang:       String)

  case class TwitterUser(
    id:   Long,
    name: String
  )

  // FlowOpsrmats --- 

  implicit val jsonStreamingSupport =
    EntityStreamingSupport.json(maxObjectLength = Int.MaxValue)
      .withSupported(ContentTypeRange(MediaTypes.`application/octet-stream`))

  implicit val twitterUserFlowOpsrmat = jsonFormat2(TwitterUser)
  implicit val tweetFlowOpsrmat = jsonFormat4(Tweet)
}

object TwitterProtocol extends TwitterProtocol
