import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import com.typesafe.sbt.packager.docker.Cmd

organization := "com.idorsia.research.arcite"

name := "arcite-bcl2fastq"

version := "1.3.45"

scalaVersion := "2.11.8"

crossScalaVersions := Seq(scalaVersion.value, "2.12.1")

resolvers ++= Seq(
  Resolver.mavenLocal,
  Resolver.file("ivy local", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns),
  Resolver.bintrayRepo("typesafe", "maven-releases"),
  Resolver.jcenterRepo,
  Resolver.sonatypeRepo("public"),
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  MavenRepository("mvn-repository", "https://mvnrepository.com/artifact/"),
  MavenRepository("Artima Maven Repository", "http://repo.artima.com/releases/"))

// These options will be used for *all* versions.
scalacOptions ++= Seq(
  "-deprecation"
  , "-unchecked"
  , "-encoding", "UTF-8"
  , "-Xlint"
  //  , "-Yclosure-elim"
  //  , "-Yinline"
  , "-Xverify"
  , "-feature"
  , "-language:postfixOps"
)

libraryDependencies ++= {
  val akkaVersion = "2.4.17"
  val sparkVersion = "2.1.0"
  val akkaHttpVersion = "10.0.5"
  val akkaManagementVersion = "0.12.0"

  Seq(
    "com.idorsia.research.arcite" %% "arcite-core" % "1.91.6",
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-agent" % akkaVersion,
    "com.typesafe.akka" %% "akka-camel" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
    "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
    "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,
    "com.typesafe.akka" %% "akka-osgi" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence-tck" % akkaVersion,
    "com.typesafe.akka" %% "akka-remote" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
    "com.typesafe.akka" %% "akka-distributed-data-experimental" % akkaVersion,
    "com.typesafe.akka" %% "akka-typed-experimental" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence-query-experimental" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-jackson" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-xml" % akkaHttpVersion,
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "org.scalanlp" %% "breeze" % "0.13",
    "org.iq80.leveldb" % "leveldb" % "0.9",
    "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
    "commons-io" % "commons-io" % "2.5",
    "com.fasterxml.jackson.core" % "jackson-databind" % "2.7.6",
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.7.6",
    "org.apache.spark" %% "spark-core" % sparkVersion,
    "org.apache.spark" %% "spark-hive" % sparkVersion,
    "org.apache.spark" %% "spark-graphx" % sparkVersion,
    "org.apache.spark" %% "spark-mllib" % sparkVersion,
    "org.scalacheck" %% "scalacheck" % "1.13.5" % "test",
    "org.specs2" %% "specs2-core" % "3.8.9" % "test",
    "org.scalactic" %% "scalactic" % "3.0.1",
    "org.scalatest" %% "scalatest" % "3.0.1" % "test",
    "com.github.agourlay" %% "cornichon" % "0.11.2" % "test",
    "org.json4s" %% "json4s-jackson" % "3.5.1",
    "com.twitter" %% "chill" % "0.9.2" % "runtime",
    "com.twitter" %% "chill-akka" % "0.9.2" % "runtime",
    "com.lightbend.akka.management" %% "akka-management" % akkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion,
    "com.lightbend.akka.discovery" %% "akka-discovery-marathon-api" % akkaManagementVersion)

}

// which one is really needed?
enablePlugins(JavaServerAppPackaging)
enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)
enablePlugins(DockerSpotifyClientPlugin)

mainClass in Compile := Some("com.idorsia.research.arcite.mps.bcl2fastq.Main")

//we wipe out all previous settings of docker commands
dockerCommands := Seq(
  Cmd("FROM", "centos:7"),
  Cmd("MAINTAINER", "Bernard Deffarges bernard.deffarges@idorsia.com"),
  Cmd("RUN", "rm -f /etc/localtime && ln -s /usr/share/zoneinfo/Europe/Zurich /etc/localtime"),
  Cmd("RUN", "yum update -y && yum install -y unzip && yum install -y wget && yum clean all"),
  Cmd("RUN",
    """wget --no-cookies --no-check-certificate \
      |--header "Cookie: oraclelicense=accept-securebackup-cookie" \
      |"http://download.oracle.com/otn-pub/java/jdk/8u141-b15/336fa29ff2bb4ef291e347e091f7f4a7/jdk-8u141-linux-x64.rpm" \
      |-O /tmp/jdk-8-linux-x64.rpm""".stripMargin),
  Cmd("RUN", "yum -y install /tmp/jdk-8-linux-x64.rpm"),
  Cmd("RUN", "rm -rf /tmp/*"),
  Cmd("ENV", "JAVA_HOME /usr/java/latest"),
  Cmd("ADD", "http://support.illumina.com/content/dam/illumina-support/documents/downloads/software/bcl2fastq/bcl2fastq2-v2-18-0-12-linux-x86-64.zip  /"),
  Cmd("RUN",
    """unzip /bcl2fastq2-v2-18-0-12-linux-x86-64.zip && \
      |	rpm -i /bcl2fastq2-v2.18.0.12-Linux-x86_64.rpm && \
      |	rm /bcl2fastq2-v2-18-0-12-linux-x86-64.zip && \
      |	rm /bcl2fastq2-v2.18.0.12-Linux-x86_64.rpm
      |""".stripMargin),
  Cmd("RUN", "groupadd arcite -g 987654"),
  Cmd("RUN", "useradd --uid 987654 --gid 987654 arcite"),
  Cmd("WORKDIR", "/opt/docker"),
  Cmd("COPY", "opt /opt"),
  Cmd("RUN", "chown -R arcite:arcite ."),
  Cmd("USER", "arcite"),
  Cmd("ENTRYPOINT", "bin/arcite-bcl2fastq"))

dockerRepository := Some("nexus-docker.idorsia.com")
dockerAlias := DockerAlias(dockerRepository.value, Some("arcite"), packageName.value, Some(version.value))

dockerExposedPorts := Seq(2600)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))

bashScriptExtraDefines += """addJava "-Dconfig.resource=$ARCITE_BCL2FASTQ_CONF""""



