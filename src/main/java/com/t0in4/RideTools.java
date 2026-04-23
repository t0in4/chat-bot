package com.t0in4;

import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;

import java.util.stream.Collectors;

public class RideTools {
    @Inject
    RideRepository rides;

    @Tool("Get all rides with ratings")
    public String listRides() {
        return rides.listAll().stream()
                .map(r -> r.name + " " + r.rating + "⭐")
                .collect(Collectors.joining(", "));
    }
}