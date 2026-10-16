#!/usr/bin/env bash
# Asigna el flujo `issuer-state-browser` (login automático con issuer_state) SOLO al cliente `newwallet-app`
# en el Keycloak en ejecución (keycloak-reg). El import del realm no aplica authenticationFlowBindingOverrides
# (el cliente se importa sin la asignación), por eso este paso se ejecuta tras cada recreación del contenedor.
set -euo pipefail
K="docker exec keycloak-reg /opt/keycloak/bin/kcadm.sh"
R=pid-registraduria-realm
$K config credentials --server http://localhost:8080/idp --realm master --user admin \
  --password "$(docker exec keycloak-reg printenv KC_BOOTSTRAP_ADMIN_PASSWORD)" >/dev/null
FID=$($K get authentication/flows -r $R --fields id,alias | python3 -c "import json,sys;print([f['id'] for f in json.load(sys.stdin) if f['alias']=='issuer-state-browser'][0])")
CID=$($K get clients -r $R -q clientId=newwallet-app --fields id | python3 -c "import json,sys;print(json.load(sys.stdin)[0]['id'])")
$K update clients/$CID -r $R -s "authenticationFlowBindingOverrides.browser=$FID"
echo "newwallet-app -> flujo issuer-state-browser ($FID)"
