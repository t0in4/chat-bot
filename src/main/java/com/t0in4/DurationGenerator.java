package com.t0in4;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Random;

@ApplicationScoped
public class DurationGenerator {
    private static Random r = new Random();

    public long randomDuration() {
        return r.nextLong(0,90);
    }
}
