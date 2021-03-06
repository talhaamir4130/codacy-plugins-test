package codacy.plugins.test.duplication

import codacy.plugins.test._
import com.codacy.analysis.core
import better.files._
import java.io.{File => JFile}

import codacy.plugins.test.resultprinter.ResultPrinter

import scala.util.Try
import scala.xml.XML
import com.codacy.analysis.core.model.DuplicationClone
import com.codacy.plugins.api.duplication.DuplicationTool.CodacyConfiguration
import com.codacy.plugins.duplication.traits.{DuplicationRunner, DuplicationTool}
import com.codacy.plugins.api
import com.codacy.plugins.runners.BinaryDockerRunner

import scala.concurrent.duration._

object DuplicationTests extends ITest {

  val opt = "duplication"

  def run(docsDirectory: JFile, dockerImage: DockerImage, optArgs: Seq[String]): Boolean = {
    debug(s"Running DuplicationTests:")
    val testsDirectory = docsDirectory.toScala / DockerHelpers.duplicationTestsDirectoryName
    ParallelCollectionsUtils
      .toPar(testsDirectory.list.toList)
      .map { testDirectory =>
        val srcDir = testDirectory / "src"
        val languages = findLanguages(srcDir.toJava, dockerImage)
        val duplicationTool = new DuplicationTool(languages.toList, dockerImage.name, dockerImage.version) {}
        val duplicationTools = languages.map(l => new core.tools.DuplicationTool(duplicationTool, l))
        val resultFile = testDirectory / "results.xml"
        val resultFileXML = XML.loadFile(resultFile.toJava)
        val (expectedResults, ignoreMessage) = CheckstyleFormatParser.parseResultsXml(resultFileXML)

        val results = duplicationTools.map(runDuplicationTool(srcDir, duplicationTool, _))
        (testDirectory.name, results, expectedResults, ignoreMessage)
      }
      .seq
      .map {
        case (directoryName, results, expectedResults, ignoreMessage) =>
          debug(s"${directoryName} should have ${expectedResults.size} results")
          if (ignoreMessage) {
            results.exists(res => ResultPrinter.printToolResults(ignoreClonedLines(res), expectedResults.toSet))
          } else {
            results.exists(ResultPrinter.printToolResults(_, expectedResults.toSet))
          }
      }
      .forall(identity)
  }

  private def ignoreClonedLines(res: Try[Set[DuplicationClone]]): Try[Set[DuplicationClone]] = {
    res.map(duplicationClones => duplicationClones.map(dupClone => dupClone.copy(cloneLines = "")))
  }

  private def runDuplicationTool(srcDir: File,
                                 duplicationTool: DuplicationTool,
                                 tool: com.codacy.analysis.core.tools.DuplicationTool): Try[Set[DuplicationClone]] = {
    val dockerRunner = new BinaryDockerRunner[api.duplication.DuplicationClone](duplicationTool)()
    val runner = new DuplicationRunner(duplicationTool, dockerRunner)

    for {
      duplicationClones <- runner.run(srcDir.toJava,
                                      CodacyConfiguration(Option(tool.languageToRun), Option.empty),
                                      15.minutes,
                                      None)
    } yield {
      duplicationClones.map(
        clone => DuplicationClone(clone.cloneLines, clone.nrTokens, clone.nrLines, clone.files.to[Set])
      )(collection.breakOut): Set[DuplicationClone]
    }
  }
}
