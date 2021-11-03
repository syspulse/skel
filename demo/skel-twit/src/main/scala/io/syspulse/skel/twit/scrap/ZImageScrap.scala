package io.syspulse.skel.twit.scrap

import scala.util.{Success, Failure, Random, Try}

import com.typesafe.scalalogging.Logger

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxBinary
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration;
import org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;
import scala.jdk.CollectionConverters._
import java.net.URL
import javax.imageio.ImageIO
import java.io.File

class ZImageScrap(imageDir:String = "/dev/shm") {

  val log = Logger(s"${this}")

  def scrap(url:String,headless:Boolean=false):Try[String] = {
    
    try {
      val driver = if(headless) {
        val firefoxBinary = new FirefoxBinary()
        firefoxBinary.addCommandLineOptions("--headless")
        val firefoxOptions = new FirefoxOptions()
        firefoxOptions.setBinary(firefoxBinary)
        new FirefoxDriver(firefoxOptions)
      } else new FirefoxDriver()

      try {
        val w = new WebDriverWait(driver, Duration.ofSeconds(10));

        log.info(s"browsing: ${url}")
        driver.get(url)

        w.until(presenceOfElementLocated(By.tagName("article")))

        Thread.sleep(5000)

        // get all divs
        val ss = driver.findElements(By.tagName("div")).asScala

        // find and click button to show
        try {
          ss.filter(_.getAttribute("role") == "button").filter(e => e.getText == "Anzeigen" || e.getText == "View").map(_.click)
        } catch {
          case e:Exception => log.warn(s"Ignoring Error: ${e.getMessage()}")
        }
        
        val ss2 = driver.findElements(By.tagName("div")).asScala.filter(e=>{ val a = e.getAttribute("style"); a!=null && a.startsWith("background-image") && a.contains("media")})
      
        val ii = ss2.map(_.getAttribute("style").stripPrefix("background-image: url(\"").stripSuffix("\");"))
        val i = ii.head

        val u = new URL(i)
        val image = ImageIO.read(u)
        val suffix = url.split("/").last

        //val imageFile = s"${imageDir}/tmp-${System.currentTimeMillis}-${suffix}.jpg"
        val imageFile = s"${imageDir}/tmp-${suffix}.jpg"
        
        ImageIO.write(image, "jpg", new File(imageFile));

        log.info(s"stored: ${url}: ${imageFile} ")        
        driver.close

        io.syspulse.skel.twit.App.metricImageCount.inc()
        Success(imageFile)
      } catch {
        case e:Exception => log.error(s"failed to scrap: ${url}",e); driver.close; Failure(e)
      }

    } catch {
      case e:Exception => log.error(s"failed to scrap: ${url}",e); Failure(e)
    }
  }

}
