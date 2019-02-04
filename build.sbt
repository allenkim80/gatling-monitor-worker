val scala_version = "2.12.6"
val akkaVersion     = "2.5.12"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings(
    name := "gatling-monitor-worker",
    version := (version in ThisBuild).value,
    organization := "com.github.allenkim",
    scalaVersion := scala_version,
    mainClass in Compile := Some("com.github.allenkim.gatling.monitor.worker.Worker"),
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka"   %% "akka-actor"                 % akkaVersion,
        "com.typesafe.akka"   %% "akka-stream"                % akkaVersion,
        "com.typesafe.akka"   %% "akka-slf4j"                 % akkaVersion,
        "ch.qos.logback"      %  "logback-classic"           % "1.2.3",

        "com.typesafe.akka"   %% "akka-testkit"               % akkaVersion     % "test",

        "org.scala-lang"      % "scala-library"              % scala_version,
        "org.scalatest"       %% "scalatest"                  % "3.0.5"         % "test",

        "com.typesafe.akka"   %% "akka-remote"                % akkaVersion,
        "com.typesafe.akka"   %% "akka-multi-node-testkit"    % akkaVersion     % "test"
      )
    }
  )


publishArtifact in Test := false

publishArtifact in packageBin := true

publishMavenStyle := true

//publishTo := {
//  val nexus = "http://apseo-nexus"
//  if (isSnapshot.value)
//    Some("snapshots" at nexus + "/repository/maven-snapshots")
//  else
//    Some("releases"  at nexus + "/repository/maven-releases")
//}
//
//credentials += Credentials( "Sonatype Nexus Repository Manager", "apseo-nexus", "", "")

pomIncludeRepository := { _ => false }

pomExtra := <url>http://github.com/allenkim80/gatling-sentry-extension</url>
  <licenses>
    <license>
      <name>The MIT License (MIT)</name>
      <url>https://opensource.org/licenses/MIT</url>
    </license>
  </licenses>
  <scm>
    <url>http://github.com/allenkim80/gatling-sentry-extension.git</url>
    <connection>scm:git:http://github.com/allenkim80/gatling-sentry-extension.git</connection>
  </scm>
  <developers>
    <developer>
      <id>allenkim80</id>
      <name>Allen Kim</name>
    </developer>
  </developers>