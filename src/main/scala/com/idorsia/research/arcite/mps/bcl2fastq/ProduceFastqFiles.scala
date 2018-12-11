package com.idorsia.research.arcite.mps.bcl2fastq

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{FileSystemException, Files, Path, StandardOpenOption}
import java.nio.file.StandardOpenOption.CREATE

import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy}

import com.idorsia.research.arcite.core.transforms.cluster.{GetTransfDefId, TransformType}
import com.idorsia.research.arcite.core.transforms.cluster.TransformWorker.{WorkerJobFailed, WorkerJobProgress, WorkerJobSuccessFul}
import com.idorsia.research.arcite.core.transforms._
import com.idorsia.research.arcite.core.utils
import com.idorsia.research.arcite.core.utils.FullName
import com.idorsia.research.arcite.mps.bcl2fastq.Bcl2FastqFolderSetProcessActor._
import com.typesafe.scalalogging.LazyLogging

import scala.sys.process.ProcessLogger
import scala.concurrent.duration._


/**
  * arcite-mps
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
  * along with this program. If not, see <http://www.gnu.org/licenses/>.
  *
  * Created by Bernard Deffarges on 2017/07/11.
  *
  */
class ProduceFastqFiles extends Actor with ActorLogging {

  import ProduceFastqFiles._

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 0, loggingEnabled = true) {
      case exc: Exception ⇒
        log.error(s"exception [$exc] in children of [ProduceFastqFiles]")
        Escalate
    }

  override def receive: Receive = {
    case t: Transform ⇒
      require(t.transfDefName == transfDefID.fullName)
      val s = sender()
      context.actorOf(Props(classOf[ProduceFastqFilesHelper], s)) ! t

    case GetTransfDefId(wi) ⇒
      log.info(s"asking worker type for $wi, that's who I am: [${transfDefID.fullName}]")
      sender() ! TransformType(wi, transfDefID)

    case msg: Any ⇒ log.error(s"(ProduceFastqFiles) unable to deal with message: $msg")

  }
}

object ProduceFastqFiles {
  val fastqFolder = "fastqFiles"

  val fullName = FullName("com.idorsia.research.arcite.mps.bcl2fastq",
    "Transform bcl files to fastq files ", "bcl-2-fastq")

  val transfDefID = TransformDefinitionIdentity(fullName,
    TransformDescription("Takes a set of bcl files and tranform them to fastq ",
      consumes = "bcl files", produces = "fastq files",
      Set(PredefinedValues("barcodeMismatches", "barcode mismatches", List("0", "1", "2"), Some("1"), false),
        FreeText("bases-mask", "bases-mask", Some("Y*,I8")),
        PredefinedValues("CreateFastqForIndexReads", "CreateFastqForIndexReads", List("true", "false"), Some("true"), false))))

  val definition = TransformDefinition(transfDefID, props)

  def props(): Props = Props(classOf[ProduceFastqFiles])

  case class FlushOutputFiles(output: String, error: String)

}

class ProduceFastqFilesHelper(requester: ActorRef) extends Actor with ActorLogging {

  import ProduceFastqFiles._
  import Bcl2FastqFolderSetProcessActor._

  private var transfHelper: Option[TransformHelper] = None
  private var fastqFoldersCompleted: Set[File] = Set.empty
  private var sampleSheet: Option[SampleSheet] = None
  private var targetFastQFolders: Set[Path] = Set.empty

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 0, loggingEnabled = true) {
      case exc: Exception ⇒
        log.error(s"exception [$exc] in children of [ProduceFastqFilesHelper]")
        Escalate
    }

  override def receive: Receive = {
    case t: Transform ⇒
      require(t.transfDefName == transfDefID.fullName)

      t.source match {
        case tfr: TransformSourceFromRaw ⇒
          transfHelper = Some(TransformHelper(t))
          val visit = transfHelper.get.experimentFolderVisitor

          val transfFolder = transfHelper.get.getTransformFolder()

          val targetFolder = transfFolder resolve fastqFolder

          targetFolder.toFile.mkdir()

          log.debug(s"looking into userMeta ${visit.userMetaFolderPath.toString} for sample sheet file...")
          val sampleSheetFile = SampleSheetHelper.getSampleSheetFile(visit.userMetaFolderPath)

          if (sampleSheetFile.isDefined) {
            log.debug(s"found sample sheet file: ${sampleSheetFile.get.getAbsolutePath}")
            sampleSheet =
              Some(SampleSheetHelper.readSampleSheet(sampleSheetFile.get.toPath))
          }

          val barcodeMismatches = t.parameters.getOrElse("barcodeMismatches", "1")

          val creatFastq4InR: Boolean = t.parameters.getOrElse("CreateFastqForIndexReads", "true") == "true"

          val baseMask = t.parameters.getOrElse("bases-mask", "")

          val allFolders = visit.rawFolderPath.toFile.listFiles().filter(_.isDirectory).toSet

          log.info(s"in [${visit.rawFolderPath.toString}] found folders to process: $allFolders")

          log.info(s"sample sheet: ${sampleSheetFile.fold("none defined")(_.getName)}")

          if (allFolders.isEmpty) {
            requester ! WorkerJobFailed("tried to produce fastq but did not find any bcl files. ")
          } else {
            targetFastQFolders = allFolders.map(f ⇒ targetFolder resolve f.getName)
            targetFastQFolders.foreach(_.toFile.mkdirs())
            context.actorOf(Bcl2FastqFolderSetProcessActor
              .props(targetFolder, allFolders.map(_.toPath), sampleSheetFile,
                Bcl2FastqOptions(barcodeMismatches, baseMask, creatFastq4InR))) !
              StartBcl2FastqForAllFolders
          }

        case _: Any ⇒ requester ! WorkerJobFailed("should start from raw files. ")

      }


    case AllFastqProduced(outp, err) ⇒

      val artifacts =
        fastqFoldersCompleted.map(fc ⇒ (fc.getName, s"${fastqFolder}/${fc.getName}/Reports/html/index.html")).toMap

      self ! FlushOutputFiles(outp, err)
      requester ! WorkerJobSuccessFul("all fastq files produced. ", artifacts)


    case FastqFailed(outp, err, complete) ⇒
      val st = if (complete > 0) s"$complete fastq were produced but failed afterwards. $outp" else outp
      self ! FlushOutputFiles(outp, err)
      requester ! WorkerJobFailed(utils.getEnoughButNotTooMuchForUserInfo(st),
        utils.getEnoughButNotTooMuchForUserInfo(err))


    case FastqProgress(fastq, completed) ⇒
      fastqFoldersCompleted += fastq
      requester ! WorkerJobProgress(1)


    case FlushOutputFiles(outp, err) ⇒
      val transfFolder = transfHelper.get.getTransformFolder()
      Files.write(transfFolder resolve "output.log", outp.getBytes(StandardCharsets.UTF_8), CREATE)
      Files.write(transfFolder resolve "error.log", err.getBytes(StandardCharsets.UTF_8), CREATE)
      log.info("feedback files flushed. ")

    case msg: Any ⇒ log.error(s"unable to deal with message: $msg")

  }
}


class Bcl2FastqFolderSetProcessActor(targetFolder: Path, bclFolders: Set[Path], sampleSheet: Option[File],
                                     bcl2fastqOpts: Bcl2FastqOptions) extends Actor with ActorLogging {

  import Bcl2FastqFolderProcessAct._
  import Bcl2FastqFolderSetProcessActor._

  val counter = bclFolders.size

  val output = new StringBuilder
  val error = new StringBuilder

  private var completed = 0
  private var failed = 0

  output ++= bclFolders.map(_.getFileName)
    .mkString("List of folders where to expect BCL folder structure: \n", "\n", "\n")

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 2, withinTimeRange = 5 minutes, loggingEnabled = true) {
      case _: FileSystemException ⇒ Restart

      case exc: Exception ⇒
        log.error(s"exception [$exc] in child actor [Bcl2FastqFolderProcessAct]")
        Escalate
    }

  override def receive: Receive = {

    case StartBcl2FastqForAllFolders ⇒
      log.info("dispatching bcl folders to multiple actors... ")
      bclFolders.foreach(fp ⇒
        context.actorOf(Props(classOf[Bcl2FastqFolderProcessAct],
          targetFolder, sampleSheet, bcl2fastqOpts)) ! StartBcl2Fastq(fp))


    case Bcl2FastqCompleted(fastqFolder, outp, err) ⇒
      log.info(s"bcl2fasq has completed for $fastqFolder")
      completed += 1
      output append outp
      error append err
      context.parent ! FastqProgress(fastqFolder, completed)
      if (completed == counter) {
        context.parent ! AllFastqProduced(output.toString, error.toString)
      } else if (completed + failed == counter) {
        context.parent ! FastqFailed(output.toString, error.toString, completed)
      }

    case Bcl2FastqFailed(outp, err) ⇒
      log.warning(s"bcl2fastq has failed for $outp, total=$counter, failed=$failed, successful=$completed")
      failed += 1
      output append outp
      error append err
      if (completed + failed == counter) context.parent ! FastqFailed(output.toString, error.toString, completed)

    case _: Any ⇒
      log.error("unknown message, should not have happened. ")
  }
}

object Bcl2FastqFolderSetProcessActor {

  def props(targetFolder: Path, bclFolders: Set[Path], sampleSheet: Option[File], bcl2fastqOpts: Bcl2FastqOptions) =
    Props(classOf[Bcl2FastqFolderSetProcessActor], targetFolder, bclFolders, sampleSheet, bcl2fastqOpts)

  case object StartBcl2FastqForAllFolders

  case class FastqProgress(currFolder: File, fileCompleted: Int)

  case class AllFastqProduced(output: String, error: String)

  case class FastqFailed(output: String, error: String, fileCompleted: Int)

  case class Bcl2FastqOptions(barcodeMismatches: String, baseMask: String, creatFastq4IndRds: Boolean)

}

class Bcl2FastqFolderProcessAct(targetFolder: Path, sampleSheet: Option[File],
                                bcl2fastqOpts: Bcl2FastqOptions) extends Actor with ActorLogging {

  import Bcl2FastqFolderProcessAct._

  val output = new StringBuilder
  val error = new StringBuilder

  override def receive: Receive = {

    case StartBcl2Fastq(bclFolder) ⇒
      log.info(s"starting processing bclFolder... ${bclFolder.getFileName}")

      val bclFolderName = bclFolder.toFile.getName //todo maybe we can get rid of the bcl folder name
    val toFolder = (targetFolder resolve bclFolderName).toFile
      output append " ************************************** \n "
      output append s" bclFile= $bclFolder\n "
      output append s" target folder for fastq= $toFolder\n "

      val cmd1 =
        Seq("/usr/local/bin/bcl2fastq", "--runfolder-dir",
          bclFolder.toFile.getAbsolutePath, "--output-dir", toFolder.getAbsolutePath,
          "--barcode-mismatches", bcl2fastqOpts.barcodeMismatches, "--no-lane-splitting")

      val cmd2: Seq[String] = if (bcl2fastqOpts.creatFastq4IndRds) cmd1 :+ "--create-fastq-for-index-reads" else cmd1

      val cmd3: Seq[String] = if (bcl2fastqOpts.baseMask.nonEmpty) cmd2 ++ Seq("--use-bases-mask", bcl2fastqOpts.baseMask) else cmd2

      val procCmd = if (sampleSheet.isDefined) cmd3 ++ Seq("--sample-sheet", sampleSheet.get.getAbsolutePath) else cmd1

      val process = scala.sys.process.Process(procCmd)

      log.info(s"starting process: ${process.toString}")
      output append s"process command: ${process.toString}"

      val status = process.!(ProcessLogger(output append _, error append _))

      if (status == 0) {
        output append "bcl2fastq completed for file set, concatenating lanes. "
        output append s"********** COMPLETED for ${bclFolder.getFileName} ************\n"
        sender() ! Bcl2FastqCompleted(toFolder, output.toString, error.toString)
      } else {
        output append s"**********bcl2fastq FAILED for ${bclFolder.getFileName} ************\n"
        sender() ! Bcl2FastqFailed(output.toString, error.toString)
      }
  }
}

object Bcl2FastqFolderProcessAct extends LazyLogging {
  private val rgx = ".{3,50}\\_L\\d{1,5}\\_.{2,40}\\.fastq\\.gz".r

  case class StartBcl2Fastq(bclFolder: Path)

  case class Bcl2FastqCompleted(fastqFolder: File, output: String, error: String)

  case class Bcl2FastqFailed(output: String, error: String)

  //todo remove, we do not concatenate anymore
  @deprecated
  def concatenateFastqFiles(folderPath: Path, sampleSheetF: Option[File]): ConcatFeedback = {
    val info = new StringBuilder
    var status = true
    var concFiles: Seq[Path] = Seq.empty

    def concatFiles(target: Path, files: Set[File]): Unit = {
      val s1 = s"concatenating [${files.mkString(",")}] into [${target.toString}]"
      logger.info(s1)
      info append s1 append "\n"

      val outputStream = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.APPEND)
      files.foreach(f ⇒ Files.copy(f.toPath, outputStream))
      outputStream.close()
    }

    try {
      if (sampleSheetF.isDefined) {
        val sample2Files = SampleSheetHelper.producedFastqFilesBySampleID(folderPath,
          SampleSheetHelper.readSampleSheet(sampleSheetF.get.toPath))

        val s2 = s"sample2Files: ${sample2Files.mkString(",")}"
        logger.debug(s2)
        info append s2 append "\n"

        sample2Files.keys.foreach { k ⇒
          val fileSet: Set[File] = sample2Files(k)
          val s3 = s"concatenating [${fileSet.size}] files for $k"
          logger.debug(s3)
          info append s3 append "\n"

          val concF = folderPath resolve k.project resolve k.id resolve newLaneName(fileSet.head.getName)
          concFiles = concFiles :+ concF
          concatFiles(concF, fileSet)
        }
      }

      val undetF = folderPath.toFile.listFiles()
        .filter(f ⇒ f.getName.startsWith("Undetermined") && f.getName.endsWith("fastq.gz"))
        .toSet

      if (undetF.nonEmpty) {
        val concF = folderPath resolve newLaneName(undetF.head.getName)
        concFiles = concFiles :+ concF
        concatFiles(concF, undetF)
      } else {
        val s4 = "no undetermined files to concatenate. "
        logger.info(s4)
        info append s4 append "\n"
      }
    } catch {
      case exc: Exception ⇒
        val errr = s"Problem while concatenating files ${exc.toString}"
        logger.error(errr)
        info append errr append "\n"
        status = false
    }

    ConcatFeedback(status, info.toString, concFiles.sortBy(_.toString))
  }

  def newLaneName(originalName: String): String = {
    rgx.findFirstIn(originalName).fold("undefined.fastq.gz")(_.replaceAll("L(\\d{3,4})", "L_ALL"))
  }

  case class ConcatFeedback(status: Boolean, output: String, concatFiles: Seq[Path])

}

