package http;

import discovery.DiscoveryManager;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.reactivex.Observable;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.mqtt.MqttClient;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DevicesHealth<T> {

  private final Logger logger = LoggerFactory.getLogger(DevicesHealth.class);
  private DiscoveryManager discoveryManager;
  private WebClient webClient;
  private MqttClient mqttClient;
  private CircuitBreaker breaker;
  private Map<String, CircuitBreaker> map = new HashMap<>();

  public DevicesHealth(ServiceDiscovery discovery, WebClient webClient, MqttClient mqttClient) {
    this.discoveryManager = new DiscoveryManager(discovery);
    this.webClient = webClient;
    this.mqttClient = mqttClient;
  }

  private CircuitBreaker getBreaker(Vertx vertx, String id) {
    if(!map.containsKey(id)) {
      breaker = CircuitBreaker.create("device-circuit-breaker-" + id, vertx,
        new CircuitBreakerOptions()
          .setMaxFailures(3) // number of failure before opening the circuit
          .setMaxRetries(20) // number of retry before a failure
          .setTimeout(5_000) // consider a failure if the operation does not succeed in time
          .setResetTimeout(10_000) // time spent in open state before attempting to re-try
      ).retryPolicy(retryCount -> retryCount * 100L); // time to retry scaled linearly
      map.put(id, breaker);
      return breaker;
    }
    return map.get(id);
  }

  // This handler is executed periodically by this line:
  // vertx.setPeriodic(5000, new DevicesHealth(discovery, webClient, mqttClient).handler);
  // in the MainVerticle

  // 1- search all record with a "category" in the discovery backend
  // 2- for each record, create a web client to do a get request to the device
  // 3- if the device is disconnected then unpublish its associated record
  // 4- if the device responds, MQTT publish the data of the device on the mqttTopic
  public Handler<Long> handler = aLong -> {
    var mqttTopic = Optional.ofNullable(System.getenv("MQTT_TOPIC")).orElse("house");

    discoveryManager.getDiscovery().rxGetRecords(rec -> !rec.getMetadata().getString("category").isEmpty())
      .doOnError(error -> {
        logger.error("Discovery error: {}", error.getMessage());
      })
      .toObservable().flatMap(Observable::fromIterable)
      .subscribe(record -> {
          var location = record.getLocation();
          webClient.get(location.getInteger("port"), location.getString("host"), "/")
            .rxSend()
            .subscribe(data ->{
              publishMqttMessage(mqttTopic, data.bodyAsJsonObject());
            }, error -> {
              logger.warn("Unable to connect: {}", record.getName());
              record.setStatus(Status.UNKNOWN);
              discoveryManager.update(record)
                .subscribe(
                  ok -> logger.warn("Change {} status to UNKNOWN", record.getName()),
                  err -> logger.error("Update status failed"));
              Vertx.currentContext().owner().eventBus().publish("device.unhealthy", record);
            });
      });
  };

  public Handler<Message<Record>> retryHandler = message -> {
    var mqttTopic = Optional.ofNullable(System.getenv("MQTT_TOPIC")).orElse("house");
    var record = message.body();
    var location = record.getLocation();
    var id = record.getName();
    getBreaker(Vertx.currentContext().owner(), id).rxExecute(promise -> {
      webClient.get(location.getInteger("port"), location.getString("host"), "/")
        .rxSend()
        .subscribe(data -> {
          record.setStatus(Status.UP);
          discoveryManager.update(record)
            .subscribe(
              ok -> logger.info("Change {} status to UP", id),
              err -> logger.error("Update status failed"));
          publishMqttMessage(mqttTopic, data.bodyAsJsonObject());
          promise.complete();
        },
          fail -> logger.warn("{} Wait for connection retry...", id)
        );
    }).subscribe(
      ok -> {}, fail -> {
        record.setStatus(Status.OUT_OF_SERVICE);
        discoveryManager.update(record)
          .subscribe(
            ok -> logger.info("Change {} status to OUT_OF_SERVICE", id),
            err -> logger.error("Update status failed"));
      }
    );
  };

  private void publishMqttMessage(String topic, JsonObject data) {
    // send MQTT Message
    if(mqttClient!=null && mqttClient.isConnected()) {
      mqttClient.publish(topic,
        Buffer.buffer(data.encode()),
        MqttQoS.AT_LEAST_ONCE, // AT_LEAST_ONCE
        false,
        false
      );
    }
  }
}
