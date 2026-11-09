package ua.course.courses;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.fail;

class IntentionalFailureDemoTest {

    @Test
    @EnabledIfSystemProperty(named = "demo.failure", matches = "true")
    void intentionalFailure_makesPipelineFailForDemonstration() {
        fail("Навмисна помилка для демонстрації failed CI pipeline");
    }
}
