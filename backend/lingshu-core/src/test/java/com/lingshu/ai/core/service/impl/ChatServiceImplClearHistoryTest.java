package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.*;
import com.lingshu.ai.core.tool.McpToolArtifactRegistry;
import com.lingshu.ai.infrastructure.repository.ChatSessionRepository;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceImplClearHistoryTest {

    @Test
    void clearHistory_shouldRemoveRetrievalSnapshotsForSession() {
        ChatMemory chatMemory = mock(ChatMemory.class);
        ChatMemoryProvider chatMemoryProvider = mock(ChatMemoryProvider.class);
        when(chatMemoryProvider.get(42L)).thenReturn(chatMemory);

        RetrievalContextSnapshotStore snapshotStore = mock(RetrievalContextSnapshotStore.class);

        ChatServiceImpl chatService = new ChatServiceImpl(
                mock(MemoryService.class),
                mock(AgentConfigService.class),
                mock(ChatSessionService.class),
                mock(ChatSessionRepository.class),
                mock(StreamingChatModel.class),
                mock(RestTemplate.class),
                mock(SettingService.class),
                mock(SystemLogService.class),
                mock(AffinityService.class),
                mock(PromptBuilderService.class),
                chatMemoryProvider,
                mock(McpService.class),
                List.<ChatModelListener>of(),
                mock(TurnPostProcessingServiceImpl.class),
                mock(ToolResultSummarizer.class),
                mock(TurnTimelineService.class),
                mock(McpToolArtifactRegistry.class),
                snapshotStore
        );

        chatService.clearHistory(42L);

        verify(chatMemoryProvider).get(42L);
        verify(chatMemory).clear();
        verify(snapshotStore).removeBySessionId(42L);
    }
}
