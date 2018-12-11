package com.idorsia.research.arcite.mps.bcl2fastq.api

import java.io.File

import com.idorsia.research.arcite.mps.bcl2fastq.{SampleInfo, SampleSheetHelper}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FlatSpec, Matchers}

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
class SampleSheetTests extends FlatSpec with Matchers with LazyLogging {

  val sampleSheet = SampleSheetHelper.readSampleSheet(new File("./test_data/SOLO_Nasal_Fluid_test.csv").toPath)
//  val sampleSheet = SampleSheetHelper.readSampleSheet(new File("./test_data/sample_sheet_test1.csv").toPath)
//  val sampleSheet = SampleSheetHelper.readSampleSheet(new File("./test_data/sample_sheet").toPath)
  //  val sampleSheet = SampleSheetHelper.readSampleSheet(
  //    SampleSheetHelper.getSampleSheetFile(new File("/home/deffabe1/Downloads").toPath).get.toPath)

  "reading a sample sheet file " should " produce a proper sample sheet structure " in {

    assert(sampleSheet.header.get("IEMFileVersion").isDefined)
    assert(sampleSheet.header("IEMFileVersion") == "4")
    assert(sampleSheet.header.get("Workflow").isDefined)
    assert(sampleSheet.header("Workflow") == "GenerateFASTQ")
    assert(sampleSheet.header.get("Description").isDefined)
    assert(sampleSheet.header("Description") == "SOD1 model genomic DNA sequencing")

    assert(sampleSheet.reads.head == 154)
    assert(sampleSheet.reads.last == 154)

    assert(sampleSheet.data.headers.indexOf("Sample_ID") == 0)
    assert(sampleSheet.data.headers.indexOf("Sample_Name") == 1)
    assert(sampleSheet.data.headers.indexOf("Sample_Plate") == 2)
    assert(sampleSheet.data.headers.indexOf("Sample_Well") == 3)
    assert(sampleSheet.data.headers.indexOf("Description") == 7)

    assert(sampleSheet.data.sampleSheetSample.head.data.get("Sample_ID").isDefined)
    assert(sampleSheet.data.sampleSheetSample.head.data("Sample_ID") == "1234")

    assert(sampleSheet.data.sampleSheetSample.head.data.get("Sample_Name").isDefined)
    assert(sampleSheet.data.sampleSheetSample.head.data("Sample_Name") == "TG_SOD1")

    assert(sampleSheet.data.sampleSheetSample.head.data.get("index").isDefined)
    assert(sampleSheet.data.sampleSheetSample.head.data("index") == "CGATGT")

    assert(sampleSheet.data.sampleSheetSample.head.data.get("Description").isDefined)
    assert(sampleSheet.data.sampleSheetSample.head.data("Description") == "SOD1genomicSequencing")

    assert(sampleSheet.data.sampleSheetSample.head.data.get("Sample_Project").isDefined)
    assert(sampleSheet.data.sampleSheetSample.head.data("Sample_Project") == "SOD1")
  }

  "asking for all fastq files produced based on folder and sample sheet " should
    "return a list of paths of all fastq.gz files " in {

    val files = SampleSheetHelper.allProducedFastqFiles(
      Set(new File("./test_data/fastqFiles/folder1").toPath,
        new File("./test_data/fastqFiles/folder2").toPath), sampleSheet)

    assert(files.size == 16)
    assert(files.map(_.toString).contains("./test_data/fastqFiles/folder1/SOD1/1234/TG_SOD1_S1_L001_R1_001.fastq.gz"))
    assert(files.map(_.toString).contains("./test_data/fastqFiles/folder2/SOD1/1234/TG_SOD1_S1_L001_R2_001.fastq.gz"))
    assert(files.map(_.toString).contains("./test_data/fastqFiles/folder1/SOD1/1234/TG_SOD1_S1_L004_R2_001.fastq.gz"))
    assert(files.map(_.toString).contains("./test_data/fastqFiles/folder2/SOD1/1234/TG_SOD1_S1_L004_R2_001.fastq.gz"))
  }

  val sample1 =
    """[Header]
      |IEMFileVersion,4
      |Investigator Name,renaulb
      |Experiment Name,ARSeq_170925
      |Date,9/25/2017
      |Workflow,GenerateFASTQ
      |Application,NextSeq FASTQ Only
      |Assay,TruSeq LT
      |Description,mRNA seq test
      |Chemistry,Default
      |
      |[Reads]
      |76
      |
      |[Settings]
      |
      |[Data]
      |Sample_ID,Sample_Name,Sample_Plate,Sample_Well,I7_Index_ID,index,Sample_Project,Description
      |1,10ng_Spk1,,,A04,ACGGACTT,ARS_25092017,
      |2,50ng_Spk1,,,B04,CTAAGACC,ARS_25092017,
      |3,100ng_Spk1,,,C04,AACCGAAC,ARS_25092017,
      |4,10ng_Spk2,,,D04,CCTTAGGT,ARS_25092017,
      |5,50ng_Spk2,,,E04,CCTATACC,ARS_25092017,
      |6,100ng_Spk2,,,F04,AACGCCTT,ARS_25092017,
      |
    """.stripMargin


  "find the right files for sampleID " should " return a list of file per sample " in {
    val samS = SampleSheetHelper.readSampleSheet(new File("./test_data/fastqFiles2/sampleSheet").toPath)
    val sampToFiles = SampleSheetHelper
      .producedFastqFilesBySampleID(new File("./test_data/fastqFiles2").toPath, samS)

    assert(sampToFiles(SampleInfo("1","ARS_25092017","folder1")).exists(_.getName == "10ng_Spk1_S1_L001_R1_001.fastq.gz"))
    assert(sampToFiles(SampleInfo("1","ARS_25092017","folder1")).exists(_.getName == "10ng_Spk1_S1_L002_R1_001.fastq.gz"))
    assert(sampToFiles(SampleInfo("2","ARS_25092017","folder1")).exists(_.getName == "50ng_Spk1_S2_L002_R1_001.fastq.gz"))
    assert(sampToFiles(SampleInfo("3","ARS_25092017","folder1")).exists(_.getName == "100ng_Spk1_S3_L001_R1_001.fastq.gz"))
    assert(sampToFiles(SampleInfo("3","ARS_25092017","folder1")).exists(_.getName == "100ng_Spk1_S3_L004_R1_001.fastq.gz"))
    assert(sampToFiles(SampleInfo("4","ARS_25092017","folder1")).exists(_.getName == "10ng_Spk2_S4_L001_R1_001.fastq.gz"))
    assert(sampToFiles(SampleInfo("4","ARS_25092017","folder1")).exists(_.getName == "10ng_Spk2_S4_L003_R1_001.fastq.gz"))
    assert(sampToFiles(SampleInfo("5","ARS_25092017","folder1")).exists(_.getName == "50ng_Spk2_S5_L002_R1_001.fastq.gz"))
    assert(sampToFiles(SampleInfo("5","ARS_25092017","folder1")).exists(_.getName == "50ng_Spk2_S5_L003_R1_001.fastq.gz"))
    assert(sampToFiles(SampleInfo("6","ARS_25092017","folder1")).exists(_.getName == "100ng_Spk2_S6_L004_R1_001.fastq.gz"))
  }
}
