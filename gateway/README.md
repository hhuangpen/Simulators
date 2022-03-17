# Gateway

## Build

To build and package the gateway application, use the below command:
```bash
./mvnw clean package
```

To run the application without packaging it, use the command below:
```bash
./mvnw clean compile exec:java
```

## Before starting the gateway (prerequisites)

You'll need of a Redis database, and a "fake IoT device".

In the Docker Compose project, start the containers:

```bash
docker-compose up
```

Once the containers started, you have to start the "fake device" (to simulate a "running" device), type the command below in a terminal to connect the container in an interactive mode:

```bash
docker exec -it fake-device /bin/sh
```

Then at the prompt, type the below command to start the "fake device":

```bash
node index.js
```

You can access the device with this URL: [http://fake-device:8099](http://fake-device:8099)

Now, in another terminal, run a Mosquitto client to listen on the `house` topic:

```bash
docker exec -it mqtt-server /bin/sh
mosquitto_sub -h localhost -t house/#
```

## Start the gateway

First build the application (`./mvnw clean package`) then run the below command:

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

The url of the gateway will be [http://gateway.home.smart:9090](http://gateway.home.smart:9090)
> `9090` is the default http port

## Manually test the device registration

```bash
curl --header "Content-Type: application/json" \
     --header "smart-token: smart.home" \
     --request POST \
     --data '{"category":"something","id":"000","position":"somewhere","host":"fake-device","port":8099}' \
     http://gateway.home.smart:9090/register
```
> you can check the registration by using this: `curl http://gateway.home.smart:9090/discovery`

So,
- You started the "fake IoT device"
- You started the Mosquitto client to listening on the `house` topis
- You started the gateway
- Finally, you manually send a curl command to register a device

Then, you should obtain something like this in the MQTT client terminal:

```json
{"id":"fake-device","location":"bathroom","category":"http","sensors":[{"temperature":{"unit":"Celsius","value":18}},{"humidity":{"unit":"%","value":50.5}},{"eCO2":{"unit":"ppm","value":45100}}]}
```

**Remark**: For your tests and experiments, you probably need often to re-initialize your environments. With Docker Compose it's pretty easy. Use this command:

```bash
docker-compose rm
```
> You will loose the data in the containers

To recreate the containers use this command:

```bash
docker-compose up
```


## Run the gateway with HTTPS protocol (SSL)

> This part is optional

First you need SSL certificates. You can use your own certificate and key. If you want to generate self-signed certificates, you can use the **MKCert** project: [https://github.com/FiloSottile/mkcert](https://github.com/FiloSottile/mkcert)

**Example**: If you want to generate a certificate and key for your local domain name `gateway.home.smart`:

```bash
cd certificates
mkcert home.smart "*.home.smart"
cp home.smart+1-key.pem gateway.home.smart.key
cp home.smart+1.pem gateway.home.smart.crt
EOF
```

Then you can start the gateway with the command below:

```bash
GATEWAY_TOKEN="smart.home" \
GATEWAY_SSL="true" \
GATEWAY_HTTP_PORT=8443 \
GATEWAY_CERTIFICATE="./certificates/gateway.home.smart.crt" \
GATEWAY_KEY="./certificates/gateway.home.smart.key" \
REDIS_HOST="redis-server" \
REDIS_PORT=6379 \
MQTT_HOST="mqtt-server" \
MQTT_PORT=1883 \
java -jar target/gateway-1.0.0-SNAPSHOT-fat.jar
```
The url of the gateway will be [https://gateway.home.smart:8443](https://gateway.home.smart:8443)

> - when registering the "fake device" don't forget to use the new url of the gateway
> - you can check the registration by using this: `https://gateway.home.smart:8443/discovery`



