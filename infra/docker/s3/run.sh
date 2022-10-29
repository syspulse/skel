echo "$AWS_ACCESS_KEY_ID:$AWS_SECRET_ACCESS_KEY" > /passwd-s3fs 
chmod 600 /passwd-s3fs
ls -l /
s3fs "$S3_BUCKET" "$MNT_POINT" -o passwd_file=/passwd-s3fs 
bash
