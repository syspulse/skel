package io.syspulse.skel.db

object AppShellDB {
  
  def main(args: Array[String]): Unit = {
    val cli = new ShellDB("jdbc:mysql://172.17.0.1:3306/medar_db")
    cli.shell()
  }
}