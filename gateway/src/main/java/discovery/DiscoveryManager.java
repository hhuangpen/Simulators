package discovery;



import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;

import java.util.Optional;

public class DiscoveryManager {

  ServiceDiscovery discovery;

  public DiscoveryManager(ServiceDiscovery serviceDiscovery) {
    this.discovery = serviceDiscovery;
  }

  /*
    Initialize the ServiceDiscovery
    Set the backend configuration
    In the last milestone, check if the Redis Db is connected
   */
  static public ServiceDiscovery initializeServiceDiscovery(Vertx vertx) {
    var redisHost = Optional.ofNullable(System.getenv("REDIS_HOST")).orElse("localhost");
    var redisPort = Integer.parseInt(Optional.ofNullable(System.getenv("REDIS_PORT")).orElse("6379"));
    var redisAuth = Optional.ofNullable(System.getenv("REDIS_PASSWORD")).orElse("");

    var redisConnectionString = redisAuth.isEmpty()
      ? "redis://"+redisHost+":"+redisPort
      : "redis://user:"+redisAuth+"@"+redisHost+":"+redisPort;
    /*
      Initialize the ServiceDiscovery
      Set the backend configuration
      In the last milestone, check if the Redis Db is connected
   */
    return ServiceDiscovery.create(vertx, new ServiceDiscoveryOptions()
      .setBackendConfiguration(
        new JsonObject()
          .put("connectionString", redisConnectionString)
          .put("key", "devices_records")
      ));
  }

  public ServiceDiscovery getDiscovery() {
    return discovery;
  }

  public Single<Record> publish(Record record) {
    return getDiscovery().rxPublish(record);
  }

  public Single<Record> update(Record record) {
    return getDiscovery().rxUpdate(record);
  }


}
