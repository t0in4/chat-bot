package com.t0in4;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Metadata;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;

@ApplicationScoped
public class ThemeParkChatBotImpl implements ThemeParkChatBot {
    @Inject
    @Named("giga-chat")
    StreamingChatModel model;
    @Inject
    RidesRetrievalAugmentor ridesAugmentor;
    @Inject
    RideRepository rides;
    @Inject
    WaitingTime waitingTime;
    @Inject
    RedisChatMemoryStore store;

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
        memory.add(userMessage(question));

        RetrievalAugmentor augmentor = ridesAugmentor.get();
        ChatMessage latestUserMessage = memory.messages().get(memory.messages().size() - 1);
        Metadata metadata = Metadata.from(latestUserMessage, id, memory.messages().subList(0, memory.messages().size() - 1));
        AugmentationRequest request = new AugmentationRequest(latestUserMessage, metadata);
        AugmentationResult result = augmentor.augment(request);
        result.contents().forEach(content -> {
                    String rideText = content.textSegment().text();
                    memory.add(new AiMessage("CONTENT:\n" + rideText));
                }

        );


        String retrievedContext = result.contents().stream()
                .map(content -> content.textSegment().text())
                .collect(Collectors.joining("\n\n"));
        String ridesData = getRidesSummary();
        System.out.println("🔍 Retrieved Context for LLM:\n" + retrievedContext);
        String systemPrompt = """
                You are a theme park assistant.
                
                CRITICAL RULES FOR HEIGHT QUESTIONS:
                1. The "Retrieved Accessible Rides" section below contains ONLY rides the user can access based on their height.
                2. If a ride is NOT listed in "Retrieved Accessible Rides", it means the user DOES NOT meet the height requirement.
                3. You MUST explicitly state that missing rides are inaccessible due to height restrictions.
                4. Do NOT say "no height restriction mentioned" for rides that are missing from the list.
                
                Retrieved Accessible Rides (FILTERED by user height):
                %s
                
                Current Live Data (All Rides for waiting times):
                %s
                
                User Question: What rides can I access?
                
                Answer Logic:
                - If a ride is in the "Retrieved Accessible Rides" list -> Say it is accessible.
                - If a ride is in "Current Live Data" but NOT in "Retrieved Accessible Rides" -> Say "You cannot access [Ride Name] because your height does not meet the minimum requirement."
                """.formatted(retrievedContext, ridesData);
     /*   String systemPrompt = """
                        CRITICAL RULES (NEVER VIOLATE):
                                1. ONLY use EXACT text from provided context
                                2. NO inventing rides, ratings, heights
                                3. "no data" → "I don't know from ride data"
                                4. List ONLY rides from context
                
                                RIDES DATA:
                                %s
                
                                Question: %s
                """.formatted(ridesData);*/

        memory.add(systemMessage(systemPrompt));


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