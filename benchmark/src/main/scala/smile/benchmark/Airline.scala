/*******************************************************************************
  * Copyright (c) 2010 Haifeng Li
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  *******************************************************************************/
package smile.benchmark

import smile.data._
import smile.data.parser.DelimitedTextParser
import smile.classification._
import smile.io._
import smile.math.Math
import smile.validation._
import smile.util._

/**
 *
 * @author Haifeng Li
 */
object Airline {

  def main(args: Array[String]): Unit = {
    benchmark("0.1m")
    benchmark("1m")
  }

  def benchmark: Unit = {
    benchmark("0.1m")
    benchmark("1m")
  }

  def benchmark(data: String): Unit = {
    println("Airline")

    val parser = new DelimitedTextParser()
    parser.setDelimiter(",")
    parser.setColumnNames(true)
    parser.setResponseIndex(new NominalAttribute("class"), 8)

    val attributes = new Array[Attribute](8)
    attributes(0) = new NominalAttribute("V0")
    attributes(1) = new NominalAttribute("V1")
    attributes(2) = new NominalAttribute("V2")
    attributes(3) = new NumericAttribute("V3")
    attributes(4) = new NominalAttribute("V4")
    attributes(5) = new NominalAttribute("V5")
    attributes(6) = new NominalAttribute("V6")
    attributes(7) = new NumericAttribute("V7")

    val train = parser.parse(attributes, smile.data.parser.IOUtils.getTestDataFile(s"airline/train-${data}.csv"))
    val test  = parser.parse(attributes, smile.data.parser.IOUtils.getTestDataFile("airline/test.csv"))

    attributes.foreach { attr =>
      if (attr.isInstanceOf[NominalAttribute])
        println(attr.getName + attr.asInstanceOf[NominalAttribute].values.mkString(", "))
    }
    println("class: " + train.response.asInstanceOf[NominalAttribute].values.mkString(", "))
    println("train data size: " + train.size + ", test data size: " + test.size)

    val (x, y) = train.unzipInt
    val (testx, testy) = test.unzipInt

    val pos = Math.sum(y)
    val testpos = Math.sum(testy)
    println(s"train data positive : negative =  $pos : ${y.length - pos}")
    println(s"test  data positive : negative =  $testpos : ${testy.length - testpos}")

    /*
    // manual discretize numeric attribute to match other systems.
    // SMILE doesn't need this actually.
    val x3 = x.map(_(3))
    val x7 = x.map(_(7))
    val testx3 = testx.map(_(3))
    val testx7 = testx.map(_(3))
    val min3 = Math.min(Math.min(x3: _*), Math.min(testx3: _*))
    val max3 = Math.max(Math.max(x3: _*), Math.max(testx3: _*))
    val min7 = Math.min(Math.min(x7: _*), Math.min(testx7: _*))
    val max7 = Math.max(Math.max(x7: _*), Math.max(testx7: _*))
    val x3i = 15//(max3 - min3 + 0.1) / 50
    val x7i = (max7 - min7 + 0.1) / 50
    x.foreach { xi =>
      xi(3) = Math.floor(xi(3) / 25 + (xi(3) % 100) / 15).toInt //Math.floor((xi(3) - min3) / x3i).toInt
      xi(7) = Math.floor(xi(7) / 50).toInt //Math.floor((xi(7) - min7) / x7i).toInt
    }
    testx.foreach { xi =>
      xi(3) = Math.floor(xi(3) / 25 + (xi(3) % 100) / 15).toInt //Math.floor((xi(3) - min3) / x3i).toInt
      xi(7) = Math.floor(xi(7) / 50).toInt //Math.floor((xi(7) - min7) / x7i).toInt
    }
    println(min3, max3, min7, max7)
    println(Math.min(x.map(_(3)): _*), Math.min(testx.map(_(3)): _*))
    println(Math.max(x.map(_(3)): _*), Math.max(testx.map(_(3)): _*))
    println(Math.min(x.map(_(7)): _*), Math.min(testx.map(_(7)): _*))
    println(Math.max(x.map(_(7)): _*), Math.max(testx.map(_(7)): _*))

    attributes(3) = new NominalAttribute("V3", (0 until 107).map(_.toString).toArray)
    attributes(7) = new NominalAttribute("V7", (0 until 100).map(_.toString).toArray)
*/
    // The data is unbalanced. Large positive class weight of should improve sensitivity.
    val classWeight = Array(1, 3)

    // Random Forest
    val forest = test2(x, y, testx, testy) { (x, y) =>
      println("Training Random Forest of 500 trees...")
      if (x.length <= 100000)
        new RandomForest(attributes, x, y, 500, 400, 5, 2, 0.632, DecisionTree.SplitRule.ENTROPY, classWeight)
      else
        new RandomForest(attributes, x, y, 500, 650, 5, 2, 0.632, DecisionTree.SplitRule.ENTROPY, classWeight)
    }.asInstanceOf[RandomForest]

    println("OOB error rate = %.2f%%" format (100.0 * forest.error()))
    for (i <- 0 until attributes.length) {
      println(s"importance of ${attributes(i).getName} = ${forest.importance()(i)}")
    }

    // Gradient Tree Boost
    test2(x, y, testx, testy) { (x, y) =>
      println("Training Gradient Boosted Trees of 300 trees...")
      new GradientTreeBoost(attributes, x, y, 300, 6, 0.1, 0.5)
    }

    // AdaBoost
    test2(x, y, testx, testy) { (x, y) =>
      println("Training AdaBoost of 300 trees...")
      new AdaBoost(attributes, x, y, 300, 6)
    }
  }
}