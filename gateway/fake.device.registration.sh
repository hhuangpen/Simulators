#!/bin/bash
curl --header "Content-Type: application/json" \
     --header "smart-token: smart.home" \
     --request POST \
     --data '{"category":"something","id":"000","position":"somewhere","host":"fake-device","port":8099}' \
     http://gateway.home.smart:9090/register
