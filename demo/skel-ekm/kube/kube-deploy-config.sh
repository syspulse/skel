ENV=${1:-prod}

echo "Env: $ENV"

CONFIGMAP="config-${ENV}"
CONFIGMAP_FILE="skel-ekm-configmap-${ENV}.properties"
SECRETS_FILE="skel-ekm-secrets-${ENV}.yaml"

kubectl delete configmap "$CONFIGMAP"
kubectl create configmap "$CONFIGMAP" --from-file=$CONFIGMAP_FILE

#kubectl config use-context dev
kubectl delete -f "$SECRETS_FILE"
kubectl create -f "$SECRETS_FILE"