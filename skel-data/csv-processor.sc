//#!/opt/amm/amm-2.12-2.2.0

// Scala 2.13
import $ivy.`org.scala-lang.modules::scala-parallel-collections:1.0.3`
import scala.collection.parallel.CollectionConverters._


// Parallel processor (scala 2.12)
import scala.collection.mutable.HashMap
import scala.collection.parallel.mutable._
import scala.collection.parallel._
import scala.io.Source


val dIn = HashMap()
val dOut = HashMap()


@main
def main(file:String, count:Int = 0) = {
  object Hanging {
    def res() = 
      if(count == 0) {
        println(s"Seq: ${file}")
        Source.fromFile(file).getLines.map(_.split(",")).map(v=>{ 
          val (b,i,o)=(v(0).toInt,v(1).toInt,v(2).toInt)
          if(i%500000 == 0) print(s"thr-${Thread.currentThread.getId}(${i}).")
          (b,i,o)
        }).size
      } else {
        println(s"Par: ${file}")
        os.list(os.pwd).filter(_.ext=="csv").filter(_.baseName.startsWith(file)).map(_.toString).take(count).map(f=>{println(s"File: ${f}");f}).map(
          Source.fromFile(_).getLines()).par.map(f=> f.map(_.split(",")).map( v=>{
            //val (i,j,s)=(v(0).toInt,v(1).toInt,v(2)); (i,j,s)
            val (b,i,o)=(v(0).toInt,v(1).toInt,v(2).toInt)
            if(i%500000 == 0) print(s"thr-${Thread.currentThread.getId}(${i}).")
            (b,i,o)
          }).size).reduce(_ + _)
      }
  }
  println(s"\n${Hanging.res()}")
}