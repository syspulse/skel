package io.syspulse.skel.serde

import java.io._

// This is needed because of Scala insantiy:
// https://github.com/scala/bug/issues/9237
// https://github.com/scala/scala/pull/7624
// https://github.com/xerial/airframe/commit/51926c89d41d6257cf4b8c17d75ae7a356688770
// Reference: https://gist.github.com/ramn/5566596
class ObjectInputStreamWithCustomClassLoader(inputStream: InputStream) extends ObjectInputStream(inputStream) {
  override def resolveClass(desc: java.io.ObjectStreamClass): Class[_] = {
    try { Class.forName(desc.getName, false, getClass.getClassLoader) }
    catch { case ex: ClassNotFoundException => super.resolveClass(desc) }
  }
}

object Serde {
  
  def serialize[T <: Serializable](obj: T): Array[Byte] = {
    val byteOut = new ByteArrayOutputStream()
    val objOut = new ObjectOutputStream(byteOut)
    objOut.writeObject(obj)
    objOut.close()
    byteOut.close()
    byteOut.toByteArray
  }

  def deserialize[T <: Serializable](bytes: Array[Byte]): T = {
    val byteIn = new ByteArrayInputStream(bytes)
    val objIn = new ObjectInputStreamWithCustomClassLoader(byteIn)
    val obj = objIn.readObject().asInstanceOf[T]
    byteIn.close()
    objIn.close()
    obj
  }
}


