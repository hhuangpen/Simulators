# Smartdevice

## Build

To package your application:
```bash
./mvnw clean package
```

To run the application without packaging it, use the command below:
```bash
./mvnw clean compile exec:java
```

## How to test the devices simulator

First run the command `docker-compose up` (we need a Redis Database, and an MQTT broker)

Secondly, start the Gateway (in the gateway project):

```bash
GATEWAY_TOKEN="smart.home" \
GATEWAY_SSL="false" \
GATEWAY_HTTP_PORT=9090 \
REDIS_HOST="redis-server" \
REDIS_PORT=6379 \
MQTT_HOST="mqtt-server" \
MQTT_PORT=1883 \
java -jar target/gateway-1.0.0-SNAPSHOT-fat.jar
```
> we'll use Mosquitto MQTT Broker


Thirdly, in another terminal, run a Mosquitto client to listen on the `house` topic:

```bash
docker exec -it mqtt-server /bin/sh
mosquitto_sub -h localhost -t house/#
```

Fourth, run the devices simulator with the command and parameters below to simulate an HTTP device:

```bash
HTTP_PORT="8081" \
DEVICE_TYPE="http" \
DEVICE_HOSTNAME="devices.home.smart" \
DEVICE_LOCATION="bedroom" \
DEVICE_ID="AX3345" \
GATEWAY_TOKEN="smart.home" \
GATEWAY_DOMAIN="gateway.home.smart" \
GATEWAY_HTTP_PORT=9090 \
java -jar target/smartdevice-1.0.0-SNAPSHOT-fat.jar ;
```

> **Remarks**: `devices.home.smart` is the DNS name to join the device(s) and `gateway.home.smart` is the DNS name to join the gateway (remember, you updated the `hosts` file of your computer).

After some seconds, you should see a json payload in the MQTT terminal:

```bash
{"id":"AX3345","location":"bedroom","category":"Temperature Humidity Environment Sensor","sensors":[{"temperature":{"unit":"Celsius","value":-1.1243556529821426}},{"humidity":{"unit":"%","value":1.0}},{"eCO2":{"unit":"ppm","value":15299.999999999987}}]}
```

Now run two new devices:

```bash
HTTP_PORT="8082" \
DEVICE_TYPE="http" \
DEVICE_HOSTNAME="devices.home.smart" \
DEVICE_LOCATION="garden" \
DEVICE_ID="BVOP34" \
GATEWAY_TOKEN="smart.home" \
GATEWAY_DOMAIN="gateway.home.smart" \
GATEWAY_HTTP_PORT=9090 \
java -jar target/smartdevice-1.0.0-SNAPSHOT-fat.jar ;
```

```bash
HTTP_PORT="8083" \
DEVICE_TYPE="http" \
DEVICE_HOSTNAME="devices.home.smart" \
DEVICE_LOCATION="bathroom" \
DEVICE_ID="OPRH67" \
GATEWAY_TOKEN="smart.home" \
GATEWAY_DOMAIN="gateway.home.smart" \
GATEWAY_HTTP_PORT=9090 \
java -jar target/smartdevice-1.0.0-SNAPSHOT-fat.jar ;
```

Then you should see new device in the json payload of the MQTT terminal.

> **Remark**: don't forget to use a different http port for every device.
