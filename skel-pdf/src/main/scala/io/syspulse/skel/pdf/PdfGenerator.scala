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

import laika.io._
import laika.io.implicits._
import laika.parse.code._
import laika.api._
import laika.format._
import laika.markdown.github._

import com.typesafe.scalalogging.Logger

import os._
import org.thymeleaf.templateresolver.FileTemplateResolver

case class Issue(title:String,desc:String,severity:Int,where:String,recommend:String,status:String)

object Issue {
  val transformer = Transformer.from(Markdown).to(HTML).using(GitHubFlavor,SyntaxHighlighting).build
  def apply(title:String, desc:String = "", severity:Int = 4,where:String="",recommend:String="",status:String="New") = {    
    val descHtml = 
      transformer.transform(desc) match {
        case Left(e) => desc
        case Right(html) => html
      }
    println(s"=======> ${descHtml}")
    new Issue(title,descHtml,severity,where,recommend,status)
  }
}

object PDF {
  val log = Logger(s"${this}")

  def generateIssues(template:String,issues:List[Issue],sectionTitle:String = "") = {
    //val templateResolver = new ClassLoaderTemplateResolver();
    val templateResolver = new FileTemplateResolver();
    templateResolver.setPrefix("")
    templateResolver.setSuffix(".html");
    
    //templateResolver.setTemplateMode(TemplateMode.HTML);
    templateResolver.setTemplateMode(TemplateMode.HTML5); // it is needed to be compatible with '>' pecularity

    val templateEngine = new TemplateEngine();
    templateEngine.setTemplateResolver(templateResolver);

    val ctx = new Context();
    ctx.setVariable("section_title", sectionTitle);
    ctx.setVariable("issues", issues.asJava);

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
    
    val html = PDF.generateIssues(
      templateDir + "/" + templateFile,
      Range(1,5).map(i => Issue(s"Issue ${i}",desc="### Description\n__Text__: 100")).toList)

    os.write.over(os.Path(outputTemplate,os.pwd),html)
    
    PDF.create(outputTemplate ,outputFile);
  }
}

object PdfGenerator extends App {
  
  var templateDir = args.headOption.getOrElse("templates/T0")
  val outputFile = "output.pdf"
  
  PDF.generate(templateDir,outputFile);
}
