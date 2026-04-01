package com.example.order.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

@Configuration
public class OrderRedisLuaConfig {

    // Status:
    // - OK: { "OK", orderDraftId, remainingStock }
    // - DUPLICATE: { "DUPLICATE", existingDraftId, remainingStock }
    // - OUT_OF_STOCK: { "OUT_OF_STOCK", "", "0" }
    @Bean
    public DefaultRedisScript<List> orderCreateLuaScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setResultType(List.class);
        // Dùng \n (một dấu \) — không dùng \\n: Redis nhận literal \n và Lua báo "unexpected symbol near '\\'"
        script.setScriptText(
                "local dedupKey = KEYS[1]\n" +
                "local stockKey = KEYS[2]\n" +
                "local pendingKey = KEYS[3]\n" +
                "local orderDraftId = ARGV[1]\n" +
                "local ttlSeconds = tonumber(ARGV[2])\n" +
                "\n" +
                "if redis.call('exists', dedupKey) == 1 then\n" +
                "  local existing = redis.call('get', dedupKey)\n" +
                "  local stock = tonumber(redis.call('get', stockKey) or '0')\n" +
                "  return {'DUPLICATE', existing, stock}\n" +
                "end\n" +
                "\n" +
                "local stock = tonumber(redis.call('get', stockKey) or '0')\n" +
                "if stock <= 0 then\n" +
                "  return {'OUT_OF_STOCK', '', 0}\n" +
                "end\n" +
                "\n" +
                "redis.call('decrby', stockKey, 1)\n" +
                "local remaining = tonumber(redis.call('get', stockKey) or '0')\n" +
                "\n" +
                "redis.call('set', dedupKey, orderDraftId, 'EX', ttlSeconds)\n" +
                "redis.call('set', pendingKey, '1', 'EX', ttlSeconds)\n" +
                "return {'OK', orderDraftId, remaining}\n"
        );
        return script;
    }

    /**
     * Cart multi-items Lua:
     * KEYS[1] = dedupKey
     * KEYS[2] = pendingKey
     * KEYS[3..] = stockKey per item (aligned with quantities ARGV[3..])
     *
     * ARGV[1] = orderDraftId
     * ARGV[2] = ttlSeconds
     * ARGV[3..] = quantities per item
     *
     * Return: { status, draftId, remaining1, remaining2, ... }
     */
    @Bean
    public DefaultRedisScript<List> orderCartCreateLuaScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setResultType(List.class);
        script.setScriptText(
                "local dedupKey = KEYS[1]\n" +
                "local pendingKey = KEYS[2]\n" +
                "local orderDraftId = ARGV[1]\n" +
                "local ttlSeconds = tonumber(ARGV[2])\n" +
                "\n" +
                "local n = #KEYS - 2\n" +
                "if redis.call('exists', dedupKey) == 1 then\n" +
                "  local existing = redis.call('get', dedupKey)\n" +
                "  local remaining = {}\n" +
                "  for i=1,n do\n" +
                "    remaining[i] = tonumber(redis.call('get', KEYS[2+i]) or '0')\n" +
                "  end\n" +
                "  return {'DUPLICATE', existing, unpack(remaining)}\n" +
                "end\n" +
                "\n" +
                "local remaining = {}\n" +
                "for i=1,n do\n" +
                "  local stock = tonumber(redis.call('get', KEYS[2+i]) or '0')\n" +
                "  local qty = tonumber(ARGV[2+i])\n" +
                "  remaining[i] = stock\n" +
                "  if stock < qty then\n" +
                "    return {'OUT_OF_STOCK', '', unpack(remaining)}\n" +
                "  end\n" +
                "end\n" +
                "\n" +
                "for i=1,n do\n" +
                "  local qty = tonumber(ARGV[2+i])\n" +
                "  redis.call('decrby', KEYS[2+i], qty)\n" +
                "end\n" +
                "\n" +
                "for i=1,n do\n" +
                "  remaining[i] = tonumber(redis.call('get', KEYS[2+i]) or '0')\n" +
                "end\n" +
                "\n" +
                "redis.call('set', dedupKey, orderDraftId, 'EX', ttlSeconds)\n" +
                "redis.call('set', pendingKey, '1', 'EX', ttlSeconds)\n" +
                "return {'OK', orderDraftId, unpack(remaining)}\n"
        );
        return script;
    }
}

