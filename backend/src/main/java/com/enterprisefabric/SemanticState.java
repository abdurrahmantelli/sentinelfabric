package com.enterprisefabric;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "semantic_states")
public class SemanticState {
    @Id
    private String stateKey; // e.g., stock_status, last_order

    @Column(length = 2000)
    private String stateValue;
    
    private LocalDateTime lastUpdated;

    public SemanticState() {
        this.lastUpdated = LocalDateTime.now();
    }

    public SemanticState(String stateKey, String stateValue) {
        this();
        this.stateKey = stateKey;
        this.stateValue = stateValue;
    }
}
