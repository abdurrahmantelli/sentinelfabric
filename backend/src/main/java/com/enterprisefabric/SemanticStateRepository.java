package com.enterprisefabric;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SemanticStateRepository extends JpaRepository<SemanticState, String> {
}
