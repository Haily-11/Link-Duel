package com.woner.linkgame.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 纯单元测试：不依赖 Spring / MySQL / Redis。
 */
class LinkSearchServiceTest {

    private final LinkSearchService svc = new LinkSearchService();

    @Test
    void straightLine() {
        int[][] board = {{1, 0, 1}};
        assertTrue(svc.canConnect(board, 0, 0, 0, 2));
    }

    @Test
    void oneCorner() {
        int[][] board = {
                {1, 0, 0},
                {0, 0, 0},
                {0, 0, 1}
        };
        assertTrue(svc.canConnect(board, 0, 0, 2, 2));
    }

    @Test
    void twoCornersAroundObstacle() {
        // 3 行 5 列；A(1,1) 与 B(1,3) 之间被 (1,2) 阻挡，需绕到第 0 行（2 次转弯）
        int[][] board = {
                {0, 0, 0, 0, 0},
                {0, 1, 2, 1, 0},
                {0, 0, 0, 0, 0}
        };
        assertTrue(svc.canConnect(board, 1, 1, 1, 3));
    }

    @Test
    void blockedCompletely() {
        // A(0,0) 四周被挡，无法到达 B(2,2)
        int[][] board = {
                {1, 2, 0},
                {2, 2, 2},
                {0, 2, 1}
        };
        assertFalse(svc.canConnect(board, 0, 0, 2, 2));
    }

    @Test
    void differentPattern() {
        int[][] board = {{1, 0, 2}};
        assertFalse(svc.canConnect(board, 0, 0, 0, 2));
    }

    @Test
    void sameCell() {
        int[][] board = {{1, 0, 1}};
        assertFalse(svc.canConnect(board, 0, 0, 0, 0));
    }

    @Test
    void emptyCell() {
        int[][] board = {{0, 0, 1}};
        assertFalse(svc.canConnect(board, 0, 0, 0, 2));
    }

    @Test
    void findPathReturnsCorners() {
        int[][] board = {
                {0, 0, 0, 0, 0},
                {0, 1, 2, 1, 0},
                {0, 0, 0, 0, 0}
        };
        List<int[]> path = svc.findPath(board, 1, 1, 1, 3);
        assertNotNull(path);
        assertEquals(1, path.get(0)[0]);
        assertEquals(1, path.get(0)[1]);
        assertEquals(1, path.get(path.size() - 1)[0]);
        assertEquals(3, path.get(path.size() - 1)[1]);
        // 折点数量：起点、两个转弯点、终点 = 4
        assertEquals(4, path.size());
    }

    @Test
    void findAnyValidPair() {
        int[][] board = {
                {1, 0, 2},
                {0, 0, 0},
                {3, 0, 2}
        };
        int[] pair = svc.findAnyValidPair(board);
        assertNotNull(pair);
        // (0,2) 与 (2,2) 之间为空，可直接相连
        assertEquals(0, pair[0]);
        assertEquals(2, pair[1]);
        assertEquals(2, pair[2]);
        assertEquals(2, pair[3]);
    }
}