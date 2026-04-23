package com.t0in4;

import dev.langchain4j.agent.tool.Tool;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;


@ApplicationScoped
public class WaitingTime {
    @Inject DurationGenerator durationGenerator;
    @Inject Logger logger;

    // ❌ NO RedisDataSource constructor

    public void setRandomWaitingTime(String attraction) {
        setWaitingTime(attraction, durationGenerator.randomDuration());
    }

    public void setWaitingTime(String attraction, long waitingTime) {
        // Mock Redis - prints to console
        logger.infof("WaitingTime[%s] = %d min", attraction, waitingTime);
    }

    @Tool("get the waiting time for the given ride name")
    public long getWaitingTime(String attraction) {
        logger.infof("Gets waiting time for %s", attraction);
        return (long) (Math.random() * 60);  // Mock 0-60 min
    }
}