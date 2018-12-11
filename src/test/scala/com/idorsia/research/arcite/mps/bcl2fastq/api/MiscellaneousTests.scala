package com.idorsia.research.arcite.mps.bcl2fastq.api

import java.io.File
import java.nio.file.Path

import com.idorsia.research.arcite.mps.bcl2fastq.ProduceFastqFiles
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
  * Created by Bernard Deffarges on 2017/08/24.
  *
  */
class MiscellaneousTests  extends FlatSpec with Matchers with LazyLogging {

  def relativePathOfFastqFile(fastqFile: Path, transfFolder: Path): String = {
    transfFolder.relativize(fastqFile).toString
  }

  "relativizing fastq file path " should " return the fastq path at the transform level " in {

    val rel = relativePathOfFastqFile(
      new File("/arcite/home/experiments/com/idorsia/research/mps/nextseq/0d235311-987f-4cfa-ad17-e7af38a2d0a8/transforms/61cbcd04-c7f1-4019-8502-8664ca33739e/fastqFiles/170704_NB502046_0002_AHYM57BGX2/SOD1/1234/TG_SOD1_S1_L001_R1_001.fastq.gz")
        .toPath,

      new File("/arcite/home/experiments/com/idorsia/research/mps/nextseq/0d235311-987f-4cfa-ad17-e7af38a2d0a8/transforms/61cbcd04-c7f1-4019-8502-8664ca33739e/").toPath)

    assert(rel == "fastqFiles/170704_NB502046_0002_AHYM57BGX2/SOD1/1234/TG_SOD1_S1_L001_R1_001.fastq.gz")
  }

}
