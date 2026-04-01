package com.example.api;

import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Bucket4jRedisProxyManagerConfig {

    @Bean
    public AsyncProxyManager<String> bucket4jAsyncProxyManager(
            @Value("${REDIS_HOST:redis}") String redisHost,
            @Value("${REDIS_PORT:6379}") int redisPort
    ) {
        RedisClient redisClient = RedisClient.create("redis://" + redisHost + ":" + redisPort);

        // Bucket4j stores distributed bucket state in Redis.
        // For Spring Cloud Gateway rateLimit keyResolver, we use String keys => Redis key codec must be StringCodec.
        StatefulRedisConnection<String, byte[]> connection = redisClient.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
        );

        return LettuceBasedProxyManager.builderFor(connection)
                .build()
                .asAsync();
    }
}

