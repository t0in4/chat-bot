package com.t0in4;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.UUID;
import java.util.stream.Collectors;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;

@ApplicationScoped
public class ThemeParkChatBotImpl implements ThemeParkChatBot {
    @Inject @Named("giga") StreamingChatModel model;
    @Inject RideRepository rides;
    @Inject WaitingTime waitingTime;
    @Inject RedisChatMemoryStore store;

    @Override
    public Multi<String> chat(String question, String sessionId) {
        //ChatMemory memory = MessageWindowChatMemory.withMaxMessages(5);
        String id = (sessionId == null || sessionId.isBlank()) ? "default-anonymous" : sessionId;
        ChatMemory memory = MessageWindowChatMemory.builder()
                .id(id) // unique per proxy instance
                .maxMessages(10)
                .chatMemoryStore(store)
                .alwaysKeepSystemMessageFirst(true)
                .build();
        memory.clear();

        String ridesData = getRidesSummary();
        String systemPrompt = """
        You are a theme park assistant.
        Current rides data:
        %s
        
        Answer using ONLY this data.
        Examples:
        - Best ride? → Highest rating ride name + rating⭐
        - Waiting time [ride]? → [ride]: XX minutes
        Unknown → "I don't know"
        """.formatted(ridesData);

        memory.add(systemMessage(systemPrompt));
        memory.add(userMessage(question));

        return Multi.createFrom().emitter(em ->
                model.chat(memory.messages(), new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partial) {
                        System.out.println("🔥 CHUNK RECEIVED: '" + partial + "' (length: " + partial.length() + ")");
                        em.emit(partial);  // ✅ Direct em.emit()
                    }
                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        memory.add(response.aiMessage()); // persist for next turn
                        System.out.println("✅ COMPLETE RESPONSE: " + response.aiMessage());
                        em.emit("END"); // Signal completion
                        em.complete();
                    }
                    @Override
                    public void onError(Throwable error) {
                        System.err.println("❌ STREAM ERROR: " + error);
                        em.fail(error);
                    }
                })
        );
    }

    private String getRidesSummary() {
        return rides.listAll().stream()
                .map(r -> "- " + r.name + ": " + r.rating + "⭐ (waiting: " +
                        waitingTime.getWaitingTime(r.name) + " min)")
                .collect(Collectors.joining("\n"));
    }

  /*  private StreamingChatResponseHandler handler(MultiEmitter<String> em) {  // Raw type
        return new StreamingChatResponseHandler() {
            @Override public void onPartialResponse(String delta) { em.emit(delta); }
            @Override public void onCompleteResponse(ChatResponse r) { em.complete(); }
            @Override public void onError(Throwable e) { em.fail(e); }
        };
    }*/
}