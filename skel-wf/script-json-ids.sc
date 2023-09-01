val json = input.toString
ujson.read(json).arr.map(o=>o.obj("id").str).mkString(";")