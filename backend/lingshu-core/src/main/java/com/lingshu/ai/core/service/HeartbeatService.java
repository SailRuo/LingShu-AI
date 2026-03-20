package com.lingshu.ai.core.service;

import org.springframework.stereotype.Service;

@Service
public class HeartbeatService {
    public String pulse() {
        return "LingShu-AI is alive and breathing.";
    }
}
