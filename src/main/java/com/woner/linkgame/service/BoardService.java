package com.woner.linkgame.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 棋盘生成与 (反) 序列化。棋盘为 (size+2) x (size+2) 的二维数组，
 * 外围一圈恒为 0（空），内部 size x size 区域填满成对图案。
 */
@Service
public class BoardService {

    private final ObjectMapper objectMapper;
    private final LinkSearchService linkSearchService;

    private final int size;
    private final int emojiCount;

    public BoardService(ObjectMapper objectMapper,
                        LinkSearchService linkSearchService,
                        @Value("${linkgame.board-size:8}") int size,
                        @Value("${linkgame.emoji-count:8}") int emojiCount) {
        this.objectMapper = objectMapper;
        this.linkSearchService = linkSearchService;
        this.size = size;
        this.emojiCount = emojiCount;
    }

    /** 生成一份「至少存在一步可消」的初始棋盘，避免开局死局。 */
    public int[][] generateBoard() {
        int[][] board;
        int attempts = 0;
        do {
            board = randomBoard();
            attempts++;
        } while (linkSearchService.findAnyValidPair(board) == null && attempts < 50);
        return board;
    }

    private int[][] randomBoard() {
        int dim = size + 2;
        int[][] grid = new int[dim][dim]; // 边框默认 0
        List<Integer> cells = new ArrayList<>();
        int copiesPerType = (size * size) / emojiCount;
        for (int t = 1; t <= emojiCount; t++) {
            for (int k = 0; k < copiesPerType; k++) {
                cells.add(t);
            }
        }
        Collections.shuffle(cells);
        int idx = 0;
        for (int r = 1; r <= size; r++) {
            for (int c = 1; c <= size; c++) {
                grid[r][c] = cells.get(idx++);
            }
        }
        return grid;
    }

    /**
     * 死局洗牌：仅重排剩余的非空图案（数量天然成对），保证洗牌后仍有解。
     */
    public int[][] reshuffle(int[][] board) {
        List<Integer> remaining = new ArrayList<>();
        for (int r = 1; r <= size; r++) {
            for (int c = 1; c <= size; c++) {
                if (board[r][c] != 0) {
                    remaining.add(board[r][c]);
                }
            }
        }
        int[][] result = new int[board.length][];
        for (int i = 0; i < board.length; i++) {
            result[i] = board[i].clone();
        }
        Collections.shuffle(remaining);
        int idx = 0;
        for (int r = 1; r <= size; r++) {
            for (int c = 1; c <= size; c++) {
                if (result[r][c] != 0) {
                    result[r][c] = remaining.get(idx++);
                }
            }
        }
        // 洗牌后若仍死局（极小概率），递归再洗一次
        if (linkSearchService.findAnyValidPair(result) == null) {
            return reshuffle(result);
        }
        return result;
    }

    public String toJson(int[][] board) {
        try {
            return objectMapper.writeValueAsString(board);
        } catch (Exception e) {
            throw new IllegalStateException("棋盘序列化失败", e);
        }
    }

    public int[][] fromJson(String json) {
        try {
            return objectMapper.readValue(json, int[][].class);
        } catch (Exception e) {
            throw new IllegalStateException("棋盘反序列化失败", e);
        }
    }

    /** 统计棋盘剩余图案对数。 */
    public int remainingPairs(int[][] board) {
        int count = 0;
        for (int[] row : board) {
            for (int v : row) {
                if (v != 0) {
                    count++;
                }
            }
        }
        return count / 2;
    }
}