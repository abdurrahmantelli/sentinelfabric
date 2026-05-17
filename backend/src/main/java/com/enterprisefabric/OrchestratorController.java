package com.enterprisefabric;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class OrchestratorController {

    private final ModelService modelService;

    public OrchestratorController(ModelService modelService) {
        this.modelService = modelService;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, String> payload) {
        String query = payload.get("query");
        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }
        try {
            return modelService.processQuery(query);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @GetMapping("/health")
    public Map<String, Object> getHealth() {
        // Just return a snapshot of the current system state
        return modelService.processQuery("status_check");
    }
}
