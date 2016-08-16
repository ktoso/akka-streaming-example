package akka.http.example

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}

class AkkaApp {
  val testConf: Config = ConfigFactory.parseString(
    """
    akka.loglevel = INFO
    akka.log-dead-letters = off
    akka.stream.materializer.debug.fuzzing-mode = off
    """)

  implicit val system = ActorSystem("TwitterStreaming", testConf)
  implicit val executionContext = system.dispatcher
  implicit val materializer = ActorMaterializer()
  
}
