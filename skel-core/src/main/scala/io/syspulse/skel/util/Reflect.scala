package io.syspulse.skel.util

import scala.reflect.runtime.{universe => ru}

object Reflect {
  
  implicit class ForAnyInstance[T: ru.TypeTag](i: T)(implicit c: scala.reflect.ClassTag[T]) {

    /* a mirror sets a scope of the entities on which we have reflective access */
    val mirror = ru.runtimeMirror(getClass.getClassLoader)

    /* here we get an instance mirror to reflect on an instance */
    val im = ru.runtimeMirror(i.getClass.getClassLoader)

    def valueOf[V](name: String):Option[V] = {
      ru.typeOf[T].members.filter(!_.isMethod).filter(_.name.decoded.trim.equals(name)).map(s => {
        val fieldValue = im.reflect(i).reflectField(s.asTerm).get

        /* typeSignature contains runtime type information about a Symbol */
        s.typeSignature match {
          // case x if x =:= ru.typeOf[String] => fieldValue.asInstanceOf[String]
          // case x if x =:= ru.typeOf[Int] =>  fieldValue.asInstanceOf[Int]
          // case x if x =:= ru.typeOf[Boolean] => fieldValue.asInstanceOf[Boolean]
          // case x if x =:= ru.typeOf[Long] => fieldValue.asInstanceOf[Long]
          // case x if x =:= ru.typeOf[Double] => fieldValue.asInstanceOf[Double]
          // case x if x =:= ru.typeOf[BigInt] => fieldValue.asInstanceOf[BigInt]
          
          // case x if x =:= ru.typeOf[Seq[_]] => fieldValue.asInstanceOf[Seq[_]]
          // case x if x =:= ru.typeOf[List[_]] => fieldValue.asInstanceOf[List[_]]
          // case x if x =:= ru.typeOf[Map[_,_]] => fieldValue.asInstanceOf[Map[_,_]]

          case _ => fieldValue.asInstanceOf[V]
        }
      }).headOption
    }
  }

  trait CaseClassReflector extends Product {
    def getFields = getClass.getDeclaredFields.map(field => {
      field setAccessible true
      field.getName -> field.get(this)
    })
  }

  def classAccessors[T: ru.TypeTag]: List[ru.MethodSymbol] = ru.typeOf[T].members.collect {
    case m: ru.MethodSymbol if m.isCaseAccessor => m
  }.toList
}

