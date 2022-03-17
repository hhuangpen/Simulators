package com.smarthome.gateway;

import discovery.DiscoveryManager;
import helpers.GenericCodec;
import http.DevicesHealth;
import http.Registration;

import io.reactivex.Completable;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.rest.ServiceDiscoveryRestEndpoint;
import mqtt.MqttManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class MainVerticle extends AbstractVerticle {

  final private Logger logger = LoggerFactory.getLogger(MainVerticle.class);

  MqttManager mqttManager;

  @Override
  public Completable rxStop() {
    logger.info("Gateway stopped");
    return mqttManager.getMqttClient().rxDisconnect();
  }

  @Override
  public Completable rxStart() {

    /*
      Define parameters of the application
      ------------------------------------
      - Redis:
        redisHost, redisPort, redisAuth, redisConnectionString
      - Http Server:
        gatewayHttPort, authenticationToken, gatewayCertificate (path to certificate), gatewayKey (path to key), httpServerOptions
      - MQTT Client
     */
    var gatewayHttPort = Integer.parseInt(Optional.ofNullable(System.getenv("GATEWAY_HTTP_PORT")).orElse("9090"));
    var ssl = Boolean.parseBoolean(Optional.ofNullable(System.getenv("GATEWAY_SSL")).orElse("false"));

    var gatewayCertificate = Optional.ofNullable(System.getenv("GATEWAY_CERTIFICATE")).orElse("");
    var gatewayKey = Optional.ofNullable(System.getenv("GATEWAY_KEY")).orElse("");

    var httpServerOptions = new HttpServerOptions()
      .setSsl(ssl).
        setKeyCertOptions(
          new PemKeyCertOptions()
            .addCertPath(gatewayCertificate)
            .addKeyPath(gatewayKey)
        );

    var discovery = DiscoveryManager.initializeServiceDiscovery(vertx);
    /*
      1. Create the registration route
      2. Creates the REST endpoint using the default root (/discovery).
      http://localhost:9090/discovery
    */
    var router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    ServiceDiscoveryRestEndpoint.create(router.getDelegate(), discovery.getDelegate());

    var registration = new Registration(discovery);

    router.post("/register")
      .handler(registration::validateRegistration)
      .handler(registration::registerDevice);

    /*
      Define and connect the MQTT client
    */
    mqttManager = new MqttManager();
    mqttManager.startAndConnectMqttClient(vertx)
      .doOnError(fail -> logger.warn("🤬 enable to connect to broker {}", fail.getMessage()))
      .doOnSuccess(ok -> {

        /*
        Create a timer that calls a handler every 5 seconds
        Then every 5 seconds, do:
          - get the list of the registered devices
          - for each device, call a http request to the device
            - if the connection is successful
              - use the MQTT client to publish the JSON data of the object
            - if you cannot connect to the device
              - set status UNKNOWN and continue trying until OUT_OF_SERVICE
       */
        var webClient = WebClient.create(vertx);
        var mqttClient = mqttManager.getMqttClient();
        var deviceHealth = new DevicesHealth(discovery, webClient, mqttClient);
        vertx.getDelegate().eventBus().registerDefaultCodec(Record.class, new GenericCodec<Record>(Record.class));
        vertx.setPeriodic(5000, deviceHealth.handler);
        vertx.eventBus().consumer("device.unhealthy", deviceHealth.retryHandler);
      })
      .subscribe();

    /*
       Create and start the http server
    */
    return vertx.createHttpServer(httpServerOptions)
      .requestHandler(router)
      .rxListen(gatewayHttPort)
      .doOnSuccess(ok -> logger.info("Gateway: HTTP server started on port {}", gatewayHttPort))
      .doOnError(fail -> logger.error("Woops!, {}", fail.getMessage()))
      .ignoreElement();

  }

}
