package com.enterprisefabric;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "resiliency_logs")
public class ResiliencyLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String level; // INFO, WARNING, CRITICAL
    private String modelName;
    private String action; // FALLBACK, TOOL_EXECUTION, SHIELD_ACTIVE
    
    @Column(length = 1000)
    private String message;
    
    private Integer latency;
    
    private LocalDateTime timestamp;

    public ResiliencyLog() {
        this.timestamp = LocalDateTime.now();
    }

    public ResiliencyLog(String modelName, String action, String level, Integer latency, String message) {
        this();
        this.modelName = modelName;
        this.action = action;
        this.level = level;
        this.latency = latency;
        this.message = message;
    }
}
