package com.enterprisefabric;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
public class MonitoringController {

    private final ModelService modelService;

    public MonitoringController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping("/health")
    public Map<String, String> getHealth() {
        return modelService.getHealth();
    }

    @GetMapping("/logs")
    public List<ResiliencyLog> getLogs() {
        return modelService.getLogs();
    }

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard() {
        return modelService.getDashboardSummary();
    }
}
