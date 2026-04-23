package com.t0in4;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.UserMessage;
import io.smallrye.mutiny.Multi;
import io.vertx.core.eventbus.Message;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;

@ApplicationScoped
public class ThemePartChatBotImpl implements ThemeParkChatBot {
    @Inject
    @Named("giga")
    StreamingChatModel streamingModel;

    private final ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
    @PostConstruct  // 👈 Add this!
    public void init() {
        chatMemory.add(systemMessage("""
        You are theme park assistant.
        Answer ONLY about rides, ratings, waiting times.
        Unknown questions: "I don't know"
        Format: "RideName rating⭐ (X min wait)"
        """));
    }

    @Override
    public Multi<String> chat(String question) {
        chatMemory.add(userMessage(question));

        return Multi.createFrom().emitter(em ->
                streamingModel.chat(  // 👈 generate(), not stream()
                        chatMemory.messages(),
                        new StreamingChatResponseHandler() {
                            @Override
                            public void onPartialResponse(String partialResponse) {
                                em.emit(partialResponse);
                            }

                            @Override
                            public void onCompleteResponse(ChatResponse response) {
                                chatMemory.add(response.aiMessage());
                                em.complete();
                            }

                            @Override
                            public void onError(Throwable error) {
                                em.fail(error);
                            }
                        }
                )
        );
    }
}
