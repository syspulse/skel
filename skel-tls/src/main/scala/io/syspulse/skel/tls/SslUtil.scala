package io.syspulse.skel.tls

/* 
 All Credits
 https://github.com/EMCECS/ssl-certificate-extractor.git
 License: Apache License, Version 2.0
*/

import scala.jdk.CollectionConverters._
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger
import java.time._
import java.time.format._

import os._

import javax.net.ssl._
import java.io._
import java.net.Socket
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.security._
import java.security.cert._
import java.util.Base64
//import java.util.Set

object SslUtil {
  def resolve(domain:String) = {
    val extractor = new SslCertificateExtractor(domain)
    extractor.resolve()
  }
}

case class SslResult(trusted:Boolean,valid:Boolean,cert:X509Certificate)

class SslCertificateExtractor(domain:String,verifyCert:Option[String]=None) {
  val log = Logger(s"${this}")
  
  val BEGIN_CERT = "-----BEGIN CERTIFICATE-----";
  val END_CERT = "-----END CERTIFICATE-----";

  def resolve():Try[SslResult] = {
    run()
  }
  
  protected var lastIssuer: Principal = null;
  protected var lastSubject: Principal = null;
  protected var lastCert:X509Certificate = null;
  protected var rootCert:X509Certificate = null;
  protected var certToVerify:X509Certificate = null;
  protected var serverCerts:Int = 0;
  
  private def run():Try[SslResult] = {

      val (host, port) = domain.split(":").toList match {
        case host :: port :: Nil => (host,port.toInt)
        case host :: Nil =>  (host,443)
        case _ => return Failure(new Exception(s"invalid host: ${domain}"))
      }
          
      try {
          val ctx:SSLContext = SSLContext.getInstance("TLS")
          val tm = new CustomTrustManager()
          ctx.init(null, 
            //new TrustManager[]{ new CustomTrustManager()},
            Array(tm),
            null
          );

          log.info("Loading root certificates...");
          
          val anchors:Set[TrustAnchor] = getTrustAnchors();

          if(verifyCert.isDefined) {
              log.info(s"Loading certificate: '${verifyCert.get}'")
              val f:File = new File(verifyCert.get);
              if(!f.exists()) {
                return Failure(new Exception(s"Certificate not found: ${verifyCert.get}"))
              }
              try {
                val in:InputStream = new FileInputStream(f)
                val certificateFactory:CertificateFactory = CertificateFactory.getInstance("X.509");
                certToVerify = certificateFactory.generateCertificate(in).asInstanceOf[X509Certificate]
                in.close()
              } catch {
                case e:Exception =>                  
                  return Failure(new Exception(s"failed not load certificate: ${verifyCert.get}",e))
              }
          }

          log.info(s"system root certificates: ${anchors.size}");

          log.info(s"Connecting -> ${host}:${port}...")
          val s:Socket = ctx.getSocketFactory().createSocket(host,port);
          val ss:OutputStream = s.getOutputStream();
          ss.write("GET / HTTP/1.1\n\n".getBytes());
          ss.close();
          s.close();

          log.info(s"${host}:${port}: server certificates=${serverCerts}: root=${lastIssuer.getName()}")

          var valid = true
          var trusted = true

          if(lastIssuer.equals(lastSubject)) {
              // The last certificate was self-signed.  This could either be a single self-signed cert or the root
              // cert (root CA certs are always self-signed since they're the trust anchor).
              if(serverCerts == 1) {                  
                rootCert = lastCert;
                val anchor:X509Certificate = findAnchor(anchors, lastIssuer)
                trusted = anchor != null
                
                log.info(s"${host}:${port}: self-signed certificate: trusted=${trusted}")
                  
              } else {
                log.warn(s"${host}:${host}: missing root certificate")
                rootCert = lastCert;
                
                val anchor:X509Certificate = findAnchor(anchors, lastIssuer);
                if(anchor == null) {
                  valid = false
                  trusted = false
                  log.warn(s"${host}:${port}: root certificate: NOT TRUSTED")
                } else {
                  // Java also has the cert... use Java's version since we trust that more.
                  rootCert = anchor;                    
                }
              }
          } else {
              // Server didn't send the root CA cert.  See if Java recognizes it.
              val anchor:X509Certificate = findAnchor(anchors, lastIssuer);
              if(anchor == null) {
                // Java doesn't have it... did the user give us a cert to test?
                if(verifyCert != null) {
                  if(certToVerify.getSubjectDN().equals(lastIssuer)) {
                    log.warn(s"${host}:${port}: root certificate: valid but NOT TRUSTED")
                    rootCert = certToVerify;
                    trusted = false
                  } else {
                    // Java doesn't have this certificate as a trusted certificate AND " +
                    //         "the certificate you passed to verify does not appear to match the required " +
                    //         "root certificate.");
                    trusted = false
                    valid = false
                    log.warn(s"${host}:${port}: certificate=${certToVerify.getSubjectDN()}: required=${rootCert.getSubjectDN()}")                        
                    return Failure(new Exception(s"Certificate: ${certToVerify.getSubjectDN()}\nRequired root: ${rootCert.getSubjectDN()}"))
                  }
                } else {
                  // "Java doesn't have this certificate as a trusted certificate.  This may " +
                  //         "happen if you're not using a common CA (Certificate Authority) or your " +
                  //         "organization runs its own CA.  Please contact your security administrator and " +
                  //         "tell them you're looking for the root certificate for " + lastIssuer)
                  trusted = false
                  valid = false
                  return Failure(new Exception(s"Root CA is not trusted by system: ${lastIssuer}"))
                }
              } else {
                log.info(s"${host}:${port}: server didn't send the CA cert (normal), but system recognizes it as trusted.")
                trusted = true
                rootCert = anchor;
              }
          }

          Success(SslResult(trusted, valid, rootCert))

      } catch {
        case e:Exception => Failure(e)        
      }
  }
  
  def writeCert(cert: X509Certificate, out:OutputStream) = {
    //val encoder:Base64.Encoder = Base64.getMimeEncoder(64, new byte[]{0x0a})
    val encoder:Base64.Encoder = Base64.getMimeEncoder(64, Array[Byte](0x0a))
    out.write(BEGIN_CERT.getBytes(StandardCharsets.US_ASCII));
    out.write(0x0a);  // Newline
    out.write(encoder.encode(cert.getEncoded()))
    out.write(0x0a);  // Newline
    out.write(END_CERT.getBytes(StandardCharsets.US_ASCII))
    out.write(0x0a);  // Newline      
  }

  def saveCert(cert:X509Certificate,fileName:String):Try[String] = {
    try {
      val out:FileOutputStream = new FileOutputStream(new File(fileName))
      writeCert(cert,out)      
      val pem = os.read(os.Path(fileName,os.pwd))
      Try(s"${pem}")

    } catch {
      case e:Exception =>
        return Failure(new Exception(s"failed not write: ${fileName}",e))
    }
  }

  def findAnchor(anchors:Set[TrustAnchor], certName:Principal):X509Certificate = {
    for (anchor <- anchors) {
      if(anchor.getTrustedCert().getSubjectDN().equals(certName)) {
        return anchor.getTrustedCert();
      }
    }
    return null;
  }
  

  def getTrustAnchors():Set[TrustAnchor] = {
      // Load the JDK's cacerts keystore file
      val filename = System.getProperty("java.home") + "/lib/security/cacerts".replace('/', File.separatorChar);
      val is:FileInputStream = new FileInputStream(filename);
      val keystore:KeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      val password = "changeit";
      keystore.load(is, password.toCharArray());

      // This class retrieves the trust anchor (root) CAs from the keystore
      val params:PKIXParameters  = new PKIXParameters(keystore);
      return params.getTrustAnchors().asScala.toSet
  }


  class CustomTrustManager extends X509TrustManager {
    override def checkClientTrusted(x509Certificates: Array[X509Certificate], s:String) = {

    }
    
    override def checkServerTrusted(x509Certificates: Array[X509Certificate], s:String) = {
        serverCerts = x509Certificates.length;
        var badChain = false;
        
        x509Certificates.zipWithIndex.map{ case(cert,i) =>
          log.info(s"Certificate[${i}]: subj=${cert.getSubjectDN()},issuer=${cert.getIssuerDN()}: [${cert.getNotBefore()} ... ${cert.getNotAfter()}]")

          // Check to make sure chain is okay
          if(lastIssuer != null && !cert.getSubjectDN().equals(lastIssuer)) {
              log.error(s"Certificate chain invalid: expected=${lastIssuer}, found=${cert.getSubjectDN()}")                
              badChain = true;
          }

          lastCert = cert;
          lastIssuer = cert.getIssuerDN()
          lastSubject = cert.getSubjectDN()
        }

        if(badChain) {
            Failure(new Exception("Please fix the server's certificate chain and try again."))
        }
    }

    override def getAcceptedIssuers():Array[X509Certificate] = {
      Array.empty
      //return Array(new X509Certificate())
    }
  }
}

