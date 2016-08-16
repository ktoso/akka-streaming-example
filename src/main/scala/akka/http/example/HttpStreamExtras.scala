package akka.http.example

import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.unmarshalling._
import akka.stream.{ActorAttributes, Attributes, Materializer, Supervision}
import akka.stream.impl.fusing.GraphStages.SimpleLinearGraphStage
import akka.stream.scaladsl.Source
import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

import scala.concurrent.ExecutionContext


trait HttpStreamExtras {

  final case class HttpRequestFailedException(r: HttpResponse)
    extends RuntimeException(s"HttpResponse failed with [${r.status}]")

  implicit class HttpSource[M](val s: Source[HttpResponse, M]) {

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
