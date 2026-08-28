package com.woner.linkgame.service;

import com.woner.linkgame.dto.RankingEntry;
import com.woner.linkgame.entity.User;
import com.woner.linkgame.repository.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.woner.linkgame.service.RedisKeys.LEADERBOARD;

/**
 * 排行榜：读取 Redis ZSET（member=userId，score=累计积分），
 * 再回查 MySQL 补充邮箱与胜场信息。
 */
@Service
public class RankingService {

    private final StringRedisTemplate redis;
    private final UserRepository userRepository;

    public RankingService(StringRedisTemplate redis, UserRepository userRepository) {
        this.redis = redis;
        this.userRepository = userRepository;
    }

    public List<RankingEntry> top(int n) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redis.opsForZSet().reverseRangeWithScores(LEADERBOARD, 0, n - 1);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<RankingEntry> result = new ArrayList<>(tuples.size());
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            long userId = Long.parseLong(t.getValue());
            int points = (int) t.getScore().doubleValue();
            User u = userRepository.findById(userId).orElse(null);
            String email = u != null ? u.getEmail() : "未知用户";
            int wins = u != null ? u.getWins() : 0;
            result.add(new RankingEntry(rank++, userId, email, wins, points));
        }
        return result;
    }
}