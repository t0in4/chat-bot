package com.t0in4;

import dev.langchain4j.data.message.AiMessage;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.stream.Collectors;

@Path("/ride")
public class RideResource {

    @Inject
    RideRepository rideRepository;
    @Inject
    WaitingTime waitingTime;
    @io.quarkus.runtime.Startup
    @jakarta.transaction.Transactional
    public void populateData() {
        insertRides();
    }
    private void insertRides() {
        Ride r1 = new Ride();
        r1.name = "Oncharted. My Penitence";
        r1.rating = 5.0;
        rideRepository.persist(r1);
        waitingTime.setRandomWaitingTime(r1.name);
        Ride r2 = new Ride();
        r2.name = "Dragon Fun";
        r2.rating = 4.9;
        rideRepository.persist(r2);
        waitingTime.setRandomWaitingTime(r2.name);
    }
    @Inject
    ThemeParkChatBot themeParkChatBot;
    @GET @Path("/chat/best")
    public String askForTheBest() {
        List<String> tokens = themeParkChatBot
                .chat("Best ride name + rating")
                .collect().asList()
                .await().indefinitely();

        return tokens.stream()
                .filter(s -> s.length() > 1)  // Skip single chars
                .map(String::trim)
                .reduce((a, b) -> a + " " + b)
                .orElse("No response");
    }
    @GET
    @Path("/chat/waiting")
    public String askForWaitingTime() {
        return this.themeParkChatBot
                .chat("What is the waiting time for Dragon Fun ride?")
                .collect().asList()
                .await().indefinitely()
                .stream()
                .map(Object::toString)
                .filter(s -> !s.trim().isEmpty())  // Better filter
                .collect(Collectors.joining(" "));  // ✅
    }

}
