val json = """{input}"""
ujson.read(json).arr.map(o=>o.obj("id").str).mkString(";")