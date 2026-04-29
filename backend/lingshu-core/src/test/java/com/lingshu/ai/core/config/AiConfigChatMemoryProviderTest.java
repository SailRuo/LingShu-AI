package com.lingshu.ai.core.config;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiConfigChatMemoryProviderTest {

    @Test
    void chatMemoryProvider_shouldKeepLatestSystemMessageOnlyInActiveMemory() {
        InMemoryStore store = new InMemoryStore();
        var provider = new AiConfig().chatMemoryProvider(store);
        var memory = provider.get(1L);

        memory.add(SystemMessage.from("system prompt 1"));
        memory.add(SystemMessage.from("system prompt 2"));
        memory.add(UserMessage.from("hi"));

        assertEquals(2, memory.messages().size());
        SystemMessage systemMessage = assertInstanceOf(SystemMessage.class, memory.messages().getFirst());
        assertEquals("system prompt 2", systemMessage.text());
        assertEquals(UserMessage.class, memory.messages().get(1).getClass());
        assertTrue(store.getMessages(1L).stream().noneMatch(SystemMessage.class::isInstance));
    }

    private static final class InMemoryStore implements ChatMemoryStore {
        private List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

        @Override
        public List<dev.langchain4j.data.message.ChatMessage> getMessages(Object memoryId) {
            return new ArrayList<>(messages);
        }

        @Override
        public void updateMessages(Object memoryId, List<dev.langchain4j.data.message.ChatMessage> messages) {
            this.messages = messages.stream()
                    .filter(message -> !(message instanceof SystemMessage))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }

        @Override
        public void deleteMessages(Object memoryId) {
            this.messages.clear();
        }
    }
}
