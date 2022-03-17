package mqtt;



import io.reactivex.Single;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.mqtt.MqttClient;
import io.vertx.reactivex.mqtt.messages.MqttConnAckMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class MqttManager {
  private MqttClient mqttClient;
  private CircuitBreaker breaker;
  final private Logger logger = LoggerFactory.getLogger(MqttManager.class);

  public MqttClient getMqttClient() {
    return mqttClient;
  }

  // get a circuit breaker
  private CircuitBreaker getBreaker(Vertx vertx) {
    if(breaker==null) {
      breaker = CircuitBreaker.create("gateway-circuit-breaker", vertx,
        new CircuitBreakerOptions()
          .setMaxFailures(3) // number of failure before opening the circuit
          .setMaxRetries(20) // number of retry before a failure
          .setTimeout(5_000) // consider a failure if the operation does not succeed in time
          .setResetTimeout(10_000) // time spent in open state before attempting to re-try
      ).retryPolicy(retryCount -> retryCount * 100L); // time to retry scaled linearly
    }
    return breaker;
  }

  // create and connect the MQTT client "in" a Circuit Breaker
  public Single<MqttConnAckMessage> startAndConnectMqttClient(Vertx vertx) {

    var mqttClientId = Optional.ofNullable(System.getenv("MQTT_CLIENT_ID")).orElse("gateway");

    var mqttPort = Integer.parseInt(Optional.ofNullable(System.getenv("MQTT_PORT")).orElse("1883"));
    var mqttHost = Optional.ofNullable(System.getenv("MQTT_HOST")).orElse("mqtt.home.smart");

    return getBreaker(vertx).rxExecute(promise -> {

      mqttClient = MqttClient.create(vertx, new MqttClientOptions()
        .setClientId(mqttClientId)
      ).exceptionHandler(throwable -> {
        // Netty ?
        logger.error(throwable.getMessage());
      }).closeHandler(voidValue -> {
        // Connection with broker is lost
        logger.warn("Connection with broker is lost");
        // try to connect again
        startAndConnectMqttClient(vertx);
      });

      // some code executing with the breaker
      // the code reports failures or success on the given promise.
      // if this promise is marked as failed, the breaker increased the
      // number of failures

      mqttClient.rxConnect(mqttPort, mqttHost)
        .subscribe(
          ok -> {
            logger.info("Connection to the broker is ok");
            promise.complete();
          },
          error -> {
            logger.error("MQTT {}", error.getMessage());
            promise.fail("[" + error.getMessage() + "]");
          }
        );
    });

  }
}

