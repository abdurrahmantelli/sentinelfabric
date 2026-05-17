package com.enterprisefabric;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ResiliencyLogRepository extends JpaRepository<ResiliencyLog, Long> {
    List<ResiliencyLog> findTop50ByOrderByTimestampDesc();
}
