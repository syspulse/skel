package io.syspulse.skel.cli

// ----------------------------------------- Colors ---
trait Colorful {
  val RESET:String = "\u001B[0m"

  // Bold \u001b[1m
  val BOLD:String = "\u001B[1m"
  // Underline
  val UNDERLINED:String = "\u001B[4;30m"
  // Background
  val BACKGROUND:String = "\u001B[40m"
  // High Intensity
  val BRIGHT:String = "\u001B[0;90m"
  // Bold High Intensity
  val BOLD_BRIGHT:String = "\u001B[1m;90m"
  // High Intensity backgrounds
  val BACKGROUND_BRIGHT:String = "\u001B[0;100m"

  // Regular Colors
  val BLACK:String = "\u001B[0;30m"
  val RED:String = "\u001B[0;31m"
  val GREEN:String = "\u001B[0;32m"
  val YELLOW:String = "\u001B[0;33m"
  val BLUE:String = "\u001B[0;34m" 
  val PURPLE:String = "\u001B[0;35m"
  val CYAN:String = "\u001B[0;36m"
  val WHITE:String = "\u001B[0;37m"
  
  val ERR:String
  val KEY:String
  val VALUE:String
  val SEARCH:String
}


case class ColorfulDay() extends Colorful {
  val ERR:String = RED
  val KEY:String = BLUE
  val VALUE:String = BLACK + BOLD
  val SEARCH:String = BLUE
}

case class ColorfulNight () extends Colorful {
  val ERR:String = RED
  val KEY:String = YELLOW
  val VALUE:String = WHITE + BOLD
  val SEARCH:String = BLUE
}

object CliColors {
  val colors = Map(
    "day" ->  ColorfulDay(),
    "night" -> ColorfulNight() 
  )

  def getColorful(name:String):Colorful = colors.get(name).getOrElse(ColorfulNight())
}


