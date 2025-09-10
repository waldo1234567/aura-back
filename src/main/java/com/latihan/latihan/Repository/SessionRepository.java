package com.latihan.latihan.Repository;

import com.latihan.latihan.Entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionRepository extends JpaRepository<SessionEntity, Long> {

}
