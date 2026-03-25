package com.lingshu.ai.infrastructure.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.infrastructure.entity.ChatSession;
import com.lingshu.ai.infrastructure.repository.ChatMessageRepository;
import com.lingshu.ai.infrastructure.repository.ChatSessionRepository;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 实现 LangChain4j 的 ChatMemoryStore，将对话历史持久化到数据库。
 * 完整支持工具调用消息链（AiMessage with toolExecutionRequests + ToolExecutionResultMessage）。
 */
@Slf4j
@Component("databaseChatMemoryStore")
public class DatabaseChatMemoryStore implements ChatMemoryStore {

    private final ChatMessageRepository messageRepository;
    private final ChatSessionRepository sessionRepository;
    private final ConcurrentMap<Long, SystemMessage> sessionSystemMessages = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatabaseChatMemoryStore(ChatMessageRepository messageRepository,
                                   ChatSessionRepository sessionRepository) {
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        log.debug("Memory retrieval: 正在加载会话 {} 的历史记录", memoryId);
        Long sessionId = parseId(memoryId);

        // 分页获取最近的 20 条消息（增大窗口以容纳工具调用链）
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, 20,
                        org.springframework.data.domain.Sort.by("createdAt").descending());
        List<com.lingshu.ai.infrastructure.entity.ChatMessage> dbMessages =
                new ArrayList<>(messageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, pageable).getContent());

        // 翻转回正序供 LangChain4j 使用
        Collections.reverse(dbMessages);

        List<ChatMessage> messages = dbMessages.stream()
                .map(this::toLangChain4jMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        SystemMessage systemMessage = sessionSystemMessages.get(sessionId);
        if (systemMessage != null) {
            messages.add(0, systemMessage);
        }

        return messages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return;

        Long sessionId = parseId(memoryId);

        Optional<SystemMessage> systemMessage = messages.stream()
                .filter(SystemMessage.class::isInstance)
                .map(SystemMessage.class::cast)
                .reduce((first, second) -> second);
        if (systemMessage.isPresent()) {
            sessionSystemMessages.put(sessionId, systemMessage.get());
        } else {
            sessionSystemMessages.remove(sessionId);
        }

        // 过滤掉 SystemMessage，因为 system prompt 由 ChatServiceImpl 每次动态注入，不应持久化
        List<ChatMessage> persistableMessages = messages.stream()
                .filter(m -> !(m instanceof SystemMessage))
                .collect(Collectors.toList());
        if (persistableMessages.isEmpty()) return;

        log.debug("Memory update: 正在同步 {} 条消息到会话 {}", messages.size(), memoryId);
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        // 获取数据库中已有的最后一条消息
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, 1,
                        org.springframework.data.domain.Sort.by("id").descending());
        List<com.lingshu.ai.infrastructure.entity.ChatMessage> lastInDbList =
                messageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, pageable).getContent();

        if (lastInDbList.isEmpty()) {
            // 数据库为空，保存所有（已过滤 SystemMessage）
            for (ChatMessage msg : persistableMessages) {
                saveMessage(session, msg);
            }
            return;
        }

        com.lingshu.ai.infrastructure.entity.ChatMessage lastDbMsg = lastInDbList.get(0);

        // 找到内存列表中哪些是新消息
        int newMessagesStartIndex = -1;
        for (int i = persistableMessages.size() - 1; i >= 0; i--) {
            ChatMessage currentMemMsg = persistableMessages.get(i);
            String currentRole = getRole(currentMemMsg);
            String currentContent = getContentForComparison(currentMemMsg);
            String dbContent = lastDbMsg.getContent() != null ? lastDbMsg.getContent() : "";

            if (currentRole != null && currentRole.equals(lastDbMsg.getRole())
                    && currentContent.equals(dbContent)) {
                newMessagesStartIndex = i + 1;
                break;
            }
        }

        if (newMessagesStartIndex == -1) {
            ChatMessage lastMemMsg = persistableMessages.get(persistableMessages.size() - 1);
            String lastMemContent = getContentForComparison(lastMemMsg);
            if (!Objects.equals(lastMemContent, lastDbMsg.getContent())) {
                saveMessage(session, lastMemMsg);
            }
        } else {
            for (int i = newMessagesStartIndex; i < persistableMessages.size(); i++) {
                saveMessage(session, persistableMessages.get(i));
            }
        }
    }

    /**
     * 获取用于对比的内容文本。
     */
    private String getContentForComparison(ChatMessage msg) {
        if (msg instanceof UserMessage) {
            return ((UserMessage) msg).singleText();
        } else if (msg instanceof AiMessage aiMessage) {
            String text = aiMessage.text();
            return text != null ? text : "";
        } else if (msg instanceof ToolExecutionResultMessage toolResult) {
            String text = toolResult.text();
            return text != null ? text : "";
        }
        return "";
    }

    @Override
    public void deleteMessages(Object memoryId) {
        log.debug("Memory deletion: 正在清除会话 {} 的历史记录", memoryId);
        Long sessionId = parseId(memoryId);
        sessionSystemMessages.remove(sessionId);
    }

    /**
     * 保存消息到数据库。支持 UserMessage、AiMessage（含工具调用请求）、ToolExecutionResultMessage。
     */
    private void saveMessage(ChatSession session, ChatMessage msg) {
        String role = getRole(msg);
        if (role == null) return;

        com.lingshu.ai.infrastructure.entity.ChatMessage.ChatMessageBuilder builder =
                com.lingshu.ai.infrastructure.entity.ChatMessage.builder()
                        .session(session)
                        .role(role)
                        .createdAt(LocalDateTime.now());

        if (msg instanceof UserMessage userMsg) {
            builder.content(userMsg.singleText());
        } else if (msg instanceof AiMessage aiMessage) {
            // AiMessage 可能包含 text 和/或 toolExecutionRequests
            builder.content(aiMessage.text() != null ? aiMessage.text() : "");

            if (aiMessage.hasToolExecutionRequests()) {
                try {
                    List<ToolCallData> toolCallDataList = aiMessage.toolExecutionRequests().stream()
                            .map(req -> new ToolCallData(req.id(), req.name(), req.arguments()))
                            .collect(Collectors.toList());
                    builder.toolCalls(objectMapper.writeValueAsString(toolCallDataList));
                    log.debug("保存 AiMessage 包含 {} 个工具调用请求", toolCallDataList.size());
                } catch (JsonProcessingException e) {
                    log.warn("序列化工具调用请求失败: {}", e.getMessage());
                }
            }
        } else if (msg instanceof ToolExecutionResultMessage toolResult) {
            builder.content(toolResult.text() != null ? toolResult.text() : "");
            builder.toolCallId(toolResult.id());
            builder.toolName(toolResult.toolName());
            log.debug("保存 ToolExecutionResultMessage: toolName={}, id={}", toolResult.toolName(), toolResult.id());
        } else {
            return;
        }

        messageRepository.save(builder.build());
    }

    private String getRole(ChatMessage msg) {
        if (msg instanceof SystemMessage) return "system";
        if (msg instanceof UserMessage) return "user";
        if (msg instanceof AiMessage) return "assistant";
        if (msg instanceof ToolExecutionResultMessage) return "tool";
        return null;
    }

    /**
     * 从数据库记录还原为 LangChain4j ChatMessage。
     * 支持还原带有 toolExecutionRequests 的 AiMessage 和 ToolExecutionResultMessage。
     */
    private ChatMessage toLangChain4jMessage(com.lingshu.ai.infrastructure.entity.ChatMessage dbMsg) {
        String role = dbMsg.getRole();
        String content = dbMsg.getContent() != null ? dbMsg.getContent() : "";

        if ("user".equalsIgnoreCase(role)) {
            return UserMessage.from(content);
        } else if ("assistant".equalsIgnoreCase(role)) {
            // 检查是否包含工具调用请求
            if (dbMsg.getToolCalls() != null && !dbMsg.getToolCalls().isBlank()) {
                try {
                    List<ToolCallData> toolCallDataList = objectMapper.readValue(
                            dbMsg.getToolCalls(), new TypeReference<List<ToolCallData>>() {});
                    List<ToolExecutionRequest> requests = toolCallDataList.stream()
                            .map(data -> ToolExecutionRequest.builder()
                                    .id(data.id())
                                    .name(data.name())
                                    .arguments(data.arguments())
                                    .build())
                            .collect(Collectors.toList());
                    if (content.isBlank()) {
                        return AiMessage.from(requests);
                    } else {
                        return AiMessage.from(content, requests);
                    }
                } catch (JsonProcessingException e) {
                    log.warn("反序列化工具调用请求失败: {}", e.getMessage());
                    return content.isBlank() ? null : AiMessage.from(content);
                }
            }
            return content.isBlank() ? null : AiMessage.from(content);
        } else if ("tool".equalsIgnoreCase(role)) {
            String toolCallId = dbMsg.getToolCallId() != null ? dbMsg.getToolCallId() : "";
            String toolName = dbMsg.getToolName() != null ? dbMsg.getToolName() : "";
            return ToolExecutionResultMessage.from(toolCallId, toolName, content);
        }

        // 不再加载数据库中的 System 消息
        return null;
    }

    private Long parseId(Object memoryId) {
        if (memoryId instanceof Long) return (Long) memoryId;
        if (memoryId instanceof String) return Long.parseLong((String) memoryId);
        return 1L; // Fallback
    }

    /**
     * 用于 JSON 序列化/反序列化工具调用请求的 record。
     */
    private record ToolCallData(String id, String name, String arguments) {}
}
