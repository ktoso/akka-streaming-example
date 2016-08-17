/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.http.example

import java.nio.file.Paths

import akka.NotUsed
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

final case class WikipediaEntry(title: String, content: String, related: List[String])
final case class RichWikipediaEntry(wikipediaEntry: WikipediaEntry, image: ByteString)

object ETLSample extends AkkaApp {

  // kafka producer settings
  lazy val producerSettings = ProducerSettings(system, new ByteArraySerializer, new StringSerializer)
    .withBootstrapServers("localhost:9092")

  /* Here we'll show off the Streaming XML capabilities */
  def parseWikiEntries: Flow[ByteString, WikipediaEntry, NotUsed] =
    ??? 

  /* Here we're showing off parallel fetching of additional data using Akka HTTP, the response is an image */
  def enrichWithImageData: Flow[WikipediaEntry, RichWikipediaEntry, NotUsed] = {
    val parallelism = Runtime.getRuntime.availableProcessors()

    Flow[WikipediaEntry]
      .mapAsyncUnordered(parallelism) { w =>
        val uri = Uri("http://images.example.com/query").withQuery(Query(Map("query" -> w.title)))
        val request = HttpRequest(uri = uri)

        Http().singleRequest(request)
          .flatMap { response =>
            response.entity.toStrict(1.second)
              .map(strict =>  RichWikipediaEntry(w, strict.data))
          }
      }
  }

  /* This stores wikipedia contents to Kafka */
  def wikipediaKafkaTopic: Sink[RichWikipediaEntry, NotUsed] =
    Flow[RichWikipediaEntry]
      .map(_.wikipediaEntry.content)
      .map(elem => new ProducerRecord[Array[Byte], String]("contents", elem))
      .to(Producer.plainSink(producerSettings))

  /* This is an imaginary S3 Sink */
  def s3ImageStorage: Sink[RichWikipediaEntry, NotUsed] =
    ???

  
  /* Combining the pipeline: */
  
  val wikipediaEntries: Source[WikipediaEntry, Future[IOResult]] =
    FileIO.fromPath(Paths.get("/tmp", "wiki"))
      .via(parseWikiEntries) 

  val enrichedData: Source[RichWikipediaEntry, Future[IOResult]] = wikipediaEntries
    .via(enrichWithImageData)
  
  val completed = enrichedData
    .alsoTo(s3ImageStorage)
    .to(wikipediaKafkaTopic)
    .run()
  
  completed.onComplete {
    case Success(ioResult) => 
      println(s"Streamed ${ioResult.count} bytes of wikipedia data!")
      system.terminate()
      
    case Failure(ex) => 
      println("ERROR during wikipedia streaming! Exception was: " + ex)
      system.terminate()
  }

}
