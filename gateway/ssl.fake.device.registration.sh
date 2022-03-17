#!/bin/bash
curl --header "Content-Type: application/json" \
     --header "smart-token: smart.home" \
     --request POST \
     --data '{"category":"something","id":"000","position":"somewhere","host":"fake-device","port":8099}' \
     https://gateway.home.smart:8443/register
