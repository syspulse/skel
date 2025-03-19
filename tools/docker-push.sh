#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

t=`pwd`;
APP_DEF=`basename "$t"`

CMD=${1:-deploy}

if [ "$2" != "" ]; then
   APP_FULL=$2
else
   APP_FULL=syspulse/${APP_DEF}:latest
fi


APP_NAME=`echo $APP_FULL | awk -F':' '{print $1}'`
APP_VER=`echo $APP_FULL | awk -F':' '{print $2}'`

if [ "$VERSION" != "" ]; then
  APP_VER=$VERSION
else
  APP_VER=$(basename `ls target/scala-2.13/*.pom | tail -1` .pom | awk -F_ '{print $2}'| awk -F- '{print $2}')
fi

if [ "$APP_VER" != "" ]; then
  APP=$APP_NAME:$APP_VER
else
  APP=$APP_NAME
fi

AWS_ACCOUNT=${AWS_ACCOUNT:-459501022842}
AWS_REGION=${AWS_REGION:-eu-west-1}
AWS=$AWS_ACCOUNT.dkr.ecr.${AWS_REGION}.amazonaws.com

echo ${APP_NAME}
echo ${APP_VER}
echo ${APP}
echo ${AWS}/${APP}

case "$CMD" in
   build)
     docker build . -f Dockerfile -t $APP
     ;;
   aws)
     # push to ECR
     docker tag $APP ${AWS}/$APP
     aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin ${AWS}
     aws ecr create-repository --repository-name $APP_NAME
     docker push ${AWS}/$APP
     ;;
   aws-login)
     aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin ${AWS}
     ;;
   deploy)
     # push to ECR
     #docker tag $APP ${AWS}/$APP
     docker push $APP
     ;;
esac
