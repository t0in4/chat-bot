package com.t0in4;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.chain.ConversationalChain;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.UserMessage;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import io.vertx.core.eventbus.Message;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.List;

@ApplicationScoped
public class ThemeParkChatBotImpl implements ThemeParkChatBot {
    @Inject @Named("giga") StreamingChatModel model;
    @Inject ThemeParkTools tools;

    @Override
    public Multi<String> chat(String question) {
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(5);
        memory.add(systemMessage("You are a theme park assistant. Use tools for rides/waiting."));

        return Multi.createFrom().emitter(em -> {
            agentLoop(question, memory, em);
        });
    }

    private void agentLoop(String question, ChatMemory memory, MultiEmitter<? super String> em) {
        memory.add(userMessage(question));

        model.chat(memory.messages(), new StreamingChatResponseHandler() {
            StringBuilder content = new StringBuilder();

            @Override
            public void onPartialResponse(String partialResponse) {  // ✅ String parameter
                if (partialResponse != null && !partialResponse.isEmpty()) {
                    content.append(partialResponse);
                    em.emit(partialResponse);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                AiMessage aiMsg = aiMessage(content.toString());
                memory.add(aiMsg);

                List<ToolExecutionRequest> toolRequests = aiMsg.toolExecutionRequests();
                if (!toolRequests.isEmpty()) {
                    executeTools(toolRequests, memory, em);
                } else {
                    em.complete();
                }
            }

            @Override
            public void onError(Throwable error) {
                em.fail(error);
            }
        });
    }

    private void executeTools(List<ToolExecutionRequest> requests, ChatMemory memory, MultiEmitter<? super String> em) {
        for (ToolExecutionRequest req : requests) {
            try {
                Object result = invokeTool(req);
                ToolExecutionResultMessage toolMsg = ToolExecutionResultMessage.from(req, result.toString());  // ✅ req + result
                memory.add(toolMsg);
                em.emit("Tool '" + req.name() + "': " + result + "\n");
            } catch (Exception e) {
                em.emit("Tool error: " + e.getMessage() + "\n");
            }
        }
        agentLoop("", memory, em);
    }

    private Object invokeTool(ToolExecutionRequest req) {
        return switch (req.name()) {
            case "listAllRides" -> tools.listAllRides();
            case "getBestRide" -> tools.getBestRide();
            case "getWaitingTime" -> {
                String jsonArgs = req.arguments();  // JSON string
                // Extract rideName from '{"rideName":"Dragon Fun"}'
                String rideName = parseRideName(jsonArgs);
                yield tools.getWaitingTime(rideName);
            }
            default -> "Unknown tool: " + req.name();
        };
    }

    private String parseRideName(String jsonArgs) {
        if (jsonArgs == null) return "Unknown";
        // Simple extraction: '{"rideName":"Dragon Fun"}' → "Dragon Fun"
        return jsonArgs.replaceAll(".*\"rideName\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}