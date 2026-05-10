package com.t0in4;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.smallrye.mutiny.Multi;

public interface ThemeParkChatBot {
    Multi<String> chat(String question, String sessionId);
}
