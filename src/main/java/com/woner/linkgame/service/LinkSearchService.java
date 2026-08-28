package com.woner.linkgame.service;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

/**
 * 连连看核心算法：判断两点是否可通过「不超过 2 次转弯」的路径相连。
 *
 * <p>棋盘约定：{@code board[r][c]} 中 0 表示空位（可通行），非 0 表示图案 ID。
 * 棋盘外围保留一圈 0 的边框，因此允许路径从棋盘外部绕行。</p>
 *
 * <p>算法：0-1 BFS / 最短转弯数。状态为 (r, c, 方向)，以转弯次数为代价做广度优先，
 * 记录到达每个 (r, c, 方向) 的最小转弯次数；到达终点且转弯数 <= 2 即视为可连接。
 * 复杂度 O(rows * cols * 4)。</p>
 */
@Service
public class LinkSearchService {

    private static final int[][] DIRS = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};

    /**
     * 判断 (x1,y1) 与 (x2,y2) 能否消除。
     */
    public boolean canConnect(int[][] board, int x1, int y1, int x2, int y2) {
        if (!isValid(board, x1, y1) || !isValid(board, x2, y2)) {
            return false;
        }
        if (x1 == x2 && y1 == y2) {
            return false;
        }
        if (board[x1][y1] == 0 || board[x1][y1] != board[x2][y2]) {
            return false;
        }

        int rows = board.length;
        int cols = board[0].length;

        int[][][] minTurns = new int[rows][cols][4];
        for (int[][] g : minTurns) {
            for (int[] row : g) {
                Arrays.fill(row, Integer.MAX_VALUE);
            }
        }

        Queue<Node> queue = new ArrayDeque<>();
        for (int d = 0; d < 4; d++) {
            minTurns[x1][y1][d] = 0;
            queue.offer(new Node(x1, y1, d, 0));
        }

        while (!queue.isEmpty()) {
            Node curr = queue.poll();
            if (curr.turns > 2) {
                continue;
            }
            if (curr.r == x2 && curr.c == y2) {
                return true;
            }
            for (int d = 0; d < 4; d++) {
                int nr = curr.r + DIRS[d][0];
                int nc = curr.c + DIRS[d][1];
                int nextTurns = (curr.dir == d) ? curr.turns : curr.turns + 1;
                if (nextTurns > 2) {
                    continue;
                }
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) {
                    continue;
                }
                // 途经点必须是空位，或者是终点本身
                if (board[nr][nc] == 0 || (nr == x2 && nc == y2)) {
                    if (nextTurns < minTurns[nr][nc][d]) {
                        minTurns[nr][nc][d] = nextTurns;
                        queue.offer(new Node(nr, nc, d, nextTurns));
                    }
                }
            }
        }
        return false;
    }

    /**
     * 返回一条可连接的路径，仅包含「折点」（起点、每个转弯点、终点），
     * 用于前端绘制连线动画。无法连接返回 null。
     */
    public List<int[]> findPath(int[][] board, int x1, int y1, int x2, int y2) {
        if (!canConnect(board, x1, y1, x2, y2)) {
            return null;
        }
        int rows = board.length;
        int cols = board[0].length;

        // parent[r][c][dir] 记录前驱节点（含方向），便于回溯
        Node[][][] parent = new Node[rows][cols][4];
        int[][][] minTurns = new int[rows][cols][4];
        for (int[][] g : minTurns) {
            for (int[] row : g) {
                Arrays.fill(row, Integer.MAX_VALUE);
            }
        }

        Queue<Node> queue = new ArrayDeque<>();
        Node end = null;
        for (int d = 0; d < 4; d++) {
            Node start = new Node(x1, y1, d, 0);
            minTurns[x1][y1][d] = 0;
            queue.offer(start);
        }

        while (!queue.isEmpty()) {
            Node curr = queue.poll();
            if (curr.turns > 2) {
                continue;
            }
            if (curr.r == x2 && curr.c == y2) {
                end = curr;
                break;
            }
            for (int d = 0; d < 4; d++) {
                int nr = curr.r + DIRS[d][0];
                int nc = curr.c + DIRS[d][1];
                int nextTurns = (curr.dir == d) ? curr.turns : curr.turns + 1;
                if (nextTurns > 2) {
                    continue;
                }
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) {
                    continue;
                }
                if (board[nr][nc] == 0 || (nr == x2 && nc == y2)) {
                    if (nextTurns < minTurns[nr][nc][d]) {
                        minTurns[nr][nc][d] = nextTurns;
                        Node next = new Node(nr, nc, d, nextTurns);
                        parent[nr][nc][d] = curr;
                        queue.offer(next);
                    }
                }
            }
        }

        if (end == null) {
            return null;
        }

        // 回溯得到完整路径，再压缩为折点
        List<int[]> full = new ArrayList<>();
        Node cur = end;
        while (cur != null) {
            full.add(new int[]{cur.r, cur.c});
            cur = parent[cur.r][cur.c][cur.dir];
        }
        // full 是终点到起点，反转
        java.util.Collections.reverse(full);
        return compressToCorners(full);
    }

    /** 把逐格路径压缩为仅含方向变化的折点。 */
    private List<int[]> compressToCorners(List<int[]> full) {
        List<int[]> corners = new ArrayList<>();
        for (int i = 0; i < full.size(); i++) {
            int[] p = full.get(i);
            if (i == 0 || i == full.size() - 1) {
                corners.add(p);
                continue;
            }
            int[] prev = full.get(i - 1);
            int[] next = full.get(i + 1);
            boolean straight = (prev[0] == p[0] && p[0] == next[0])
                    || (prev[1] == p[1] && p[1] == next[1]);
            if (!straight) {
                corners.add(p);
            }
        }
        return corners;
    }

    /**
     * 扫描棋盘上是否还存在任意一对可消除的图案，返回其坐标；无则返回 null。
     * 用于死局检测（此时应洗牌）。
     */
    public int[] findAnyValidPair(int[][] board) {
        int rows = board.length;
        int cols = board[0].length;
        for (int r1 = 0; r1 < rows; r1++) {
            for (int c1 = 0; c1 < cols; c1++) {
                int v = board[r1][c1];
                if (v == 0) {
                    continue;
                }
                for (int r2 = 0; r2 < rows; r2++) {
                    for (int c2 = 0; c2 < cols; c2++) {
                        if (r1 == r2 && c1 == c2) {
                            continue;
                        }
                        if (board[r2][c2] != v) {
                            continue;
                        }
                        if (canConnect(board, r1, c1, r2, c2)) {
                            return new int[]{r1, c1, r2, c2};
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isValid(int[][] board, int r, int c) {
        return r >= 0 && r < board.length && c >= 0 && c < board[0].length;
    }

    private static class Node {
        int r, c, dir, turns;

        Node(int r, int c, int dir, int turns) {
            this.r = r;
            this.c = c;
            this.dir = dir;
            this.turns = turns;
        }
    }
}