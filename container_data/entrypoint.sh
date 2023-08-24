#!/usr/bin/env bash

if [ "$CLIENT_TYPE" = "kubectl" ]; then
  # Set kubectl
  export KUBECONFIG="/app/.kube/config"
elif [ "$CLIENT_TYPE" = "oc" ]; then
  # Set oc client
  oc login -u $OCP_USERNAME -p $OCP_PASSWORD $OCP_API_URL --insecure-skip-tls-verify=true
else
  # Invalid client type
  echo "Invalid client type: $CLIENT_TYPE"
  exit 1
fi

exec "$@"
