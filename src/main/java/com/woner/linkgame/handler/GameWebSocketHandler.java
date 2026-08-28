package com.woner.linkgame.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.woner.linkgame.dto.MoveOutcome;
import com.woner.linkgame.dto.RoomState;
import com.woner.linkgame.dto.SettleOutcome;
import com.woner.linkgame.service.AuthService;
import com.woner.linkgame.service.GameService;
import com.woner.linkgame.service.MatchService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 入口：负责登录态鉴权、匹配、走子、认输、断线/重连的实时消息分发。
 *
 * <p>协议：连接时通过 query 参数 token 鉴权（token 由 REST 登录返回）。
 * 客户端上行 type：MATCH_START / TRY_ELIMINATE / SURRENDER / PING / CANCEL_MATCH。</p>
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthService authService;
    private final MatchService matchService;
    private final GameService gameService;

    // userId -> 会话。用于向对手推送实时消息（进程内映射，仅作推送通道，权威状态在 Redis）
    private final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    public GameWebSocketHandler(AuthService authService,
                                MatchService matchService,
                                GameService gameService) {
        this.authService = authService;
        this.matchService = matchService;
        this.gameService = gameService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = queryParam(session, "token");
        Long userId = authService.resolveUserId(token);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("未登录或会话失效"));
            return;
        }
        session.getAttributes().put("userId", userId);
        userSessions.put(userId, session);
        authService.touch(token);

        // 重连：若存在未结束对局，恢复完整状态，并通知对手
        RoomState restored = gameService.reconnect(userId);
        if (restored != null) {
            sendTo(userId, roomMessage("RECONNECT_STATE", restored, userId));
            long opponent = opponentOf(restored, userId);
            sendTo(opponent, Map.of("type", "OPPONENT_RECONNECTED"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            return;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            sendTo(userId, Map.of("type", "ERROR", "message", "非法消息格式"));
            return;
        }
        String type = node.path("type").asText();
        switch (type) {
            case "MATCH_START" -> handleMatchStart(userId);
            case "CANCEL_MATCH" -> matchService.cancelMatch(userId);
            case "TRY_ELIMINATE" -> handleEliminate(userId, node);
            case "SURRENDER" -> handleSurrender(userId);
            case "PING" -> sendTo(userId, Map.of("type", "PONG"));
            default -> sendTo(userId, Map.of("type", "ERROR", "message", "未知消息类型: " + type));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            return;
        }
        // 先取到进行中的房间，再标记离线，以便通知对手
        Optional<RoomState> room = gameService.findActiveRoom(userId);
        userSessions.remove(userId);
        gameService.markDisconnected(userId);
        room.ifPresent(r -> sendTo(opponentOf(r, userId), Map.of("type", "OPPONENT_DISCONNECTED")));
    }

    // ------------------------------------------------------------------
    // 业务分发
    // ------------------------------------------------------------------

    private void handleMatchStart(long userId) throws IOException {
        Optional<RoomState> room = matchService.joinMatch(userId);
        if (room.isEmpty()) {
            sendTo(userId, Map.of("type", "MATCH_WAITING"));
            return;
        }
        RoomState r = room.get();
        sendTo(r.playerA(), roomMessage("MATCH_SUCCESS", r, r.playerA()));
        sendTo(r.playerB(), roomMessage("MATCH_SUCCESS", r, r.playerB()));
    }

    private void handleEliminate(long userId, JsonNode node) throws IOException {
        String roomId = node.path("roomId").asText();
        JsonNode p1 = node.path("p1");
        JsonNode p2 = node.path("p2");
        if (roomId.isEmpty() || !p1.isArray() || !p2.isArray() || p1.size() < 2 || p2.size() < 2) {
            sendTo(userId, Map.of("type", "ELIMINATE_REJECT", "reason", "参数不完整"));
            return;
        }
        MoveOutcome outcome = gameService.tryEliminate(userId, roomId,
                p1.get(0).asInt(), p1.get(1).asInt(), p2.get(0).asInt(), p2.get(1).asInt());

        if (!outcome.valid()) {
            sendTo(userId, Map.of("type", "ELIMINATE_REJECT", "reason", outcome.reason()));
            return;
        }

        RoomState r = outcome.roomState();
        Map<String, Object> sync = new HashMap<>(roomMessage("ELIMINATE_SYNC", r, userId));
        sync.put("operatorId", outcome.eliminatedBy());
        sync.put("p1", outcome.p1());
        sync.put("p2", outcome.p2());
        sync.put("path", outcome.path());
        sync.put("remainingPairs", r == null ? 0 : remainingPairs(r));

        sendTo(r.playerA(), sync);
        sendTo(r.playerB(), sync);

        if (outcome.gameOver()) {
            broadcastGameOver(r, outcome.winnerId(), outcome.endReason());
        }
    }

    private void handleSurrender(long userId) throws IOException {
        SettleOutcome outcome = gameService.surrender(userId);
        if (outcome == null) {
            return;
        }
        // 认输者视角：输；胜者（对手）视角：赢
        sendTo(userId, gameOverMessage(outcome.winnerId(), outcome.scoreA(), outcome.scoreB(), outcome.endReason(), userId));
        if (outcome.winnerId() != null) {
            sendTo(outcome.winnerId(), gameOverMessage(outcome.winnerId(),
                    outcome.scoreA(), outcome.scoreB(), outcome.endReason(), outcome.winnerId()));
        }
    }

    private void broadcastGameOver(RoomState r, Long winnerId, String endReason) throws IOException {
        sendTo(r.playerA(), gameOverMessage(winnerId, r.scoreA(), r.scoreB(), endReason, r.playerA()));
        sendTo(r.playerB(), gameOverMessage(winnerId, r.scoreA(), r.scoreB(), endReason, r.playerB()));
    }

    // ------------------------------------------------------------------
    // 消息构建
    // ------------------------------------------------------------------

    private Map<String, Object> roomMessage(String type, RoomState s, long toUserId) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("roomId", s.roomId());
        m.put("playerA", s.playerA());
        m.put("playerB", s.playerB());
        m.put("youAre", toUserId == s.playerA() ? "A" : "B");
        m.put("grid", s.board());
        m.put("scoreA", s.scoreA());
        m.put("scoreB", s.scoreB());
        return m;
    }

    private Map<String, Object> gameOverMessage(Long winnerId, int scoreA, int scoreB, String endReason, long toUserId) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "GAME_OVER");
        m.put("winnerId", winnerId);
        m.put("scoreA", scoreA);
        m.put("scoreB", scoreB);
        m.put("endReason", endReason);
        m.put("youWon", winnerId != null && winnerId == toUserId);
        return m;
    }

    private int remainingPairs(RoomState s) {
        int count = 0;
        for (int[] row : s.board()) {
            for (int v : row) {
                if (v != 0) {
                    count++;
                }
            }
        }
        return count / 2;
    }

    private long opponentOf(RoomState s, long userId) {
        return s.playerA() == userId ? s.playerB() : s.playerA();
    }

    // ------------------------------------------------------------------
    // 发送工具
    // ------------------------------------------------------------------

    private void sendTo(long userId, Map<String, Object> msg) {
        WebSocketSession session = userSessions.get(userId);
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
        } catch (IOException e) {
            // 发送失败（连接已断）可忽略，断线状态由 afterConnectionClosed 处理
        }
    }

    private String queryParam(WebSocketSession session, String name) {
        if (session.getUri() == null || session.getUri().getQuery() == null) {
            return null;
        }
        for (String pair : session.getUri().getQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && name.equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}