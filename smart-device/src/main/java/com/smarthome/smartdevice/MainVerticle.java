package com.smarthome.smartdevice;

import devices.HttpDevice;
import io.reactivex.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sensors.eCO2Sensor;
import sensors.HumiditySensor;
import sensors.TemperatureSensor;

import java.util.List;
import java.util.Optional;

public class MainVerticle extends AbstractVerticle {

  private final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public Completable rxStop() {
    logger.info("Device stopped");
    return Completable.complete();
  }

  @Override
  public Completable rxStart() {

    /*
      Define parameters of the application
      ------------------------------------
      deviceType, httpPort, deviceLocation, deviceId, gatewayHttPort, domainNameOrIP, ssl
    */

    var deviceType = Optional.ofNullable(System.getenv("DEVICE_TYPE")).orElse("http");
    var deviceLocation = Optional.ofNullable(System.getenv("DEVICE_LOCATION")).orElse("somewhere");
    var deviceId = Optional.ofNullable(System.getenv("DEVICE_ID")).orElse("something");

    if(deviceType.equals("http")) { // HTTP Device

      var httpPort = Integer.parseInt(Optional.ofNullable(System.getenv("HTTP_PORT")).orElse("8080"));

      var gatewayHttPort = Integer.parseInt(Optional.ofNullable(System.getenv("GATEWAY_HTTP_PORT")).orElse("9090"));
      var domainNameOrIP = Optional.ofNullable(System.getenv("GATEWAY_DOMAIN")).orElse("0.0.0.0");
      var ssl = Boolean.parseBoolean(Optional.ofNullable(System.getenv("GATEWAY_SSL")).orElse("false"));

      var authenticationToken = Optional.ofNullable(System.getenv("GATEWAY_TOKEN")).orElse("secret");

      /*
        Initialize the device (new HttpDevice(deviceId))
     */
      var httpDevice = new HttpDevice(deviceId)
        .setCategory("Temperature Humidity Environment Sensor")
        .setPosition(deviceLocation)
        .setPort(httpPort)
        .setSensors(List.of(
          new TemperatureSensor(),
          new HumiditySensor(),
          new eCO2Sensor()
        ));

      /*
        Create the request for the gateway
        Send the request to the gateway
        If the registration fails, call the `setConnectedToGateway(false)` method of the device
        If the registration succeeds, call the `setConnectedToGateway(true)` method of the device
     */
      var requestToGateway = httpDevice.createRegisterToGatewayRequest(vertx, domainNameOrIP, gatewayHttPort, ssl, authenticationToken);

      var registration = new JsonObject()
        .put("category",httpDevice.getCategory())
        .put("id", httpDevice.getId())
        .put("position", httpDevice.getPosition())
        .put("host", httpDevice.getHostName())
        .put("port", httpDevice.getPort());

      logger.info("try connecting to gateway...");
      requestToGateway.rxSendJsonObject(registration)
              .subscribe(response -> {
                if (response.statusCode() != 200) {
                  logger.warn("Registration failed: " + response.statusCode());
                  httpDevice.setConnectedToGateway(false);
                  // try again
                } else {
                  httpDevice.setConnectedToGateway(true);
                  logger.info("Registration succeeded: " + response.statusCode());
                  logger.info(response.bodyAsJsonObject().encodePrettily());
                }
              }, error -> {
                logger.error("Connection to the Gateway failed: " + error.getMessage());
                httpDevice.setConnectedToGateway(false);
              });
      /*
        Define a router
        Add a route that returns the value of the Device
      */
      var router = httpDevice.createRouter(vertx);

      router.get("/").handler(routingContext -> {
        routingContext.json(httpDevice.jsonValue());
      });

      /*
        Start the http server of the device
     */
      return httpDevice
              .createHttpServer(vertx, router)
              .rxListen(httpPort)
              .doOnSuccess(ok -> logger.info("Device: HTTP server started on port {}", httpPort))
              .doOnError(err -> logger.error("Woops!, {}", err.getMessage()))
              .ignoreElement();
    } else { // MQTT Device
      // To Be done in the next project
      return Completable.error(NoSuchMethodError::new);
    }

  }

}
