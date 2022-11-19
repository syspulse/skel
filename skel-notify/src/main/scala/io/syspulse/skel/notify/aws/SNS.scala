package io.syspulse.skel.notify.aws

import scala.util.{Try,Success,Failure}

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult

trait SNS {

  val region:String = Option(System.getenv("AWS_REGION")).getOrElse("eu-west-1")

  val snsClient:AmazonSNS  = AmazonSNSClient
            .builder()
            .withRegion(Regions.fromName(region))
            .withCredentials(new DefaultAWSCredentialsProviderChain())
            .build();
  
  // ARN format: 'arn:aws:sns:eu-west-1:649502643044:notify-topic'
  def publish(message:String,topicArn:String):Try[PublishResult] = {
    try {
        val publishReq:PublishRequest = new PublishRequest()
                .withTopicArn(topicArn)
                .withMessage(message);
        
        Success(snsClient.publish(publishReq))
        
    } catch {
        case e:Exception => Failure(e)
    } 
  }

}
