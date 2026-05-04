package com.t0in4;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.smallrye.mutiny.Multi;

/*// this leverages @Tool discovery
@SystemMessage("""
         You are a theme park assistant. Use tools for accurate ride info.
            Examples: "best ride?", "waiting time for Dragon Fun".
        """)
public interface ChatBotAgent {
    @UserMessage
    Multi<String> chat(String question, StreamingResponseHandler<String> handler);
}*/
