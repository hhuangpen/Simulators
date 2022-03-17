package http;



import discovery.DiscoveryManager;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.reactivex.servicediscovery.types.HttpEndpoint;
import io.vertx.servicediscovery.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class Registration {
  private final Logger logger = LoggerFactory.getLogger(Registration.class);
  private final DiscoveryManager discoveryManager;
  private final String authenticationToken = Optional.ofNullable(System.getenv("GATEWAY_TOKEN"))
    .orElse("secret");

  public Registration(ServiceDiscovery discovery) {
    this.discoveryManager = new DiscoveryManager(discovery);
  }

  public void validateRegistration(RoutingContext routingContext) {
    var payload = routingContext.getBodyAsJson();
    if (!checkAuthenticationToken(routingContext)) {
      logger.warn("{} provides invalid authentication token", payload.getString("id") );
      routingContext.fail(401);
    } else if (!checkRegistrationDataFormat(routingContext)) {
      logger.warn("bad registration data format");
      routingContext.fail(400);
    } else {
      routingContext.next();
    }
  }

  // Check if the authentication token in the "smart-token" header is the good token
  public boolean checkAuthenticationToken(RoutingContext routingContext) {
    var optionalToken = Optional.ofNullable(routingContext.request().getHeader("smart-token")) ;
    var token = optionalToken.isEmpty() ? "" : optionalToken.get();
    return token.equals(authenticationToken);
  }

  // Check if the registration payload sent by the device contains the appropriate data
  public boolean checkRegistrationDataFormat(RoutingContext routingContext) {
    var payload = routingContext.getBodyAsJson();
    var category = Optional.ofNullable(payload.getString("category"));
    var id = Optional.ofNullable(payload.getString("id"));
    var position = Optional.ofNullable(payload.getString("position"));
    var host = Optional.ofNullable(payload.getString("host"));
    var port = Optional.ofNullable(payload.getString("port"));

    // check the data posted by the device
    return category.isPresent() && id.isPresent() && position.isPresent() && host.isPresent() && port.isPresent();
  }


  // this is the handler triggered by the registration route
  public void registerDevice(RoutingContext routingContext) {
    var payload = routingContext.getBodyAsJson();
    RegistrationData registrationData = new RegistrationData(
      payload.getString("id"),
      payload.getString("category"),
      payload.getString("position"),
      payload.getString("host"),
      payload.getString("port"));

    // construct the record for registration
    var record = HttpEndpoint.createRecord(
      registrationData.getId(),
      registrationData.getIp(),
      Integer.parseInt(registrationData.getPort()),
      "/");

    // add metadata
    record.setMetadata(
      new JsonObject()
        .put("category", registrationData.getCategory())
        .put("position", registrationData.getPosition())
    );

    // search if the record exists in the backend discovery
    discoveryManager.getDiscovery().rxGetRecord(rec -> rec.getName().equals(registrationData.getId()), true)
      .subscribe(okRecord -> {
          if (okRecord.getStatus().equals(Status.OUT_OF_SERVICE)){
            // The record exists
            // Update the record
            okRecord.setStatus(Status.UP);
            okRecord.setMetadata(
              new JsonObject()
                .put("category", registrationData.getCategory())
                .put("position", registrationData.getPosition()));
            discoveryManager.update(okRecord).subscribe(
              ok -> routingContext.json(new JsonObject().put("registration updated","ok")),
              error -> logger.error("Error when updating {}", error.getMessage())
            );
          } else {
            routingContext.fail(409);
            logger.warn("device already exists");
          }
        }
        , error -> {
          routingContext.fail(500);
          logger.error("Error when fetching the records {}", error.getMessage());
        }, () -> {
          // the lookup succeeded, but no matching service (the record doesn't exist)
          // create the record
          discoveryManager.publish(record).subscribe(
            ok -> routingContext.json(new JsonObject().put("registration","ok")),
            error -> logger.error("Error when publishing {}", error.getMessage())
          );
        });
  }
}
