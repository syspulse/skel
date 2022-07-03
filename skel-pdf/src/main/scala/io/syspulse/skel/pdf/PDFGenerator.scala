package io.syspulse.skel.pdf

import scala.jdk.CollectionConverters._

import java.io.FileOutputStream
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.xhtmlrenderer.pdf.ITextRenderer
import org.thymeleaf.templatemode.TemplateMode

import org.xhtmlrenderer.pdf.ITextOutputDevice;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.ITextUserAgent;
import org.xhtmlrenderer.resource.XMLResource;

import java.io.OutputStream
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.InputStream
import java.io.IOException
import java.io.File

import com.typesafe.scalalogging.Logger

import os._
import org.thymeleaf.templateresolver.FileTemplateResolver
import io.syspulse.skel.util.Util
import io.syspulse.skel.pdf.issue._
import org.jsoup.Jsoup
import org.jsoup.nodes

class PDFGenerator {
  val log = Logger(s"${this}")

  protected def setData(ctx:Context) = {}

  def generateData(template:String) = {
    //val templateResolver = new ClassLoaderTemplateResolver();
    val templateResolver = new FileTemplateResolver();
    templateResolver.setPrefix("")
    templateResolver.setSuffix(".html");
    
    //templateResolver.setTemplateMode(TemplateMode.HTML);
    templateResolver.setTemplateMode(TemplateMode.HTML5); // it is needed to be compatible with '>' pecularity

    val templateEngine = new TemplateEngine();
    templateEngine.setTemplateResolver(templateResolver);

    val ctx = new Context();
    
    setData(ctx)

    templateEngine.process(template, ctx);
  }

  class ResourceLoaderUserAgent(outputDevice: ITextOutputDevice) extends ITextUserAgent(outputDevice)
  {
    override def resolveAndOpenStream(uri:String):InputStream = {
      val is: InputStream = super.resolveAndOpenStream(uri);
      log.info(s"resolve: ${uri} -> ${is}");
      return is;
    }
  }

  def create(inputPath:String, fileName:String)= {
    var os:OutputStream = null;
    val url = pathToUri(inputPath)
    try {
      os = new FileOutputStream(fileName);

      /* standard approach
      ITextRenderer renderer = new ITextRenderer();
      renderer.setDocument(url);
      renderer.layout();
      renderer.createPDF(os);
      */

      val renderer = new ITextRenderer();
      val callback = new ResourceLoaderUserAgent(renderer.getOutputDevice());
      callback.setSharedContext(renderer.getSharedContext());
      renderer.getSharedContext ().setUserAgentCallback(callback);

      val doc:Document = XMLResource.load(new InputSource(url)).getDocument();

      renderer.setDocument(doc, url);
      renderer.layout();
      renderer.createPDF(os);

      os.close();
      os = null;
    } finally {
      if (os != null) {
        try {
          os.close()
        } catch {
          case e: IOException => 
        }
      }
    }
  }

  def pathToUri(url:String) = {
    if (url.indexOf("://") == -1) {        
      val f = new File(url);
      if (f.exists()) {
        f.toURI().toURL().toString();
      } else ""
    } else url
  }

  def generate(templateDir:String,outputFile:String="output.pdf", templateFile:String = "template.html") = {
    val outputTemplate = s"${templateDir}/output.html"
    
    val html = generateData(templateDir + "/" + templateFile)

    // fix HTML5 tag for SAX parser (it does not like <hr> or no </tag>)
    val document = Jsoup.parse(html)
    document.outputSettings().syntax(nodes.Document.OutputSettings.Syntax.xml);
    val html2 = document.html()

    os.write.over(os.Path(outputTemplate,os.pwd),html2)
    
    create(outputTemplate ,outputFile);
  }
}
