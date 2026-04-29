package com.lingshu.ai.infrastructure.memory;

import com.lingshu.ai.infrastructure.repository.ChatTurnArtifactRepository;
import com.lingshu.ai.infrastructure.repository.ChatTurnEventRepository;
import com.lingshu.ai.infrastructure.repository.ChatTurnRepository;
import dev.langchain4j.data.message.SystemMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DatabaseChatMemoryStoreTest {

    @Test
    void updateMessages_shouldNotReplaySystemMessagesFromInMemoryCache() {
        DatabaseChatMemoryStore store = new DatabaseChatMemoryStore(
                mock(ChatTurnRepository.class),
                mock(ChatTurnEventRepository.class),
                mock(ChatTurnArtifactRepository.class)
        );

        store.updateMessages(1L, List.of(SystemMessage.from("system prompt")));

        assertTrue(store.getMessages(1L).isEmpty());
    }
}
