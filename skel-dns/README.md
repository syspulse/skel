# DNS

## Examples


### Query IO zone

1. Get whois server for .io zone:
```
whois -h whois.iana.org io
```

2. use this server to quere domain.io:

```
whois.nic.io
```

3. In skel:

```
./run-dns.sh syspulse.io whois.nic.io
```