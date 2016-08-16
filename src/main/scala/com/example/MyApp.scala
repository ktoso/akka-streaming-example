package com.example

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import argonaut.Argonaut._
import argonaut._
import de.heikoseeberger.akkahttpargonaut.ArgonautSupport

import scala.concurrent.Await
import scala.concurrent.duration._




import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import argonaut.Argonaut._
import argonaut._

import scala.concurrent.Await
import scala.concurrent.duration._
import de.heikoseeberger.akkahttpargonaut.ArgonautSupport

object Main extends App  with ArgonautSupport { //with RestInterface

  val config = ConfigFactory.load()
  val host = config.getString("http.host")
  val port = config.getInt("http.port")
  val targetEnvironment = args(0)

  implicit val system = ActorSystem("quiz-management-service")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher
  implicit val timeout = Timeout(10 seconds)

  /*val api = routes

  Http().bindAndHandle(handler = api, interface = host, port = port) map { binding =>
    println(s"REST interface bound to ${binding.localAddress}")
  } recover { case ex =>
    println(s"REST interface could not bind to $host:$port", ex.getMessage)
  }

  val db = Database.forURL(
    url = config.getString(s"database.$targetEnvironment.url"),
    driver = config.getString(s"database.$targetEnvironment.driver"),
    user = config.getString(s"database.$targetEnvironment.username"),
    password = config.getString(s"database.$targetEnvironment.password")
  )

  partitionedJobService.setDBConnection(db)*/

  val json = """{"client":{"name":"BRAD_TEST","createdOn":"2016-01-27T13:51:14.095-05:00","updatedOn":"2016-01-27T13:51:14.095-05:00","description":"BRAD_TEST","id":1,"createdBy":"xxx","activeFlag":true,"updatedBy":"xxxx","previouslyUpdatedOn":"2016-01-27T13:51:14.095-05:00"},"actionResult":""}"""

  val ent = Unmarshal(HttpEntity(ContentTypes.`application/json`, json)).to[ClientDTOResponseWrapper]

  println("Await.result(ent) = " + Await.result(ent, 1.second))


}

case class ClientDTOResponseWrapper(clientDTO: ClientDTOO, actionResult: String)

object ClientDTOResponseWrapper {
  implicit def ClientDTOResponseWrapperCodecJson: CodecJson[ClientDTOResponseWrapper] =
    casecodec2(ClientDTOResponseWrapper.apply, ClientDTOResponseWrapper.unapply)("client", "actionResult")
}


final case class ClientDTOO(
                            id: Option[Int] = None,
                            name: Option[String] = None,
                            description: Option[String] = None,
                            activeFlag: Boolean = false,
                            createdBy: Option[String] = None,
                            updatedBy: Option[String] = None
                          )


object ClientDTOO {

  /**
    * ClientDTO JSON Encoder/Decoder
    */
  implicit def ClientDTOCodecJson: CodecJson[ClientDTOO] = {
    CodecJson(
      (js: ClientDTOO) =>
        ("id" := js.id) ->:
          ("name" := js.name) ->:
          ("description" := js.description) ->:
          ("activeFlag" := js.activeFlag) ->:
          ("createdBy" := js.createdBy) ->:
          ("updatedBy" := js.updatedBy) ->:
          jEmptyObject,
      js => for {
        id <- (js --\ "id").as[Option[Int]]
        name <- (js --\ "name").as[Option[String]]
        description <- (js --\ "description").as[Option[String]]
        activeFlag <- (js --\ "activeFlag").as[Boolean]
        createdBy <- (js --\ "createdBy").as[Option[String]]
        updatedBy <- (js --\ "updatedBy").as[Option[String]]
      } yield ClientDTOO(
        id,
        name,
        description,
        activeFlag,
        createdBy,
        updatedBy
      )
    )
  }
}

//object MyApp extends App
//  with ArgonautSupport {
//
//  implicit val system = ActorSystem()
//  implicit val mat = ActorMaterializer()
//
//  import system.dispatcher
//  
//  val json = """{"client":{"name":"BRAD_TEST","createdOn":"2016-01-27T13:51:14.095-05:00","updatedOn":"2016-01-27T13:51:14.095-05:00","description":"BRAD_TEST","id":1,"createdBy":"xxx","activeFlag":true,"updatedBy":"xxxx","previouslyUpdatedOn":"2016-01-27T13:51:14.095-05:00"},"actionResult":""}"""
//
//  val ent = Unmarshal(HttpEntity(ContentTypes.`application/json`, json)).to[ClientDTOResponseWrapper]
//
//  println("Await.result(ent) = " + Await.result(ent, 1.second))
//
//}
//
//case class ClientDTOResponseWrapper(clientDTO: ClientDTO, actionResult: String)
//
//object ClientDTOResponseWrapper {
//  implicit def ClientDTOResponseWrapperCodecJson: CodecJson[ClientDTOResponseWrapper] =
//    casecodec2(ClientDTOResponseWrapper.apply, ClientDTOResponseWrapper.unapply)("client", "actionResult")
//}
//
//
//final case class ClientDTO(
//  id: Option[Int] = None,
//  name: Option[String] = None,
//  description: Option[String] = None,
//  activeFlag: Boolean = false,
//  createdBy: Option[String] = None,
//  updatedBy: Option[String] = None
//)
//
//// extends TDataTransferObject
//
//
//object ClientDTO {
//
//  /**
//   * ClientDTO JSON Encoder/Decoder
//   */
//  implicit def ClientDTOCodecJson: CodecJson[ClientDTO] = {
//    CodecJson(
//      (js: ClientDTO) =>
//        ("id" := js.id) ->:
//          ("name" := js.name) ->:
//          ("description" := js.description) ->:
//          ("activeFlag" := js.activeFlag) ->:
//          ("createdBy" := js.createdBy) ->:
//          ("updatedBy" := js.updatedBy) ->:
//          jEmptyObject,
//      js => for {
//        id <- (js --\ "id").as[Option[Int]]
//        name <- (js --\ "name").as[Option[String]]
//        description <- (js --\ "description").as[Option[String]]
//        activeFlag <- (js --\ "activeFlag").as[Boolean]
//        createdBy <- (js --\ "createdBy").as[Option[String]]
//        updatedBy <- (js --\ "updatedBy").as[Option[String]]
//      } yield ClientDTO(
//        id,
//        name,
//        description,
//        activeFlag,
//        createdBy,
//        updatedBy
//      )
//    )
//  }
//}
