ENV=${1:-prod}

echo "Env: $ENV"

CONFIGMAP="skel-ekm-configmap-${ENV}"
SECRETS="skel-ekm-secrets-${ENV}"

CONFIGMAP_FILE="configmap-${ENV}.yaml"
SECRETS_FILE="secrets-${ENV}.yaml"

kubectl delete -f "$CONFIGMAP_FILE"
kubectl apply -f "$CONFIGMAP_FILE"

kubectl delete -f "$SECRETS_FILE"
kubectl apply -f "$SECRETS_FILE"