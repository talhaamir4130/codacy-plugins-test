package codacy.plugins.test

import scala.collection.parallel.ParIterable

object ParallelCollectionsUtils {

  private[this] lazy val calcForkNum: Int = {
    val cpuCores = Runtime.getRuntime.availableProcessors()
    if (cpuCores > 2) cpuCores else 1
  }

  def forkJoinTaskSupport =
    new scala.collection.parallel.ForkJoinTaskSupport(new java.util.concurrent.ForkJoinPool(calcForkNum))

  def toPar[A](coll: Iterable[A]): ParIterable[A] = {
    val collPar = coll.par
    collPar.tasksupport = forkJoinTaskSupport
    collPar
  }
}
