TOPIC=${1:-}
wscat --connect ws://localhost:8080/api/v1/notify/ws/$TOPIC
