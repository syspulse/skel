package io.syspulse.skel.notify.aws

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult

class SNS() {

  val region:String = Option(System.getProperty("AWS_REGION")).getOrElse("us-west-1")

  val snsClient:AmazonSNS  = AmazonSNSClient
            .builder()
            .withRegion(Regions.fromName(region))
            .withCredentials(new DefaultAWSCredentialsProviderChain()) //new AWSStaticCredentialsProvider(new BasicAWSCredentials(awsAccount, awsSecret)))
            .build();

  def publish(message:String,topicArn:String):PublishResult = {
    try {
        val publishReq:PublishRequest = new PublishRequest()
                .withTopicArn(topicArn)
                .withMessage(message);
        
        snsClient.publish(publishReq);
        
    } catch {
        case e:Exception => throw e  
    } 
  }

}
