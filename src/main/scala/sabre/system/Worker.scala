package sabre.system

import akka.actor.{ Actor, ActorLogging, ActorPath, ActorRef, ActorSystem, Props }
import akka.pattern.pipe
import akka.remote.RemoteClientLifeCycleEvent
import com.typesafe.config.ConfigFactory
import java.net.InetAddress
import sabre.algorithm._
import sabre.system.Master._
import sabre.system.Reaper._
import sabre.system.ResultHandler._
import sabre.util.{ FromEdgelist, ParseConfig }
import scala.Console.err
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.util.{ Failure, Success, Try }
import scalax.collection.Graph
import scalax.collection.GraphPredef._
import scalax.collection.GraphEdge._

object Worker {
  case class DoAlgorithm(algorithm: AbstractAlgorithm)
  case class WorkToBeDone(work: Any)
  case object WorkIsReady
  case object NoWorkToBeDone

  val workerAkkaConfig = ConfigFactory.parseString("""
    akka {
      actor {
        provider = "akka.remote.RemoteActorRefProvider"
        serialization-bindings {
          "scala.collection.Map" = java
          "scalax.collection.Graph" = java
        }
      }
      remote {
        netty {
          hostname = """ + "\"" + InetAddress.getLocalHost().getHostName() + "\"" + """
          message-frame-size = 500 MiB
          port = 2552
        }
      }
    }
  """)

  def main(args: Array[String]) {
    val availableProcessors = Runtime.getRuntime().availableProcessors()
    val hostname = InetAddress.getLocalHost().getHostName()
    val masterServerAddress = ParseConfig.config.getString("sabre.master")

    val numberOfThreads = ParseConfig.config.getInt("sabre.deploy." + hostname + ".nr-of-workers")

    val system = ActorSystem("Worker", ConfigFactory.load(workerAkkaConfig))
    val graph = FromEdgelist.undirectedGraph()
    val masterLocation = "akka://Sabre@" + masterServerAddress + ":2554/user/master"
    val master = system.actorFor(masterLocation)
    val reaper = system.actorOf(Props[Reaper], "reaper")

    for (i <- 0 until numberOfThreads) {
      val workerName = "worker" + i
      val workerRef = system.actorOf(Props(new Worker(graph, master)), workerName)
      reaper ! WatchMe(workerRef)
    }

    println(numberOfThreads + " worker(s) started on " + hostname)
  }
}

class Worker(graph: Graph[Int, UnDiEdge], master: ActorRef) extends Actor with ActorLogging {
  import Worker._
  case object WorkComplete

  var algorithm: Option[AbstractAlgorithm] = None

  def doWork(resultHandler: ActorRef, work: Any) {
    future {
      val result = Try { algorithm.get.execute(graph, work) }
      result match {
        case Success(res) => resultHandler ! HandleResult(res)
        case Failure(exception) => log.error("Input {} resulted in exception {}.", work, exception.getMessage())
      }

      WorkComplete
    } pipeTo self
  }

  override def preStart() = {
    master ! WorkerCreated(self)
  }

  def working: Receive = {
    case WorkIsReady =>
    case NoWorkToBeDone =>
    case WorkToBeDone =>
      log.error("Received work while working.")
    case WorkComplete =>
      master ! WorkIsDone(self)
      master ! WorkerRequestsWork(self)
      context.become(idle)
  }

  def idle: Receive = {
    case DoAlgorithm(alg) =>
      algorithm = Some(alg)
    case WorkIsReady =>
      master ! WorkerRequestsWork(self)
    case WorkToBeDone(work) =>
      algorithm match {
        case None =>
          log.error("Got work {} without algorithm!", work)
        case Some(alg) =>
          val resultHandler = sender
          doWork(resultHandler, work)
          context.become(working)
      }
    case NoWorkToBeDone =>
  }

  override def receive = idle
}
