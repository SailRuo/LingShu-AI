package com.lingshu.ai.core.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServiceImplErrorMessageTest {

    @Test
    void describeThrowable_shouldFallbackWhenExceptionMessageIsNull() {
        String description = ChatServiceImpl.describeThrowable(new IllegalStateException());

        assertEquals("IllegalStateException: unknown error", description);
    }

    @Test
    void describeThrowable_shouldKeepConcreteMessageWhenPresent() {
        String description = ChatServiceImpl.describeThrowable(new RuntimeException("boom"));

        assertTrue(description.contains("RuntimeException"));
        assertTrue(description.contains("boom"));
    }
}
