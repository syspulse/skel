PASSWD=/tmp/passwd-s3fs 
MNT_POINT=${MNT_POINT:-/mnt/s3}

echo "$AWS_ACCESS_KEY_ID:$AWS_SECRET_ACCESS_KEY" >$PASSWD
chmod 600 $PASSWD
#cat $PASSWD
s3fs "$S3_BUCKET" "$MNT_POINT" -o passwd_file=$PASSWD,umask=0007,uid=1000,gid=1000 1>&2
ls -l $MNT_POINT 1>&2
#bash
