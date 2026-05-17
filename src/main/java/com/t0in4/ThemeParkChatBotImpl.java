package com.t0in4;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
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
import org.eclipse.microprofile.context.ManagedExecutor;

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
    @Inject
    ManagedExecutor executor;

    @Override
    public Multi<String> chat(String question, String sessionId) {

        String id = (sessionId == null || sessionId.isBlank()) ? "default-anonymous" : sessionId;
        ChatMemory memory = MessageWindowChatMemory.builder()
                .id(id) // unique per proxy instance
                .maxMessages(10)
                .chatMemoryStore(store)
                .alwaysKeepSystemMessageFirst(true)
                .build();
        memory.add(userMessage(question));
        System.out.println("🔧 Getting RetrievalAugmentor...");
        RetrievalAugmentor augmentor = ridesAugmentor.get();
        System.out.println("✅ Augmentor obtained: " + augmentor);
        ChatMessage latestUserMessage = memory.messages().get(memory.messages().size() - 1);
        Metadata metadata = Metadata.from(latestUserMessage, id, memory.messages().subList(0, memory.messages().size() - 1));
        AugmentationRequest request = new AugmentationRequest(latestUserMessage, metadata);
        System.out.println("🔍 Starting augmentation with query: " + latestUserMessage);
        AugmentationResult result = augmentor.augment(request);
        List<Content> contents = result.contents();
        System.out.println("📦 Augmentation complete. Retrieved " + contents.size() + " content items.");

// 2. Extract the names of the ALLOWED rides from the filtered content
        System.out.println("🎢 Processing " + contents.size() + " content items to extract allowed ride names...");
        Set<String> allowedRideNames = contents.stream()
                .map(content -> content.textSegment().metadata().getString("file_name"))
                .filter(Objects::nonNull)
                .map(name -> name.replace(".txt", "")) // Normalize name if needed
                .collect(Collectors.toSet());
        System.out.println("✅ Allowed ride names extracted: " + allowedRideNames);
        System.out.println("📋 Fetching all rides from repository...");
// 3. Filter the LIVE data list to ONLY include allowed rides
        String ridesData = rides.listAll().stream()
                .filter(r -> {
                    // Check if this ride's name matches an allowed file name
                    // You might need to adjust the matching logic based on your file names vs DB names
                    String normalizedName = r.name.toLowerCase().replace(" ", "").replace(".", "");
                    return allowedRideNames.stream().anyMatch(allowed ->
                            allowed.toLowerCase().replace(".txt", "").replace(" ", "").replace(".", "").contains(normalizedName) ||
                                    normalizedName.contains(allowed.toLowerCase().replace(".txt", ""))
                    );
                })
                .map(r -> "- " + r.name + ": " + r.rating + "⭐ (waiting: " +
                        waitingTime.getWaitingTime(r.name) + " min)")
                .collect(Collectors.joining("\\n"));
        System.out.println("📊 Filtered rides data:\\n" + ridesData);
// 4. Update System Prompt to be strict
        String systemPrompt = """
                You are a theme park assistant.
                
                CRITICAL RULES:
                1. The "Retrieved Context" below contains ONLY rides the user can access based on their height.
                2. The "Current Live Data" below ALSO contains ONLY rides the user can access.
                3. If a ride is NOT present in these lists, the user CANNOT access it due to height restrictions.
                4. Do NOT mention rides that are not in the provided lists.
                
                Retrieved Context (Accessible Rides):
                %s
                
                Current Live Data (Accessible Rides Only):
                %s
                
                Answer the user's question using ONLY the data above.
                """.formatted(
                contents.stream().map(c -> c.textSegment().text()).collect(Collectors.joining("\n\n")),
                ridesData
        );
        System.out.println("📝 System prompt created (length: " + systemPrompt.length() + ")");
        System.out.println("💾 Adding system message to memory...");
        memory.add(systemMessage(systemPrompt));

        System.out.println("🚀 Sending request to streaming chat model...");
        return Multi.createFrom().emitter(em ->
                model.chat(memory.messages(), new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partial) {
                        System.out.println("🔥 CHUNK RECEIVED: '" + partial + "' (length: " + partial.length() + ")");
                        em.emit(partial);
                    }

                    @Override
                    public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                        memory.add(response.aiMessage());
                        System.out.println("✅ COMPLETE RESPONSE: " + response.aiMessage());
                        em.emit("END"); // Signal completion
                        em.complete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        System.err.println("❌ STREAM ERROR: " + error);
                        em.fail(error);
                    }
                }
                ));


    }

    private String getRidesSummary() {
        return rides.listAll().stream()
                .map(r -> "- " + r.name + ": " + r.rating + "⭐ (waiting: " +
                        waitingTime.getWaitingTime(r.name) + " min)")
                .collect(Collectors.joining("\n"));
    }

}