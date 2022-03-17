package communications;

import devices.Device;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.WebClient;


import java.util.Optional;

public interface Http {

  boolean isConnectedToGateway();
  void setConnectedToGateway(boolean value);

  default String getProtocol() {
    return "http";
  }

  /*
    create an http request to register the device on the gateway
   */
  default HttpRequest<Buffer> createRegisterToGatewayRequest(Vertx vertx, String domainName, int port, boolean ssl, String token) {
    return WebClient.create(vertx).post(port, domainName, "/register")
      .putHeader("smart-token", token)
      .ssl(ssl);
  }

  default Router createRouter(Vertx vertx) {
    return Router.router(vertx);
  }

  default HttpServer createHttpServer(Vertx vertx, Router router) {
    return vertx.createHttpServer().requestHandler(router);
  }

  default String getHostName() {
    return Optional.ofNullable(System.getenv("DEVICE_HOSTNAME")).orElse("devices.home.smart");
  }

  int getPort();
  Device setPort(int value);
}
