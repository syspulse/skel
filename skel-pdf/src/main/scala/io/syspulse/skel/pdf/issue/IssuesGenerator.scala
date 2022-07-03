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
import io.syspulse.skel.util.Util
import io.syspulse.skel.pdf.issue._

class IssuesGenerator(issues:List[Issue]) extends PDFGenerator {
  
  override def setData(ctx:Context) = {
    //ctx.setVariable("signature", sig);
    ctx.setVariable("issues", issues.asJava);
  }

}
