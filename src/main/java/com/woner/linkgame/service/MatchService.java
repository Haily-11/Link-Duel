package com.woner.linkgame.service;

import com.woner.linkgame.dto.RoomState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static com.woner.linkgame.service.RedisKeys.MATCH_QUEUE;
import static com.woner.linkgame.service.RedisKeys.userRoom;

/**
 * 匹配服务：用 Redis List 维护匹配队列。
 * 两人匹配成功后调用 GameService 创建房间并生成同一份棋盘。
 */
@Service
public class MatchService {

    private final StringRedisTemplate redis;
    private final GameService gameService;

    public MatchService(StringRedisTemplate redis, GameService gameService) {
        this.redis = redis;
        this.gameService = gameService;
    }

    /**
     * 加入匹配。返回 Optional：
     * - empty：已入队等待（尚未匹配到对手）
     * - present：匹配成功（可能是新建房间，也可能是已在进行中的房间，幂等）
     */
    public Optional<RoomState> joinMatch(long userId) {
        Optional<RoomState> existing = gameService.findActiveRoom(userId);
        if (existing.isPresent()) {
            return existing;
        }

        String uid = String.valueOf(userId);
        // 防止同一用户重复入队
        redis.opsForList().remove(MATCH_QUEUE, 1, uid);
        redis.opsForList().leftPush(MATCH_QUEUE, uid);

        // 尝试从队尾拉取一位对手
        String other = null;
        while (true) {
            String popped = redis.opsForList().rightPop(MATCH_QUEUE);
            if (popped == null) {
                break;
            }
            if (popped.equals(uid)) {
                // 拉到的是自己：放回队头，继续等待
                redis.opsForList().leftPush(MATCH_QUEUE, uid);
                break;
            }
            other = popped;
            break;
        }

        if (other == null) {
            return Optional.empty();
        }
        RoomState room = gameService.createRoom(userId, Long.parseLong(other));
        return Optional.of(room);
    }

    /** 取消匹配（玩家在等待中主动离开，例如退出登录）。 */
    public void cancelMatch(long userId) {
        redis.opsForList().remove(MATCH_QUEUE, 1, String.valueOf(userId));
    }
}