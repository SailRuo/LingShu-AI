package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.EmotionAnalysis;
import com.lingshu.ai.core.event.ProactiveGreetingEvent;
import com.lingshu.ai.core.service.*;
import com.lingshu.ai.infrastructure.entity.AgentConfig;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import com.lingshu.ai.infrastructure.entity.UserState;
import com.lingshu.ai.infrastructure.repository.UserStateRepository;
import com.lingshu.ai.core.config.AiConfig.RawStreamingAssistant;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class ProactiveServiceImpl implements ProactiveService {

    private final UserStateRepository userStateRepository;
    private final AffinityService affinityService;
    private final MemoryService memoryService;
    private final AgentConfigService agentConfigService;
    private final SettingService settingService;
    private final SystemLogService systemLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final PromptBuilderService promptBuilderService;

    @Override
    @Scheduled(fixedRate = 60000)
    @Transactional("transactionManager")
    public void checkInactiveUsers() {
        SystemSetting setting = settingService.getSetting();
        if (setting.getProactiveEnabled() == null || !setting.getProactiveEnabled()) {
            //log.debug("主动问候功能已禁用");
            return;
        }
        
        int inactiveThresholdMinutes = setting.getInactiveThresholdMinutes() != null ? setting.getInactiveThresholdMinutes() : 5;
        
        log.debug("检查不活跃用户...");
        systemLogService.info("定时任务: 检查不活跃用户", "PROACTIVE");

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(inactiveThresholdMinutes);
        List<UserState> inactiveUsers = userStateRepository.findInactiveUsers(threshold);

        log.info("检查不活跃用户，阈值: {} 分钟，用户数: {}", inactiveThresholdMinutes, inactiveUsers.size());

        for (UserState state : inactiveUsers) {
            long minutesInactive = ChronoUnit.MINUTES.between(state.getLastActiveTime(), LocalDateTime.now());
            state.setInactiveHours((int) (minutesInactive / 60));

            log.info("用户 {} 最后活跃时间: {}，已不活跃 {} 分钟",
                    state.getUserId(), state.getLastActiveTime(), minutesInactive);

            log.debug("检查条件: minutesInactive({}) >= threshold({}), needsGreeting({})",
                    minutesInactive, inactiveThresholdMinutes, state.getNeedsGreeting());

            if (minutesInactive >= inactiveThresholdMinutes && !state.getNeedsGreeting()) {
                boolean canGreet = shouldTriggerGreeting(state.getUserId());
                log.info("用户 {} 满足不活跃条件，shouldTriggerGreeting={}", state.getUserId(), canGreet);
                
                if (canGreet) {
                    state.setNeedsGreeting(true);
                    log.info("用户 {} 已不活跃 {} 分钟，标记需要问候", state.getUserId(), minutesInactive);
                    systemLogService.info(String.format("用户 %s 不活跃 %d 分钟，标记需要问候", 
                            state.getUserId(), minutesInactive), "PROACTIVE");
                    
                    String timeOfDay = getTimeOfDay();
                    String greeting = generateContextualGreeting(state.getUserId(), timeOfDay);
                    
                    state.setLastGreetTime(LocalDateTime.now());
                    state.setNeedsGreeting(false);
                    
                    eventPublisher.publishEvent(new ProactiveGreetingEvent(this, state.getUserId(), greeting));
                    
                    systemLogService.success(String.format("已为不活跃用户 %s 推送问候", state.getUserId()), "PROACTIVE");
                }
            }

            userStateRepository.save(state);
        }
    }

    @Override
    @Scheduled(cron = "0 0 8 * * *")
    @Scheduled(cron = "0 0 12 * * *")
    @Scheduled(cron = "0 0 18 * * *")
    @Scheduled(cron = "0 0 22 * * *")
    @Transactional("transactionManager")
    public void scheduledGreeting() {
        SystemSetting setting = settingService.getSetting();
        if (setting.getProactiveEnabled() == null || !setting.getProactiveEnabled()) {
            log.debug("主动问候功能已禁用，跳过定时问候");
            return;
        }
        
        log.info("执行定时问候任务...");
        systemLogService.info("定时任务: 执行问候检查", "PROACTIVE");

        List<UserState> usersToGreet = userStateRepository.findUsersNeedingGreeting();

        for (UserState state : usersToGreet) {
            try {
                if (canSendGreeting(state)) {
                    String timeOfDay = getTimeOfDay();
                    String greeting = generateContextualGreeting(state.getUserId(), timeOfDay);
                    
                    log.info("为用户 {} 生成问候: {}", state.getUserId(), 
                            greeting.length() > 50 ? greeting.substring(0, 50) + "..." : greeting);
                    
                    state.setLastGreetTime(LocalDateTime.now());
                    state.setNeedsGreeting(false);
                    userStateRepository.save(state);
                    
                    eventPublisher.publishEvent(new ProactiveGreetingEvent(this, state.getUserId(), greeting));
                    
                    systemLogService.success(String.format("已为用户 %s 生成问候消息", state.getUserId()), "PROACTIVE");
                }
            } catch (Exception e) {
                log.error("为用户 {} 生成问候失败: {}", state.getUserId(), e.getMessage());
                systemLogService.error(String.format("问候生成失败: %s", e.getMessage()), "PROACTIVE");
            }
        }
    }

    @Override
    public Flux<String> generateGreeting(String userId) {
        systemLogService.info(String.format("为用户 %s 生成问候消息", userId), "PROACTIVE");

        UserState state = affinityService.getOrCreateUserState(userId);
        String memoryContext = memoryService.retrieveContext(userId, "用户最近的状态和重要事件");
        AgentConfig agent = agentConfigService.getDefaultAgent().orElse(null);
        String agentName = agent != null ? agent.getDisplayName() : "灵枢";

        String timeOfDay = getTimeOfDay();
        String relationshipPrompt = affinityService.getRelationshipPrompt(userId);

        String systemPrompt = promptBuilderService.buildMergedSystemPrompt(agent, relationshipPrompt, memoryContext);
        String userPrompt = promptBuilderService.buildGreetingUserPrompt(relationshipPrompt, memoryContext, timeOfDay, agentName);

        return executeStreamingChat(systemPrompt, userPrompt);
    }

    @Override
    public Flux<String> generateComfortMessage(String userId) {
        systemLogService.info(String.format("为用户 %s 生成安慰消息", userId), "PROACTIVE");

        UserState state = affinityService.getUserState(userId);
        if (state == null) {
            return Flux.just("我注意到你似乎有些不开心，有什么我可以帮你的吗？");
        }

        AgentConfig agent = agentConfigService.getDefaultAgent().orElse(null);
        String agentName = agent != null ? agent.getDisplayName() : "灵枢";
        String relationshipPrompt = affinityService.getRelationshipPrompt(userId);

        String systemPrompt = promptBuilderService.buildMergedSystemPrompt(agent, relationshipPrompt, null);
        String userPrompt = promptBuilderService.buildComfortUserPrompt(relationshipPrompt, 
                state.getLastEmotion(), state.getLastEmotionIntensity(), agentName);

        return executeStreamingChat(systemPrompt, userPrompt);
    }

    @Override
    @Transactional("transactionManager")
    public void markUserForGreeting(String userId) {
        UserState state = affinityService.getOrCreateUserState(userId);
        state.setNeedsGreeting(true);
        userStateRepository.save(state);
        log.info("用户 {} 已标记需要问候", userId);
    }

    @Override
    @Transactional("transactionManager")
    public void clearGreetingFlag(String userId) {
        UserState state = userStateRepository.findByUserId(userId).orElse(null);
        if (state != null) {
            state.setNeedsGreeting(false);
            state.setLastGreetTime(LocalDateTime.now());
            userStateRepository.save(state);
        }
    }

    @Override
    public List<UserState> getUsersNeedingAttention() {
        List<UserState> needsGreeting = userStateRepository.findUsersNeedingGreeting();
        List<UserState> negativeEmotion = userStateRepository.findByLastEmotion("negative");
        
        needsGreeting.addAll(negativeEmotion);
        return needsGreeting.stream().distinct().toList();
    }

    @Override
    public boolean shouldTriggerGreeting(String userId) {
        UserState state = userStateRepository.findByUserId(userId).orElse(null);
        if (state == null) {
            log.info("用户 {} 无状态记录，允许问候", userId);
            return true;
        }

        if (state.getLastGreetTime() == null) {
            log.info("用户 {} 从未被问候过，允许问候", userId);
            return true;
        }

        SystemSetting setting = settingService.getSetting();
        int greetingCooldownSeconds = setting.getGreetingCooldownSeconds() != null ? setting.getGreetingCooldownSeconds() : 300;
        
        long secondsSinceLastGreeting = ChronoUnit.SECONDS.between(state.getLastGreetTime(), LocalDateTime.now());
        boolean canGreet = secondsSinceLastGreeting >= greetingCooldownSeconds;
        log.info("用户 {} 上次问候时间: {}，距现在 {} 秒，冷却时间 {} 秒，允许问候: {}",
                userId, state.getLastGreetTime(), secondsSinceLastGreeting, greetingCooldownSeconds, canGreet);
        return canGreet;
    }

    @Override
    public String generateContextualGreeting(String userId, String timeOfDay) {
        UserState state = affinityService.getUserState(userId);
        String relationshipStage = state != null ? state.getRelationshipStage() : "初识";
        
        return switch (timeOfDay) {
            case "早晨" -> relationshipStage.equals("挚友") 
                    ? "早安！新的一天开始了，有什么计划吗？" 
                    : "早上好，希望你今天有个好的开始。";
            case "上午" -> relationshipStage.equals("挚友") 
                    ? "上午好呀，工作还顺利吗？" 
                    : "上午好，有什么我可以帮助你的吗？";
            case "中午" -> relationshipStage.equals("挚友") 
                    ? "中午了，记得吃午饭哦~" 
                    : "中午好，希望你上午过得愉快。";
            case "下午" -> relationshipStage.equals("挚友") 
                    ? "下午好！今天过得怎么样？" 
                    : "下午好，有什么需要帮忙的吗？";
            case "傍晚" -> relationshipStage.equals("挚友") 
                    ? "傍晚了，今天辛苦了~" 
                    : "傍晚好，希望你今天一切顺利。";
            case "晚上" -> relationshipStage.equals("挚友") 
                    ? "晚上好！今天有什么有趣的事吗？" 
                    : "晚上好，有什么我可以帮你的吗？";
            case "深夜" -> relationshipStage.equals("挚友") 
                    ? "这么晚还在忙？早点休息吧~" 
                    : "夜深了，注意休息。";
            default -> "你好，有什么我可以帮你的吗？";
        };
    }

    private boolean canSendGreeting(UserState state) {
        if (state.getLastGreetTime() == null) {
            return true;
        }
        
        SystemSetting setting = settingService.getSetting();
        int greetingCooldownSeconds = setting.getGreetingCooldownSeconds() != null ? setting.getGreetingCooldownSeconds() : 300;
        
        long secondsSinceLastGreeting = ChronoUnit.SECONDS.between(state.getLastGreetTime(), LocalDateTime.now());
        return secondsSinceLastGreeting >= greetingCooldownSeconds;
    }

    private String getTimeOfDay() {
        int hour = LocalTime.now().getHour();
        
        if (hour >= 5 && hour < 9) {
            return "早晨";
        } else if (hour >= 9 && hour < 12) {
            return "上午";
        } else if (hour >= 12 && hour < 14) {
            return "中午";
        } else if (hour >= 14 && hour < 17) {
            return "下午";
        } else if (hour >= 17 && hour < 19) {
            return "傍晚";
        } else if (hour >= 19 && hour < 23) {
            return "晚上";
        } else {
            return "深夜";
        }
    }

    private Flux<String> executeStreamingChat(String systemPrompt, String userPrompt) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        try {
            var setting = settingService.getSetting();
            
            if (setting == null || setting.getBaseUrl() == null || setting.getChatModel() == null) {
                log.warn("系统设置未配置，使用默认问候语");
                sink.tryEmitNext("你好，有什么我可以帮你的吗？");
                sink.tryEmitComplete();
                return sink.asFlux();
            }
            
            StreamingChatModel model;

            if ("ollama".equalsIgnoreCase(setting.getSource()) || 
                (setting.getSource() == null && setting.getBaseUrl().contains("11434"))) {
                model = dev.langchain4j.model.ollama.OllamaStreamingChatModel.builder()
                        .baseUrl(setting.getBaseUrl())
                        .modelName(setting.getChatModel())
                        .timeout(java.time.Duration.ofMinutes(2))
                        .build();
            } else {
                String url = setting.getBaseUrl();
                model = dev.langchain4j.model.openai.OpenAiStreamingChatModel.builder()
                        .baseUrl(url.endsWith("/v1") || url.endsWith("/v1/") ? url : url + (url.endsWith("/") ? "v1" : "/v1"))
                        .apiKey(setting.getApiKey() != null && !setting.getApiKey().isBlank() ? setting.getApiKey() : "no-key")
                        .modelName(setting.getChatModel())
                        .timeout(java.time.Duration.ofMinutes(2))
                        .build();
            }

            var assistant = dev.langchain4j.service.AiServices.builder(RawStreamingAssistant.class)
                    .streamingChatModel(model)
                    .build();

            assistant.chat(1L, systemPrompt, userPrompt)
                    .onPartialThinking(thinking -> systemLogService.thinking(thinking.text(), "PROACTIVE"))
                    .onPartialResponse(sink::tryEmitNext)
                    .onCompleteResponse(response -> sink.tryEmitComplete())
                    .onError(sink::tryEmitError)
                    .start();
        } catch (Exception e) {
            log.error("流式聊天执行失败: {}", e.getMessage());
            sink.tryEmitNext("你好，有什么我可以帮你的吗？");
            sink.tryEmitComplete();
        }

        return sink.asFlux();
    }
}
