package com.idorsia.research.arcite.mps.bcl2fastq.api

import java.io.File

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.idorsia.research.arcite.core.experiments.ExperimentUID
import com.idorsia.research.arcite.core.experiments.ManageExperiments.AddExperiment
import com.idorsia.research.arcite.core.rawdata.DefineRawAndMetaData.SetRawData
import com.idorsia.research.arcite.core.transforms.RunTransform.RunTransformOnRawData
import com.idorsia.research.arcite.core.transforms.TransformDefinitionIdentity
import com.idorsia.research.arcite.core.transforms.cluster.Frontend.OkTransfReceived
import com.idorsia.research.arcite.mps.bcl2fastq.ProduceFastqFiles

import scala.concurrent.Future
import spray.json._

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
class Bcl2FastqApiTests extends ApiTests {

  private var exp1 = experiment()

  private var bcl2fastqTransform: Option[TransformDefinitionIdentity] = None

  private var bcl2fastqTransfUID: Option[String] = None


  "Get bcl2fastq transform " should " return its transform definition " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/transform_definitions?search=bcl2fastq&maxHits=1"))
        .via(connectionFlow).runWith(Sink.head)

    import spray.json._
    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val transfDefs = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Set[TransformDefinitionIdentity]]

      assert(transfDefs.size == 1)

      bcl2fastqTransform = Some(transfDefs.toSeq.head)

      assert(bcl2fastqTransform.get.fullName.asUID == ProduceFastqFiles.transfDefID.fullName.asUID)
    }
  }


  "Create a new experiment " should " return the uid of the new experiment " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(AddExperiment(exp1).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/experiment",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.Created)
      exp1 = exp1.copy(uid = Some(r.entity.asInstanceOf[HttpEntity.Strict]
        .data.decodeString("UTF-8").parseJson.convertTo[ExperimentUID].uid))

      assert(exp1.uid.isDefined)
    }
  }


  "adding illumina sample-sheet design file " should " upload the given file to the user meta folder " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    def createEntity(file: File): RequestEntity = {
      require(file.exists())
      val formData = Multipart.FormData.fromPath("fileupload",
        ContentTypes.`application/octet-stream`, file.toPath, 100000)

      formData.toEntity()
    }

    def createRequest(target: Uri, file: File): HttpRequest = HttpRequest(HttpMethods.POST, uri = target, entity = createEntity(file))

    val req = createRequest(s"$urlPrefix/experiment/${exp1.uid.get}/file_upload/meta",
      new File("test_data/sample_sheet"))

    val res: Future[HttpResponse] = Source.single(req).via(connectionFlow).runWith(Sink.head)

    res.map { r ⇒
      assert(r.status == StatusCodes.Created)
    }
  }


  "defining raw data from data source " should " copy the given files to the raw data folder of the experiment " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val rawData = SetRawData(exp1.uid.get,
      files = Set("/arcite/raw_data/mps/nextseq/170704_NB502046_0002_AHYM57BGX2"),
      symLink = true)

    val jsonRequest = ByteString(rawData.toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/raw_data/from_source",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val res: Future[HttpResponse] = Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    res.map { r ⇒
      assert(r.status == StatusCodes.Created || r.status == StatusCodes.OK)
    }
  }


  " once transferred total raw files for experiment " should " be 1 (number of expected folders)" in {
    implicit val executionContext = system.dispatcher
    import scala.concurrent.duration._

    getAllFilesForExperiment(exp1.uid.get)

    eventually(timeout(2 minutes), interval(30 seconds)) {

      getAllFilesForExperiment(exp1.uid.get)

      assert(filesInfo.size == 1)
    }
  }


  "starting bcl2fastq " should " produce two new folders with fastq files " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(
      RunTransformOnRawData(exp1.uid.get, bcl2fastqTransform.get.fullName.asUID)
        .toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/run_transform/on_raw_data",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    import spray.json._

    responseFuture.map { r ⇒
      logger.info(r.toString())
      val result = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[OkTransfReceived]

      bcl2fastqTransfUID = Some(result.transfUID)
      assert(r.status == StatusCodes.OK)
    }
  }


  " bcl2fastq actors " should " eventually complete and produce the expected fastq files " in {
    implicit val executionContext = system.dispatcher
    import scala.concurrent.duration._

    transStatus += (bcl2fastqTransfUID.get -> false)
    eventually(timeout(120 minutes), interval(5 minutes)) {
      println("checking whether array file preparation is completed...")
      checkTransformStatus(bcl2fastqTransfUID.get)
      transStatus(bcl2fastqTransfUID.get) should be(true)
    }
  }
}
