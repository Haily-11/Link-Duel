package com.woner.linkgame.service;

import com.woner.linkgame.dto.MoveOutcome;
import com.woner.linkgame.dto.RoomState;
import com.woner.linkgame.dto.SettleOutcome;
import com.woner.linkgame.entity.GameRecord;
import com.woner.linkgame.entity.User;
import com.woner.linkgame.repository.GameRecordRepository;
import com.woner.linkgame.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.woner.linkgame.service.RedisKeys.*;

/**
 * 游戏核心：房间状态读写、走子合法性校验、结束判定、幂等结算与排行榜更新。
 *
 * <p>Redis 承担：匹配队列、房间权威状态（棋盘 / 双方 / 分数）、断线在线标记、
 * 排行榜 ZSET、结算幂等标记、房间级互斥锁；MySQL 保存最终对局记录与用户战绩。</p>
 */
@Service
public class GameService {

    private static final long ROOM_TTL_HOURS = 2;

    private final StringRedisTemplate redis;
    private final BoardService boardService;
    private final LinkSearchService linkSearchService;
    private final UserRepository userRepository;
    private final GameRecordRepository gameRecordRepository;

    private final int scorePerPair;
    private final long graceSeconds;

    public GameService(StringRedisTemplate redis,
                       BoardService boardService,
                       LinkSearchService linkSearchService,
                       UserRepository userRepository,
                       GameRecordRepository gameRecordRepository,
                       @Value("${linkgame.score-per-pair:10}") int scorePerPair,
                       @Value("${linkgame.reconnect-grace-seconds:60}") long graceSeconds) {
        this.redis = redis;
        this.boardService = boardService;
        this.linkSearchService = linkSearchService;
        this.userRepository = userRepository;
        this.gameRecordRepository = gameRecordRepository;
        this.scorePerPair = scorePerPair;
        this.graceSeconds = graceSeconds;
    }

    // ------------------------------------------------------------------
    // 房间读取
    // ------------------------------------------------------------------

    public Optional<RoomState> findActiveRoom(long userId) {
        String roomId = redis.opsForValue().get(userRoom(userId));
        if (roomId == null) {
            return Optional.empty();
        }
        Map<Object, Object> h = roomHash(roomId);
        if (h.isEmpty() || !"PLAYING".equals(h.get("status"))) {
            return Optional.empty();
        }
        return Optional.of(toRoomState(roomId, h));
    }

    /** 创建一个新房间，双方拿到同一份棋盘。 */
    public RoomState createRoom(long playerA, long playerB) {
        String roomId = UUID.randomUUID().toString().replace("-", "");
        int[][] board = boardService.generateBoard();
        RoomState state = new RoomState(roomId, playerA, playerB, board, 0, 0, "PLAYING");
        putRoom(state);
        redis.opsForValue().set(userRoom(playerA), roomId, Duration.ofHours(ROOM_TTL_HOURS));
        redis.opsForValue().set(userRoom(playerB), roomId, Duration.ofHours(ROOM_TTL_HOURS));
        redis.opsForSet().add(ROOMS_ACTIVE, roomId);
        return state;
    }

    // ------------------------------------------------------------------
    // 走子校验
    // ------------------------------------------------------------------

    public MoveOutcome tryEliminate(long userId, String roomId, int r1, int c1, int r2, int c2) {
        // 房间级互斥锁：保证「读取棋盘 -> 校验 -> 写回」原子，避免并发重复消除
        String lockKey = roomLock(roomId);
        boolean locked = Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(5)));
        if (!locked) {
            return MoveOutcome.reject("操作过于频繁，请稍后重试");
        }
        try {
            return doEliminate(userId, roomId, r1, c1, r2, c2);
        } finally {
            redis.delete(lockKey);
        }
    }

    private MoveOutcome doEliminate(long userId, String roomId, int r1, int c1, int r2, int c2) {
        String myRoom = redis.opsForValue().get(userRoom(userId));
        if (myRoom == null || !myRoom.equals(roomId)) {
            return MoveOutcome.reject("房间不存在或你已不在该房间");
        }
        Map<Object, Object> h = roomHash(roomId);
        if (h.isEmpty() || !"PLAYING".equals(h.get("status"))) {
            return MoveOutcome.reject("对局已结束");
        }
        long playerA = parseLong(h.get("playerA"));
        long playerB = parseLong(h.get("playerB"));
        if (userId != playerA && userId != playerB) {
            return MoveOutcome.reject("你不属于该房间");
        }

        int[][] board = boardService.fromJson((String) h.get("board"));
        int scoreA = Integer.parseInt((String) h.get("scoreA"));
        int scoreB = Integer.parseInt((String) h.get("scoreB"));

        if (!inBounds(board, r1, c1) || !inBounds(board, r2, c2)) {
            return MoveOutcome.reject("坐标越界");
        }
        if (r1 == r2 && c1 == c2) {
            return MoveOutcome.reject("不能选择同一位置");
        }
        int v1 = board[r1][c1];
        int v2 = board[r2][c2];
        if (v1 == 0 || v2 == 0) {
            return MoveOutcome.reject("该位置已为空");
        }
        if (v1 != v2) {
            return MoveOutcome.reject("图案不同，无法消除");
        }
        List<int[]> path = linkSearchService.findPath(board, r1, c1, r2, c2);
        if (path == null) {
            return MoveOutcome.reject("路径超过两次转弯，无法消除");
        }

        // 合法：执行消除
        board[r1][c1] = 0;
        board[r2][c2] = 0;
        boolean isA = (userId == playerA);
        if (isA) {
            scoreA += scorePerPair;
        } else {
            scoreB += scorePerPair;
        }

        int remaining = boardService.remainingPairs(board);
        // 死局检测：仍有图案但无解 -> 自动洗牌
        if (remaining > 0 && linkSearchService.findAnyValidPair(board) == null) {
            board = boardService.reshuffle(board);
        }

        boolean gameOver = (remaining == 0);
        Long winnerId = null;
        String endReason = null;
        if (gameOver) {
            endReason = "CLEAR";
            winnerId = (scoreA == scoreB) ? null : (scoreA > scoreB ? playerA : playerB);
        }

        RoomState state = new RoomState(roomId, playerA, playerB, board, scoreA, scoreB,
                gameOver ? "FINISHED" : "PLAYING");
        putRoom(state);
        if (gameOver) {
            settle(roomId, endReason, winnerId, scoreA, scoreB, "COMPLETED");
        }
        return new MoveOutcome(true, "ok", state, path,
                new int[]{r1, c1}, new int[]{r2, c2}, userId, gameOver, winnerId, endReason);
    }

    // ------------------------------------------------------------------
    // 结束与结算
    // ------------------------------------------------------------------

    /** 玩家主动认输。返回 null 表示无进行中的对局。 */
    public SettleOutcome surrender(long userId) {
        String roomId = redis.opsForValue().get(userRoom(userId));
        if (roomId == null) {
            return null;
        }
        Map<Object, Object> h = roomHash(roomId);
        if (h.isEmpty() || !"PLAYING".equals(h.get("status"))) {
            return null;
        }
        long playerA = parseLong(h.get("playerA"));
        long playerB = parseLong(h.get("playerB"));
        if (userId != playerA && userId != playerB) {
            return null;
        }
        Long winnerId = (userId == playerA) ? playerB : playerA;
        int scoreA = Integer.parseInt((String) h.get("scoreA"));
        int scoreB = Integer.parseInt((String) h.get("scoreB"));
        settle(roomId, "SURRENDER", winnerId, scoreA, scoreB, "COMPLETED");
        return new SettleOutcome(roomId, winnerId, scoreA, scoreB, "SURRENDER");
    }

    /**
     * 幂等结算：Redis SETNX 快速去重 + MySQL 唯一约束硬保证，
     * 服务重启或重复请求都不会把同一局结算两次。
     */
    public void settle(String roomId, String endReason, Long winnerId, int scoreA, int scoreB, String status) {
        Boolean first = redis.opsForValue().setIfAbsent(settled(roomId), "1", Duration.ofHours(24));
        if (!Boolean.TRUE.equals(first)) {
            return;
        }
        Map<Object, Object> h = roomHash(roomId);
        if (h.isEmpty()) {
            return;
        }
        long playerA = parseLong(h.get("playerA"));
        long playerB = parseLong(h.get("playerB"));

        try {
            gameRecordRepository.save(GameRecord.builder()
                    .roomId(roomId)
                    .playerAId(playerA)
                    .playerBId(playerB)
                    .winnerId(winnerId)
                    .scoreA(scoreA)
                    .scoreB(scoreB)
                    .status(status)
                    .endReason(endReason)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 唯一约束冲突：该房间已结算，直接返回，不重复记账
            return;
        }

        applyResult(playerA, playerB, winnerId, scoreA, scoreB);
        cleanup(roomId, playerA, playerB);
    }

    /** 更新用户战绩与 Redis 排行榜。 */
    private void applyResult(long playerA, long playerB, Long winnerId, int scoreA, int scoreB) {
        final int BONUS_WIN = 20;
        final int BONUS_DRAW = 10;

        User ua = userRepository.findById(playerA).orElse(null);
        User ub = userRepository.findById(playerB).orElse(null);

        int bonusA = 0;
        int bonusB = 0;
        if (winnerId == null) {
            bonusA = BONUS_DRAW;
            bonusB = BONUS_DRAW;
            if (ua != null) {
                ua.setDraws(ua.getDraws() + 1);
            }
            if (ub != null) {
                ub.setDraws(ub.getDraws() + 1);
            }
        } else if (winnerId == playerA) {
            bonusA = BONUS_WIN;
            if (ua != null) {
                ua.setWins(ua.getWins() + 1);
            }
            if (ub != null) {
                ub.setLosses(ub.getLosses() + 1);
            }
        } else {
            bonusB = BONUS_WIN;
            if (ub != null) {
                ub.setWins(ub.getWins() + 1);
            }
            if (ua != null) {
                ua.setLosses(ua.getLosses() + 1);
            }
        }

        if (ua != null) {
            ua.setPoints(ua.getPoints() + scoreA + bonusA);
            userRepository.save(ua);
            redis.opsForZSet().incrementScore(LEADERBOARD, String.valueOf(playerA), scoreA + bonusA);
        }
        if (ub != null) {
            ub.setPoints(ub.getPoints() + scoreB + bonusB);
            userRepository.save(ub);
            redis.opsForZSet().incrementScore(LEADERBOARD, String.valueOf(playerB), scoreB + bonusB);
        }
    }

    private void cleanup(String roomId, long playerA, long playerB) {
        redis.delete(room(roomId));
        redis.delete(userRoom(playerA));
        redis.delete(userRoom(playerB));
        redis.opsForSet().remove(ROOMS_ACTIVE, roomId);
    }

    // ------------------------------------------------------------------
    // 断线 / 重连
    // ------------------------------------------------------------------

    /** 玩家断线：在房间 Hash 记录离线时间戳，进入宽限倒计时。 */
    public void markDisconnected(long userId) {
        String roomId = redis.opsForValue().get(userRoom(userId));
        if (roomId == null) {
            return;
        }
        redis.opsForHash().put(room(roomId), offlineField(userId), String.valueOf(System.currentTimeMillis()));
    }

    /** 玩家重连：清除离线标记并返回进行中的房间状态（无则 null）。 */
    public RoomState reconnect(long userId) {
        String roomId = redis.opsForValue().get(userRoom(userId));
        if (roomId == null) {
            return null;
        }
        Map<Object, Object> h = roomHash(roomId);
        if (h.isEmpty() || !"PLAYING".equals(h.get("status"))) {
            return null;
        }
        redis.opsForHash().delete(room(roomId), offlineField(userId));
        return toRoomState(roomId, h);
    }

    /** 定时扫描：离线超过宽限期的玩家判负，避免对局悬挂。 */
    @Scheduled(fixedDelay = 10000, initialDelay = 15000)
    public void scanForfeits() {
        Set<String> roomIds = redis.opsForSet().members(ROOMS_ACTIVE);
        if (roomIds == null || roomIds.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long graceMs = graceSeconds * 1000;
        for (String roomId : roomIds) {
            Map<Object, Object> h = roomHash(roomId);
            if (h.isEmpty() || !"PLAYING".equals(h.get("status"))) {
                redis.opsForSet().remove(ROOMS_ACTIVE, roomId);
                continue;
            }
            long playerA = parseLong(h.get("playerA"));
            long playerB = parseLong(h.get("playerB"));
            int scoreA = Integer.parseInt((String) h.get("scoreA"));
            int scoreB = Integer.parseInt((String) h.get("scoreB"));

            long offA = offlineSince(h, playerA);
            long offB = offlineSince(h, playerB);
            if (offA > 0 && now - offA > graceMs) {
                settle(roomId, "DISCONNECT", playerB, scoreA, scoreB, "FORFEIT");
            } else if (offB > 0 && now - offB > graceMs) {
                settle(roomId, "DISCONNECT", playerA, scoreA, scoreB, "FORFEIT");
            }
        }
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private Map<Object, Object> roomHash(String roomId) {
        return redis.opsForHash().entries(room(roomId));
    }

    private void putRoom(RoomState s) {
        String key = room(s.roomId());
        Map<String, String> h = Map.of(
                "playerA", String.valueOf(s.playerA()),
                "playerB", String.valueOf(s.playerB()),
                "board", boardService.toJson(s.board()),
                "scoreA", String.valueOf(s.scoreA()),
                "scoreB", String.valueOf(s.scoreB()),
                "status", s.status()
        );
        redis.opsForHash().putAll(key, h);
        redis.expire(key, Duration.ofHours(ROOM_TTL_HOURS));
    }

    private RoomState toRoomState(String roomId, Map<Object, Object> h) {
        long playerA = parseLong(h.get("playerA"));
        long playerB = parseLong(h.get("playerB"));
        int[][] board = boardService.fromJson((String) h.get("board"));
        int scoreA = Integer.parseInt((String) h.get("scoreA"));
        int scoreB = Integer.parseInt((String) h.get("scoreB"));
        String status = (String) h.get("status");
        return new RoomState(roomId, playerA, playerB, board, scoreA, scoreB, status);
    }

    private boolean inBounds(int[][] board, int r, int c) {
        return r >= 0 && r < board.length && c >= 0 && c < board[0].length;
    }

    private long parseLong(Object o) {
        return Long.parseLong((String) o);
    }

    private String offlineField(long userId) {
        return "offline:" + userId;
    }

    private long offlineSince(Map<Object, Object> h, long userId) {
        Object v = h.get(offlineField(userId));
        if (v == null) {
            return 0;
        }
        try {
            return Long.parseLong((String) v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}