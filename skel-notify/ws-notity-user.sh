USER=${1:-00000000-0000-0000-1000-000000000001}
wscat --connect ws://localhost:8080/api/v1/notify/user/$USER
