package com.t0in4;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ServerEndpoint("/chat")  // ✅ No path param needed
@ApplicationScoped // for quarkus scope
public class ChatWebSocket {
    @Inject
    ThemeParkChatBot chatBot;
    private static final ConcurrentHashMap<String, String> SESSION_MAP = new ConcurrentHashMap<>();
    private static final AtomicInteger SESSION_COUNTER = new AtomicInteger();

    @OnOpen
    public void onOpen(Session session) {
        String wsSessionId = session.getId(); // Guaranteed unique ID
        String internalSessionId = "ws-" + SESSION_COUNTER.incrementAndGet();

        SESSION_MAP.put(wsSessionId, internalSessionId);
        System.out.println("onOpen: Mapped " + wsSessionId + " -> " + internalSessionId);
    }

    @OnMessage
    public void onMessage(String question, Session session) {
        String wsSessionId = session.getId();
        String sessionId = SESSION_MAP.get(wsSessionId);

        System.out.println("onMessage: Retrieved sessionId '" + sessionId + "' for WS ID " + wsSessionId);

        if (sessionId == null) {
            System.err.println("ERROR: No session mapping for " + wsSessionId);
            return;
        }

        // Your existing reactive chain
        Uni.createFrom().item(() -> chatBot.chat(question, sessionId))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem().transformToMulti(multi -> multi)
                .subscribe().with(
                        chunk -> session.getAsyncRemote().sendText(chunk, result -> {
                            if (!result.isOK()) System.err.println("Send failed: " + result.getException());
                        }),
                        err -> System.err.println("Chat error: " + err)
                );
    }
    @OnClose
    public void onClose(Session session) {
        String wsSessionId = session.getId();
        SESSION_MAP.remove(wsSessionId);
        System.out.println("onClose: Removed " + wsSessionId);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        String wsSessionId = session.getId();
        SESSION_MAP.remove(wsSessionId);
        System.err.println("WebSocket error for " + wsSessionId + ": " + error);
    }
}