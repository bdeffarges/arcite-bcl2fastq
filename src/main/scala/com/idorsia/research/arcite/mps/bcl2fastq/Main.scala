package com.idorsia.research.arcite.mps.bcl2fastq

import java.util.UUID

import akka.actor.ActorPath
import akka.cluster.client.{ClusterClient, ClusterClientSettings}
import akka.discovery.marathon.MarathonApiSimpleServiceDiscovery
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse}
import com.idorsia.research.arcite.core.transforms.TransformDefinition
import com.idorsia.research.arcite.core.transforms.cluster.{AddWorkerClusterClient, TransformWorker}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.management.cluster.{ClusterMember, ClusterMembers, ClusterUnreachableMember}
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import scala.util.{Failure, Success}

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
  * Created by Bernard Deffarges on 2017/07/11.
  *
  */
object Main extends App {

  val logger = LoggerFactory.getLogger(this.getClass)
  println(args.mkString(" ; "))
  println(s"config environment file: ${System.getProperty("config.resource")}")

  val conf = ConfigFactory.load()

  val clusterClientAdd = new AddWorkerClusterClient("bcl2fastq-workers-actor-system", conf)

  clusterClientAdd.addWorkers(ProduceFastqFiles.definition, 2)
}
