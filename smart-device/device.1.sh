#!/bin/bash
HTTP_PORT="8081" \
DEVICE_TYPE="http" \
DEVICE_HOSTNAME="devices.home.smart" \
DEVICE_LOCATION="bedroom" \
DEVICE_ID="AX3345" \
GATEWAY_TOKEN="smart.home" \
GATEWAY_DOMAIN="gateway.home.smart" \
GATEWAY_HTTP_PORT=9090 \
java -jar target/smartdevice-1.0.0-SNAPSHOT-fat.jar ;
