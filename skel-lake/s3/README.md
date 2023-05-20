# S3 Datalake


### S3 mount as file system

```
echo $AWS_ACCESS_KEY_ID:$AWS_SECRET_ACCESS_KEY>/etc/passwd-s3fs
chmod 600 /etc/passwd-s3fs
s3fs haas-data-dev /mnt/s3
```

### S3 mount into Kubernetes docker

[https://github.com/datashim-io/datashim](https://github.com/datashim-io/datashim)

Provisioning

1. Update bucket name in `s3-data-template.yml`

2. AWS credentions in env vars

3. Create kube files:

```
./datashim-setup.sh
```

4. Deploy to kube

```
kubectl apply -f s3-data.yml
```


