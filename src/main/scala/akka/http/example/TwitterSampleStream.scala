/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.http.example

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.unmarshalling.{FromByteStringUnmarshaller, FromEntityUnmarshaller, FromRequestUnmarshaller, Unmarshal}
import akka.stream._
import akka.stream.impl.fusing.GraphStages.SimpleLinearGraphStage
import akka.stream.scaladsl._
import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.immutable
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.io.StdIn
import scala.reflect.ClassTag

trait TwitterProtocol extends DefaultJsonProtocol with SprayJsonSupport {

  case class DeleteTweet(status: TweetStatus, timestamp_ms: String)

  case class TweetStatus(id: Long, user_id: Long)

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

trait HttpStreamExtras {

  final case class HttpRequestFailedException(r: HttpResponse)
    extends RuntimeException(s"HttpResponse failed with [${r.status}]")

  implicit class HttpSource[M](val s: Source[HttpResponse, M]) {
    def handleHttpStatusCode: Source[HttpResponse, M] =
      s.via(new SimpleLinearGraphStage[HttpResponse] {
        override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
          setHandlers(in, out, this)

          override def onPush(): Unit = {
            val el = grab(in)
            if (el.status.isSuccess) push(out, el)
            else failStage(HttpRequestFailedException(el))
          }

          override def onPull(): Unit = pull(in)
        }
      })

    def extractDataBytes: Source[ByteString, M] =
      s.flatMapConcat(_.entity.dataBytes)

    def unmarshalAsSourceOf[T](implicit
      ess: EntityStreamingSupport,
                               um:  FromByteStringUnmarshaller[T],
                               ec:  ExecutionContext,
                               mat: Materializer): Source[T, M] =
      s.extractDataBytes
        .via(ess.framingDecoder)
        .mapAsync(ess.parallelism)(bs ⇒ um(bs)(ec, mat))
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))

  }

}

object TwitterStream extends App with HttpStreamExtras {
  val testConf: Config = ConfigFactory.parseString("""
    akka.loglevel = INFO
    akka.log-dead-letters = off
    akka.stream.materializer.debug.fuzzing-mode = off
    """)

  implicit val system = ActorSystem("TwitterStreaming", testConf)
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  import TwitterProtocol._
  import Directives._

  // get keys: https://dev.twitter.com/oauth/tools/signature-generator/922320?nid=813
  val consumerKey = "32RLV3a77ECWrE32SG5Pg0NkB"
  val consumerSecret = "v7fUoWn9iAHLKR2iMHL141AQOVeAPyC3YpaqrqPHpTLRNbaSpt"

  val twitterAuthSnippet = """OAuth oauth_consumer_key="32RLV3a77ECWrE32SG5Pg0NkB", oauth_nonce="0c47936958a0b4a8c2bce14d5b3699b2", oauth_signature="xLEi%2Bzlvz%2Flc8Io1m8hN8MIPSvs%3D", oauth_signature_method="HMAC-SHA1", oauth_timestamp="1471342748", oauth_token="54490597-mpxeU54vhTY6Wl1gbzGtj8kTgGR1TWMhKpxbLcXFb", oauth_version="1.0""""
  val auth = Authorization.parseFromValueString(twitterAuthSnippet).right.get

  def statusesFilter(track: immutable.Seq[String], stallWarnings: Boolean): Source[Tweet, NotUsed] = {
    val uri =
      Uri("https://stream.twitter.com/1.1/statuses/filter.json")
        .withQuery(Uri.Query("track" → track.mkString(",")))

    val r = HttpRequest(uri = uri)
      .addHeader(auth)
      .addHeader(Accept(MediaRanges.`*/*`))

    Source.fromIterator(() ⇒ List(Http().singleRequest(r)).iterator)
      .mapAsync(1)(identity)
      .handleHttpStatusCode
      .unmarshalAsSourceOf[Tweet]
      .log("tweets").withAttributes(ActorAttributes.logLevels(onFailure = Logging.ErrorLevel))
  }

  val trackTags = List("akka")
  val tweets = statusesFilter(trackTags, stallWarnings = false)
  val done = tweets.runWith(Sink.foreach(t ⇒ println(s"tweet: '${t.text.replace("\n", "")}' by ${t.user.name}")))

  done flatMap { result ⇒
    system.terminate()
  }

  readLine()
  system.terminate()

}
