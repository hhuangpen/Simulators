#!/bin/bash
GATEWAY_TOKEN="smart.home" \
GATEWAY_SSL="false" \
GATEWAY_HTTP_PORT=9090 \
REDIS_HOST="redis-server" \
REDIS_PORT=6379 \
MQTT_HOST="mqtt-server" \
MQTT_PORT=1883 \
java -jar target/gateway-1.0.0-SNAPSHOT-fat.jar

