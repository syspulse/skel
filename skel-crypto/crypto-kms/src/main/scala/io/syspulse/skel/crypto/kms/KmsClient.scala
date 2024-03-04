package io.syspulse.skel.crypto.kms

import scala.util.{Try,Success,Failure}
import scala.jdk.CollectionConverters._

import java.nio.charset.StandardCharsets
import com.amazonaws.services.kms.{AWSKMS, AWSKMSClientBuilder}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration

import io.syspulse.skel.util.Util
import io.syspulse.skel.uri.KmsURI
import com.amazonaws.services.kms.model.CreateKeyRequest
import com.amazonaws.services.kms.model.CustomerMasterKeySpec
import com.amazonaws.services.kms.model.CreateAliasRequest

class KmsClient(uri:String) {
  import Util._
  
  protected val kmsUri = KmsURI(uri)
  
  protected val kms = (for {
    k0 <- Success(AWSKMSClientBuilder.standard)
    k1 <- {
      if(kmsUri.host.isDefined)
        Success(k0.withEndpointConfiguration(new EndpointConfiguration(kmsUri.host.get,kmsUri.region.getOrElse(""))))
      else
        Success(k0)
    }
    k2 <- {
      if(kmsUri.region.isDefined)
        Success(k1.withRegion(kmsUri.region.get))
      else
        Success(k1)
    }    
    k10 <- Success(k2.build)
  } yield k10) match {
    case Success(kms) => kms
    case Failure(e) => throw e
  }

  def getAWSKMS() = kms


  def createKeyAES(name:String,alias:Option[String] = None) = {
    val req = new CreateKeyRequest()
      .withDescription(name)
      .withCustomerMasterKeySpec(CustomerMasterKeySpec.SYMMETRIC_DEFAULT)
      .withKeyUsage("ENCRYPT_DECRYPT")      
          
    var res = kms.createKey(req)
    val arn = res.getKeyMetadata().getArn()
    val keyId = res.getKeyMetadata().getKeyId()    

    // create alias
    if(alias.isDefined) {
      val req = new CreateAliasRequest()
        .withTargetKeyId(keyId)
        .withAliasName(s"alias/${alias.get}")
        
      val res = kms.createAlias(req)        
      req.getAliasName()
    }

    keyId
  }
}


