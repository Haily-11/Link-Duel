package com.woner.linkgame.repository;

import com.woner.linkgame.entity.GameRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameRecordRepository extends JpaRepository<GameRecord, Long> {

    Optional<GameRecord> findByRoomId(String roomId);

    boolean existsByRoomId(String roomId);

    List<GameRecord> findTop10ByOrderBySettledAtDesc();
}