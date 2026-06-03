package com.template.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class HealthControllerTest {

    @Test
    void healthReturnsOk() {
        HealthController controller = new HealthController();
        WebTestClient.bindToController(controller).build()
            .get().uri("/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("ok");
    }
}
