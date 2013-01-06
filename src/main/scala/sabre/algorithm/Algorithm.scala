package sabre.algorithm

import scala.language.existentials
import scalax.collection.Graph
import scalax.collection.GraphEdge._

trait AbstractResult extends Serializable {
  override def toString = "You forgot to override the toString method for AbstractResult!"
}

case class MapResult(input: Any, output: Map[_ <: Any, Any]) extends AbstractResult {
  override def toString = output.map { p: (Any, Any) => input + " " + p._1 + " " + p._2 }.mkString("\n")
}

case class MapVectorResult(input: Any, output: Map[_ <: Any, Vector[Any]]) extends AbstractResult {
  override def toString = output.map { p: (Any, Vector[Any]) => input + " " + p._1 + " " + p._2.mkString(" ") }.mkString("\n")
}

case object NullResult extends AbstractResult {
  override def toString = ""
}

case class Result(input: Any, output: Any) extends AbstractResult {
  override def toString = input + " " + output
}

case class TupleInputResult(input: (Any, Any), output: Any) extends AbstractResult {
  override def toString = input._1 + " " + input._2 + " " + output
}

case class VectorResult(input: Any, output: Vector[Any]) extends AbstractResult {
  override def toString = input + " " + output.mkString(" ")
}

trait AbstractAlgorithm extends Serializable {
  def execute(graph: Graph[Int, UnDiEdge], input: Any): AbstractResult
}
