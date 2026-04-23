package com.t0in4;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.stream.Collectors;

@ApplicationScoped
public class ThemeParkTools {

    @Inject RideRepository rides;
    @Inject WaitingTime waitingTime;

    @Tool("Get all rides with ratings")
    public String listAllRides() {
        return rides.listAll().stream()
                .map(r -> r.name + " (" + r.rating + "⭐)")
                .collect(Collectors.joining(", "));
    }

    @Tool("Get best ride by rating")
    public String getBestRide() {
        RideRecord best = rides.getTheBestRideByRatings();
        return best.name() + " " + best.rating() + "⭐";
    }

    @Tool("Get waiting time for specific ride")
    public String getWaitingTime(String rideName) {
        Long time = waitingTime.getWaitingTime(rideName);
        return rideName + ": " + time + " minutes";
    }
}