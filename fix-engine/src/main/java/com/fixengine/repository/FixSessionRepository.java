package com.fixengine.repository;

import com.fixengine.entity.FixSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FixSessionRepository extends JpaRepository<FixSession, String> {

    List<FixSession> findByEnabledTrue();

    List<FixSession> findByMode(String mode);
}
