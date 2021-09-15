ENV=${1:-prod}

echo "Env: $ENV"

DEPLOYMENT="deployment/ekm"

kubectl logs -f "$DEPLOYMENT"

