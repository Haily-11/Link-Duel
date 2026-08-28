package com.woner.linkgame.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 纯单元测试：棋盘生成与洗牌。
 */
class BoardServiceTest {

    private final BoardService svc = new BoardService(new ObjectMapper(), new LinkSearchService(), 8, 8);

    @Test
    void generateBoardIsValid() {
        int[][] board = svc.generateBoard();
        assertEquals(10, board.length);
        assertEquals(10, board[0].length);
        // 边框全空
        for (int c = 0; c < 10; c++) {
            assertEquals(0, board[0][c]);
            assertEquals(0, board[9][c]);
        }
        for (int r = 0; r < 10; r++) {
            assertEquals(0, board[r][0]);
            assertEquals(0, board[r][9]);
        }
        // 内部 64 格全非空
        int count = 0;
        for (int r = 1; r <= 8; r++) {
            for (int c = 1; c <= 8; c++) {
                assertNotEquals(0, board[r][c]);
                count++;
            }
        }
        assertEquals(64, count);
        // 每种图案数量为偶数（成对）
        Map<Integer, Integer> freq = new HashMap<>();
        for (int r = 1; r <= 8; r++) {
            for (int c = 1; c <= 8; c++) {
                freq.merge(board[r][c], 1, Integer::sum);
            }
        }
        for (int v : freq.values()) {
            assertEquals(0, v % 2);
        }
        // 保证至少存在一步可消
        assertNotNull(new LinkSearchService().findAnyValidPair(board));
        assertEquals(32, svc.remainingPairs(board));
    }

    @Test
    void reshufflePreservesTiles() {
        int[][] board = svc.generateBoard();
        int[][] before = deepCopy(board);
        int[][] after = svc.reshuffle(board);
        // 洗牌后：原空位仍为空，非空位仍非空；图案多重集合不变
        Map<Integer, Integer> freqBefore = multiset(before);
        Map<Integer, Integer> freqAfter = multiset(after);
        assertEquals(freqBefore, freqAfter);
        // 洗牌结果仍保证有解
        assertNotNull(new LinkSearchService().findAnyValidPair(after));
    }

    private Map<Integer, Integer> multiset(int[][] board) {
        Map<Integer, Integer> m = new HashMap<>();
        for (int[] row : board) {
            for (int v : row) {
                if (v != 0) {
                    m.merge(v, 1, Integer::sum);
                }
            }
        }
        return m;
    }

    private int[][] deepCopy(int[][] board) {
        int[][] r = new int[board.length][];
        for (int i = 0; i < board.length; i++) {
            r[i] = board[i].clone();
        }
        return r;
    }
}