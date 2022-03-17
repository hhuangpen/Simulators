#!/bin/bash
HTTP_PORT="8082" \
DEVICE_TYPE="http" \
DEVICE_HOSTNAME="devices.home.smart" \
DEVICE_LOCATION="garden" \
DEVICE_ID="BVOP34" \
GATEWAY_TOKEN="smart.home" \
GATEWAY_DOMAIN="gateway.home.smart" \
GATEWAY_HTTP_PORT=9090 \
java -jar target/smartdevice-1.0.0-SNAPSHOT-fat.jar ;


