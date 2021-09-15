ENV=${1:-prod}

echo "Env: $ENV"

DEPLOYMENT="deployment-${ENV}.yaml"

kubectl delete -f "$DEPLOYMENT"
