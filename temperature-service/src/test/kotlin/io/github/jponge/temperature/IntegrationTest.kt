/*
 * Copyright 2018 Julien Ponge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.jponge.temperature

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.codec.BodyCodec
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@DisplayName("👀 Some integration tests")
@ExtendWith(VertxExtension::class)
class IntegrationTest {

  @BeforeEach
  fun prepare(vertx: Vertx, context: VertxTestContext) {

    val tempDeployed = context.checkpoint()
    vertx.deployVerticle(TemperatureVerticle(), context.succeeding {
      tempDeployed.flag()
    })

    val serverDeployed = context.checkpoint()
    vertx.deployVerticle(HttpServerVerticle(), context.succeeding {
      serverDeployed.flag()
    })
  }

  @Test
  @DisplayName("☝️ Check that updates are being published to the event bus")
  fun updates(vertx: Vertx, context: VertxTestContext) {
    vertx.eventBus().consumer<JsonObject>("temperature.updates") { msg ->
      context.verify {
        assertFalse(msg.body().isEmpty)
        assertTrue(msg.body().containsKey("id"))
        assertTrue(msg.body().containsKey("temperature"))
        context.completeNow()
      }
    }
  }

  @Test
  @DisplayName("📡 Check that the HTTP server works")
  fun server(vertx: Vertx, context: VertxTestContext) {
    vertx.setTimer(1000) {
      WebClient.create(vertx)
        .get(8080, "localhost", "/api/temperature")
        .`as`(BodyCodec.jsonObject())
        .send {
          context.verify {
            assertTrue(it.succeeded())
            assertEquals(200, it.result().statusCode())
            context.completeNow()
          }
        }
    }
  }
}
