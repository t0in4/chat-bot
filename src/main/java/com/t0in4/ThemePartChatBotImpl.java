package com.t0in4;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.UserMessage;
import io.smallrye.mutiny.Multi;
import io.vertx.core.eventbus.Message;
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


    @Override
    public Multi<String> chat(String question) {
        if (chatMemory.messages().isEmpty()) {
            chatMemory.add(systemMessage("""
                    You are an assistant for answering questions about the theme park.
                    These questions can only be related to theme park.
                    Examples of these questions can be:
                    - Can you describe a given ride?
                    - What is the minimum height to enter to a ride?
                    - What rides can I access with my height?
                    - What is the best ride at the moment?
                    - What is the waiting time for a given ride?
                    If questions are not about theme park or you don't know the answer,
                    you should always return "I don't know".
                    Don't give information that is wrong
                    """)

            );
            chatMemory.add(userMessage("""
                    The theme park user has the following question: {question}
                    The answer must be max 2 lines.
                    """));
        }
        return Multi.createFrom().emitter(em ->
                        streamingModel.chat(chatMemory.messages(),
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
