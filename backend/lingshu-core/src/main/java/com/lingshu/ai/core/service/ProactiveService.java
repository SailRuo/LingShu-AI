package com.lingshu.ai.core.service;

import com.lingshu.ai.infrastructure.entity.UserState;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ProactiveService {

    void checkInactiveUsers();

    void scheduledGreeting();

    Flux<String> generateGreeting(String userId);

    Flux<String> generateComfortMessage(String userId);

    void markUserForGreeting(String userId);

    void clearGreetingFlag(String userId);

    List<UserState> getUsersNeedingAttention();

    boolean shouldTriggerGreeting(String userId);

    String generateContextualGreeting(String userId, String timeOfDay);
}
