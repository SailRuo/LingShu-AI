package com.lingshu.ai.core.service;

import com.lingshu.ai.infrastructure.entity.UserState;

public interface AffinityService {

    UserState getUserState(String userId);

    UserState getOrCreateUserState(String userId);

    void increaseAffinity(String userId, int delta);

    void decreaseAffinity(String userId, int delta);

    void recordInteraction(String userId);

    void updateEmotion(String userId, String emotion, Double intensity);

    String getRelationshipPrompt(String userId);

    int getAffinity(String userId);

    String getRelationshipStage(String userId);
}
