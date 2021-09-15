ENV=${1:-prod}

echo "Env: $ENV"

DEPLOYMENT="deployment/npp"

kubectl logs -f "$DEPLOYMENT"

