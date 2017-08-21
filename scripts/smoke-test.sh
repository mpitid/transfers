#!/bin/bash
# A simple smoke test against a live API.

set -e

BASE=${1:-http://localhost:8080}

jpost() {
    curl -sSX POST -H content-type:application/json "$@"
}

user_id=$(jpost "${BASE}/v1/user" -d'{"name":"test"}' | jq -r .id)
account1_id=$(jpost "${BASE}/v1/account" -d'{"currency":"GBP","name":"account1","user":"'$user_id'"}' | jq -r .id)
account2_id=$(jpost "${BASE}/v1/account" -d'{"currency":"GBP","name":"account2","user":"'$user_id'"}' | jq -r .id)
deposit_id=$(jpost "${BASE}/v1/transaction" -d'{"deposit":{"ref":"initial","amount":1000,"dst":"'$account1_id'"}}' | jq -r .id)
transfer_id=$(jpost "${BASE}/v1/transaction" -d'{"transfer":{"amount":500,"src":"'$account1_id'","dst":"'$account2_id'"}}' | jq -r .id)

i=0
processed=null
while [[ processed != $transfer_id && i -lt 5 ]]; do
    processed=$(curl -sS "${BASE}/v1/transaction/$transfer_id" | jq -r .id)
    sleep 1s
    i+=1
done

curl -sS "${BASE}/v1/account/$account1_id" | jq -r '"\(.name): \(.balance)"'
curl -sS "${BASE}/v1/account/$account2_id" | jq -r '"\(.name): \(.balance)"'

