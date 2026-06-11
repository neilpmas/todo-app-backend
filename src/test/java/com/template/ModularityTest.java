package com.template;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void verifiesArchitecture() {
        ApplicationModules.of(Application.class).verify();
    }
}
