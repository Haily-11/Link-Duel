package com.woner.linkgame.service;

/**
 * Redis key 统一约定，避免散落字符串。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 登录会话 token -> userId */
    public static String session(String token) {
        return "session:" + token;
    }

    /** 匹配队列（Redis List） */
    public static final String MATCH_QUEUE = "match:queue";

    /** 房间 Hash：board / playerA / playerB / scoreA / scoreB / status ... */
    public static String room(String roomId) {
        return "room:" + roomId;
    }

    /** 用户 -> 房间 反查，用于重连定位未结束对局 */
    public static String userRoom(long userId) {
        return "user:room:" + userId;
    }

    /** 房间结算幂等标记（SETNX） */
    public static String settled(String roomId) {
        return "settled:" + roomId;
    }

    /** 排行榜 ZSET（member=userId，score=积分） */
    public static final String LEADERBOARD = "leaderboard";

    /** 活跃房间集合（用于定时扫描断线判负） */
    public static final String ROOMS_ACTIVE = "rooms:active";

    /** 房间级互斥锁，串行化走子处理，避免并发重复消除 */
    public static String roomLock(String roomId) {
        return "lock:room:" + roomId;
    }
}