package com.idorsia.research.arcite.mps.bcl2fastq.api

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.idorsia.research.arcite.core.api.ArciteJSONProtocol
import com.idorsia.research.arcite.core.experiments.{Experiment, ExperimentalDesign}
import com.idorsia.research.arcite.core.transforms.{TransformCompletionFeedback, TransformCompletionStatus}
import com.idorsia.research.arcite.core.utils.{FileInformation, Owner}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.scalatest.concurrent.Eventually

import scala.concurrent.Future

/**
  * arcite-bcl2fastq
  *
  * Copyright (C) 2017 Idorsia Pharmaceuticals Ltd.
  * Gewerbestrasse 16
  * CH-4123 Allschwil, Switzerland.
  *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  *
  * Created by Bernard Deffarges on 2017/07/26.
  *
  */
trait ApiTests extends AsyncFlatSpec with Matchers
  with ArciteJSONProtocol with LazyLogging with Eventually {

  val person1 = "M. NGS world genius"
  val organization = "com.idorsia.research.mps.nextseq"
  val owner1 = Owner(organization, person1)

  protected var transStatus: Map[String, Boolean] = Map.empty

  protected var filesInfo: Set[FileInformation] = Set.empty

  val expDesign = ExperimentalDesign("Design for NextSeqTest",    Set.empty)

  def experiment() = Experiment(s"Nextseq-bcl2fastq-test-${UUID.randomUUID().toString.substring(0, 4)}",
    "testing with real values", owner1, design = expDesign)

  val config: Config = ConfigFactory.load()

  val host: String = config.getString("http.host")
  val port: Int = config.getInt("http.port")

  val urlPrefix = "/api/v1"

  implicit var system: ActorSystem = null //todo fix
  implicit var materializer: ActorMaterializer = null

  override def withFixture(test: NoArgAsyncTest) = {
    system = ActorSystem()
    materializer = ActorMaterializer()
    complete {
      super.withFixture(test) // Invoke the test function
    } lastly {
      system.terminate()
    }
  }

  def getAllFilesForExperiment(exp: String): Unit = {
    println(s"checking the raw files... ${filesInfo.size}")

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/experiment/$exp/files/raw"))
        .via(connectionFlow).runWith(Sink.head)

    import spray.json._

    responseFuture.map { r ⇒
      if (r.status == StatusCodes.OK) {
        try {
          filesInfo = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
            .parseJson.convertTo[Set[FileInformation]]
        } catch {
          case exc: Exception ⇒ println(exc)
        }

      }
    }
  }

  def checkTransformStatus(transf: String): Unit = {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/transform/${transf}"))
        .via(connectionFlow).runWith(Sink.head)

    import spray.json._

    println(transStatus)

    responseFuture.map { r ⇒
      if (r.status == StatusCodes.OK) {
        val fb = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
          .parseJson.convertTo[Option[TransformCompletionFeedback]]
        transStatus += transf -> (fb.isDefined && fb.get.status == TransformCompletionStatus.SUCCESS)
      }
    }
  }
}