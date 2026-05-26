package com.aiasistan.documents;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DocumentsApplicationTests {

    @Test
    void contextLoads() {
        // Wiring smoke test — beans construct, no startup error.
    }
}
