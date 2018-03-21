package io.github.jponge.zlack

import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.ext.bridge.PermittedOptions
import io.vertx.kotlin.ext.web.handler.sockjs.BridgeOptions
import io.vertx.reactivex.core.AbstractVerticle
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.handler.BodyHandler
import io.vertx.reactivex.ext.web.handler.StaticHandler
import io.vertx.reactivex.ext.web.handler.sockjs.SockJSHandler
import org.slf4j.LoggerFactory

class HttpServerVerticle : AbstractVerticle() {

  private val logger = LoggerFactory.getLogger(HttpServerVerticle::class.java)

  override fun start(startFuture: Future<Void>) {
    val port = config().getString("http-port", "8080").toInt()
    logger.info("Trying to start a HTTP server on port ${port}")

    val router = Router.router(vertx)
    router.route("/api/*").handler(BodyHandler.create())
    router.get("/api/messages").handler(this::getAllMessages)
    router.post("/api/messages").handler(this::postNewMessage)

    val sockJSHandler = SockJSHandler.create(vertx)
    val options = BridgeOptions(
      inboundPermitteds = listOf(PermittedOptions(addressRegex = ".*")),  // ⚠
      outboundPermitteds = listOf(PermittedOptions(addressRegex = ".*"))) // ⚠
    sockJSHandler.bridge(options)
    router.route("/eventbus/*").handler(sockJSHandler)

    router.route().handler(StaticHandler.create().setCachingEnabled(false))

    vertx.createHttpServer()
      .requestHandler(router::accept)
      .rxListen(port)
      .subscribeBy(
        onSuccess = {
          logger.info("HTTP server running on port ${port}")
          startFuture.complete()
        },
        onError = {
          logger.error("HTTP server could not be started on port ${port}", it)
          startFuture.fail(it)
        })
  }

  private val emptyJson = json { obj() }

  private fun getAllMessages(context: RoutingContext) {
    vertx.eventBus()
      .rxSend<JsonObject>("messages.get-all", emptyJson)
      .subscribeBy(
        onSuccess = { reply ->
          context.response()
            .setStatusCode(200)
            .putHeader("Content-Type", "application/json")
            .end(reply.body().encode())
        },
        onError = { context.fail(500) })
  }

  private fun postNewMessage(context: RoutingContext) {
    val payload = context.bodyAsJson

    if (!payload.containsKey("author") || !payload.containsKey("content")) {
      context.fail(400)
      return
    }

    vertx.eventBus()
      .rxSend<JsonObject>("messages.store", payload)
      .subscribeBy(
        onSuccess = { reply ->
          context.response()
            .setStatusCode(201)
            .putHeader("Content-Type", "application/json")
            .end(reply.body().encode())
        },
        onError = { context.fail(500) })
  }
}
