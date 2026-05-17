package com.enterprisefabric;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String productName;
    private Double amount;
    private String region;
    private LocalDateTime timestamp;

    public Sale(String productName, Double amount, String region) {
        this.productName = productName;
        this.amount = amount;
        this.region = region;
        this.timestamp = LocalDateTime.now();
    }
}
