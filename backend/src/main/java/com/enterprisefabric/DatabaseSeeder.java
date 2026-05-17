package com.enterprisefabric;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final SemanticStateRepository stateRepository;
    private final SaleRepository saleRepository;
    private final ResiliencyLogRepository logRepository;

    public DatabaseSeeder(SemanticStateRepository stateRepository, 
                          SaleRepository saleRepository, 
                          ResiliencyLogRepository logRepository) {
        this.stateRepository = stateRepository;
        this.saleRepository = saleRepository;
        this.logRepository = logRepository;
    }

    @Override
    public void run(String... args) {
        // Force update policy to ensure SENTINEL-ALPHA-2026 is visible
        stateRepository.save(new SemanticState("enterprise_knowledge", 
            "{\"policy_id\": \"SENTINEL-ALPHA-2026\", \"security_standard\": \"AES-512-RSA-8192\", \"compliance\": \"SOC-3-READY\"}"));
        System.out.println(">>> Enterprise Knowledge Base Updated: SENTINEL-ALPHA-2026");

        if (stateRepository.count() <= 1) {
            stateRepository.save(new SemanticState("stock_status", 
                "{\"status\": \"In Stock\", \"count\": 42, \"warehouse\": \"North-Point-7\"}"));
            System.out.println(">>> Semantic State Seeded.");
        }

        if (saleRepository.count() == 0) {
            saleRepository.save(new Sale("Sentinel Node Pro", 1200.0, "North America"));
            saleRepository.save(new Sale("Fabric Guard XL", 2500.0, "Europe"));
            saleRepository.save(new Sale("AI Core Sub", 450.0, "Asia"));
            saleRepository.save(new Sale("Mesh Router", 800.0, "North America"));
            saleRepository.save(new Sale("Sentinel Node Pro", 1200.0, "Europe"));
            System.out.println(">>> Mock Sales Seeded.");
        }

        if (logRepository.count() == 0) {
            logRepository.save(new ResiliencyLog("Llama-3.1", "SUCCESS", "Primary", 120, "None"));
            logRepository.save(new ResiliencyLog("Gemma-7b", "FAILOVER", "Secondary", 450, "Timeout on Primary"));
            System.out.println(">>> Initial Resiliency Logs Seeded.");
        }
    }
}
