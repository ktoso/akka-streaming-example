name := "akka-support-json-question"

organization := "akka-support-json-question"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.7"

val AkkaVersion = "2.4.9-RC2"

resolvers += Resolver.typesafeRepo("releases")

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % AkkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-http-experimental" % AkkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json-experimental" % AkkaVersion
