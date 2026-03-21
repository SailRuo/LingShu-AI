package com.lingshu.ai.core.service;

import org.springframework.stereotype.Service;

@Service
public class HeartbeatService {
    /**
     * 系统状态监测，返回当前系统的活跃状态。
     */
    public String pulse() {
        return "灵枢 (LingShu-AI) 运行正常，核心服务处于活跃状态。";
    }
}
