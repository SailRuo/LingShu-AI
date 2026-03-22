package com.lingshu.ai.core.dto;

public class SystemLog {
    private String time;
    private String content;
    private String type;
    private String section;

    public SystemLog() {}

    public SystemLog(String time, String content, String type, String section) {
        this.time = time;
        this.content = content;
        this.type = type;
        this.section = section;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public static class Builder {
        private String time;
        private String content;
        private String type;
        private String section;

        public Builder time(String time) { this.time = time; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder type(String type) { this.type = type; return this; }
        public Builder section(String section) { this.section = section; return this; }

        public SystemLog build() {
            return new SystemLog(time, content, type, section);
        }
    }
}
