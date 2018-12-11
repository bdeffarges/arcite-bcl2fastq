package com.idorsia.research.arcite.mps.bcl2fastq

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.typesafe.scalalogging.LazyLogging

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
  * Created by Bernard Deffarges on 2017/08/22.
  *
  */

/**
  * todo: would be nice if sampleSheet.csv is not available, to look for any file ended with csv.
  *
  */
object SampleSheetHelper extends LazyLogging {

  val sampleName: String = "Sample_Name"
  val sampleID: String = "Sample_ID"
  val sampleProject: String = "Sample_Project"

  def readSampleSheet(sampleSheetFile: Path): SampleSheet = {
    logger.debug(s"parsing sample sheet file [${sampleSheetFile.toString}] ")
    val states = List("[Header]", "[Reads]", "[Settings]", "[Data]")
    var state = -1

    var head: Map[String, String] = Map.empty
    var read: List[Int] = List.empty
    var settings: Map[String, String] = Map.empty
    var dataH: List[String] = List.empty
    var dataD: List[SampleSheetSample] = List.empty

    var dataFirst = false

    import scala.collection.convert.wrapAsScala._

    Files.readAllLines(sampleSheetFile, StandardCharsets.UTF_8)
      .toList.filter(_.length > 1).map(_.trim)
      .foreach { l ⇒
        logger.debug(s"next line: ${l}")
        val newState = states.indexOf(l)
        if (newState > state) {
          state = newState
          logger.debug(s"reading [${states(state)}] part in file. ")
        } else {
          state match {
            case 0 ⇒ {
              val kv = l.split(',')
              if (kv.length == 2) head += (kv(0) -> kv(1))
            }
            case 1 ⇒ {
              try {
                val rv = l.toInt
                read :+= rv
              } catch {
                case _: Exception ⇒
                  logger.error("could not parse reads section to Int")
              }
            }
            case 2 ⇒
              val kv = l.split(',')
              if (kv.length == 2) settings += (kv(0) -> kv(1))
            case 3 ⇒ {
              if (dataFirst) {
                val ll = l.split(',')
                val line: Array[String] =
                  if (ll.length < dataH.length) {
                    logger.debug(s"fixing length difference between header [${dataH.mkString(",")}] and data. [${ll.mkString(",")}]")
                    ll ++ Array.fill(dataH.length - ll.length)(" ")
                  } else {
                    ll
                  }
                if (line.length == dataH.length) {
                  val m = dataH.zip(line.toList.map(_.trim)).toMap
                  dataD :+= SampleSheetSample(m)
                } else {
                  logger.error(s"data header [${dataH.mkString(";")}] line and actual data line [${line.mkString(";")}] do not have same length. ")
                }
              } else {
                dataH = l.split(',').toList
                dataFirst = true
              }
            }
            case _ ⇒
              logger.error(s"don't know what to do with line $l")
          }
        }
      }

    val sampS = SampleSheet(head, read, settings, SampleSheetData(dataH, dataD))
    logger.debug(s"sample sheet= [${sampS.toString}]")
    sampS
  }


  def allProducedFastqFiles(fastqPath: Set[Path], sampleSheet: SampleSheet): Set[File] = {

    val projectSampleFolders = sampleSheet.data.sampleSheetSample
      .map(d ⇒ (d.data(sampleProject), d.data(sampleID)))

    val paths = fastqPath.flatMap(p ⇒ projectSampleFolders.map(sf ⇒ p resolve sf._1.trim resolve sf._2.trim))
    logger.debug(s"paths where to search for fastq files: ${paths.mkString(";")}")

    val fastqFiles = paths.filter(_.toFile.isDirectory).flatMap(_.toFile.listFiles.filter(_.getName.endsWith(".fastq.gz")))

    logger.info(s"found ${fastqFiles.size} fastq files.")
    fastqFiles
  }


  @deprecated
  def allConcatenatedFastqFiles(fastqPath: Set[Path], sampleSheet: SampleSheet): Set[File] = {

    val projectSampleFolders = sampleSheet.data.sampleSheetSample
      .map(d ⇒ (d.data(sampleProject), d.data(sampleID)))

    val paths = fastqPath.flatMap(p ⇒ projectSampleFolders.map(sf ⇒ p resolve sf._1 resolve sf._2))

    paths.filter(_.toFile.isDirectory)
      .flatMap(_.toFile.listFiles.filter(f ⇒ f.getName.contains("_L_ALL_") && f.getName.endsWith(".fastq.gz")))
  }

  /**
    * can throw an exception if deep path does not resolve into a folder with files
    *
    * @param fastqPath
    * @param sampleSheet
    * @return
    */
  def producedFastqFilesBySampleID(fastqPath: Path, sampleSheet: SampleSheet): Map[SampleInfo, Set[File]] = {
    require(fastqPath.toFile.exists(), s"$fastqPath does not seem to exist. ")
    logger.debug(s"looking for files in: $fastqPath")

    val fqfsid = sampleSheet.data.sampleSheetSample
      .map(d ⇒ (d.data.get(sampleProject), d.data.get(sampleID), d.data.get(sampleName)))
      .filter(d ⇒ d._1.isDefined && d._2.isDefined && d._3.isDefined)
      .map(d ⇒ SampleInfo(id = d._2.get.trim, name = d._3.get.trim, project = d._1.get.trim))
      .map(d ⇒ (d, (fastqPath resolve d.project resolve d.id).toFile)).filter(_._2.exists())
      .map(d ⇒ (d._1, d._2.listFiles.filter(_.getName.startsWith(d._1.name))
        .filter(_.getName.endsWith(".fastq.gz")).toSet)).toMap

    logger.info(s"produced ${fqfsid.size} fastq files by sample IDs.")

    fqfsid
  }


  def getSampleSheetFile(metaFolder: Path): Option[File] = {
    val allInMeta = metaFolder.toFile.listFiles
    logger.debug(s"trying to find sample sheet in ${allInMeta.map(_.getAbsolutePath).mkString(" ; ")}")

    val sampSh = allInMeta.find(fn ⇒
      fn.getName.toLowerCase.contains("sample") && fn.getName.toLowerCase.contains("sheet"))
      .fold(allInMeta.find(_.getName.endsWith("csv")))(Some(_)) //todo there must be something better

    logger.debug(s"found sample sheet: ${sampSh.toString}")

    sampSh
  }


  def main(args: Array[String]): Unit = {
    //    println(readSampleSheet(getSampleSheetFile(new File("/home/deffabe1/Downloads/").toPath).get.toPath))
    //    println(readSampleSheet(new File("/home/deffabe1/Downloads/sams.csv").toPath))
//    println(readSampleSheet(new File("./test_data/SOLO_test_sample_sheet.csv").toPath))
    println(readSampleSheet(new File("./test_data/sample_sheet_test1.csv").toPath))
  }

}


case class SampleSheet(header: Map[String, String] = Map.empty, reads: List[Int] = List.empty,
                       settings: Map[String, String] = Map.empty, data: SampleSheetData = SampleSheetData())


case class SampleSheetSample(data: Map[String, String])


case class SampleSheetData(headers: List[String] = List.empty,
                           sampleSheetSample: List[SampleSheetSample] = List.empty)


case class SampleInfo(id: String, name: String, project: String)



