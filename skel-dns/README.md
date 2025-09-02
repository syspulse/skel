# DNS

## Examples


### Query IO zone

1. Get whois server for .io zone:
```
whois -h whois.iana.org io
```

2. use this server to query domain.io:

```
whois -h whois.nic.io domain.io
```

3. In skel:

```
./run-dns.sh domain.io whois.nic.io
```