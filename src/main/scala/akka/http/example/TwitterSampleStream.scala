/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.http.example

import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Path}

import akka.NotUsed
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import scala.concurrent.Future


object TwitterSampleStream extends AkkaApp 
  with HttpStreamExtras
  with TwitterProtocol {

  // kafka producer settings
  lazy val producerSettings = ProducerSettings(system, new ByteArraySerializer, new StringSerializer)
    .withBootstrapServers("localhost:9092")
  
  // get keys: https://dev.twitter.com/oauth/tools/signature-generator/922320?nid=813
  val consumerKey = system.settings.config.getString("akka.example.twitter.consumer-key")
  val consumerSecret = system.settings.config.getString("akka.example.twitter.consumer-secret")

  val twitterAuthSnippet = """OAuth oauth_consumer_key="32RLV3a77ECWrE32SG5Pg0NkB", oauth_nonce="0c47936958a0b4a8c2bce14d5b3699b2", oauth_signature="xLEi%2Bzlvz%2Flc8Io1m8hN8MIPSvs%3D", oauth_signature_method="HMAC-SHA1", oauth_timestamp="1471342748", oauth_token="54490597-mpxeU54vhTY6Wl1gbzGtj8kTgGR1TWMhKpxbLcXFb", oauth_version="1.0""""
  val auth = Authorization.parseFromValueString(twitterAuthSnippet).right.get

  val uri =
    Uri("https://stream.twitter.com/1.1/statuses/filter.json")
      .withQuery(Uri.Query("track" → List("akka").mkString(",")))

  val request =
    HttpRequest(uri = uri)
      .addHeader(auth)
      .addHeader(Accept(MediaRanges.`*/*`))

  val requestSource =
    Source.single(())
      .map(_ => Http().singleRequest(request))
      .mapAsync(1)(identity)
      .handleHttpStatusCode

  def fileSink(file: Path): Sink[ByteString, Future[IOResult]] =
    FileIO.toPath(file, Set(APPEND, CREATE))

  def writeToKafka = 
    Flow[Tweet]
      .mapAsync(1)(t => Marshal(t).to[ByteString])
      .map(elem => new ProducerRecord[Array[Byte], String]("tweets", elem.utf8String))
      .to(Producer.plainSink(producerSettings))
    
  
  val tweets = requestSource
    .extractDataBytes
    .alsoTo(fileSink(Files.createTempFile("tweets", "json")))
    
    .via(jsonStreamingSupport.framingDecoder)
    .mapAsync(jsonStreamingSupport.parallelism)(bs ⇒ Unmarshal(bs).to[Tweet])
    
    .log("tweets").withAttributes(ActorAttributes.logLevels(onFailure = Logging.ErrorLevel))

  
  tweets.runWith(writeToKafka)


  readLine()
  system.terminate()

}
