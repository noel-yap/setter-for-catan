package sfc.board

import akka.actor.{Props, ActorSystem}
import akka.pattern.ask
import akka.routing.SmallestMailboxRouter
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * @author noel.yap@gmail.com
 */
abstract class ValidCount {
  import ValidCount._

  protected def count: (Int => Pair[Int, Int])
  def probability: Double = {
    val c = count(sampleSize)

    c._1.asInstanceOf[Double] / c._2
  }
}

object ValidCount {
  // FIXME: OOM if sampleSize is too large
  private val sampleSize: Int = math.pow(6, 6).round.asInstanceOf[Int]

  private implicit val timeout = Timeout((sampleSize / 648).seconds)

  def apply(piecesConfigSpec: Configuration.PiecesConfigSpec*): (Int => Pair[Int, Int]) = { sampleSize: Int =>
    val system = ActorSystem("SetterForCatan")

    try {
      val validCountActor = system.actorOf(Props[ValidCountActor], "validCount")

      val numberOfGenerators = math.min(sampleSize, 127)
      val generateBoardActor = system.actorOf(
        Props[GenerateBoardActor].withRouter(SmallestMailboxRouter(nrOfInstances = numberOfGenerators)),
        "generateBoard")
      for (i <- 1 to sampleSize) {
        generateBoardActor.tell(GenerateBoardActor.GenerateBoard(piecesConfigSpec: _*), validCountActor)
      }

      val count = validCountActor ? ValidCountActor.GetResult(sampleSize)
      Await.result(count.mapTo[Pair[Int, Int]], timeout.duration)
    } finally {
      system.shutdown()
    }
  }
}
