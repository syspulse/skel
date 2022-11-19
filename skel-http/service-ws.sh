TOPIC=${1:-}
wscat --connect ws://localhost:8083/api/v1/service/ws/$TOPIC
