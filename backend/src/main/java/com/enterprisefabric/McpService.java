package com.enterprisefabric;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.*;

/**
 * Pure Java Implementation of Model Context Protocol (MCP) Client
 * Focuses on Resiliency as per TrueFoundry Hackathon Challenge
 */
@Service
public class McpService {

    private final RestTemplate restTemplate;
    private final SemanticStateRepository stateRepository;

    public McpService(SemanticStateRepository stateRepository) {
        this.stateRepository = stateRepository;
        
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // Tools should be fast (5s)
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    public String executeMcpTool(String toolName, String arguments) {
        try {
            // Simplified for hackathon: Tool is local but follows MCP logic
            if (toolName.equals("get_enterprise_data")) {
                return stateRepository.findById("enterprise_knowledge")
                        .map(SemanticState::getStateValue)
                        .orElseThrow(() -> new RuntimeException("Knowledge base not found in DB"));
            }
            
            throw new RuntimeException("MCP_SERVER_ERROR: Tool not found on remote server");

        } catch (Exception e) {
            System.err.println("MCP Infrastructure Error: " + e.getMessage());
            throw new RuntimeException("MCP_INFRASTRUCTURE_DOWN");
        }
    }

    public List<Map<String, Object>> getRemoteTools() {
        // We no longer auto-create the row here to allow for manual chaos testing.
        // If the row is deleted from DB, the tool will still be listed but execution will fail.
        
        List<Map<String, Object>> remoteTools = new ArrayList<>();
        
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", "get_enterprise_data");
        tool.put("description", "Fetch sensitive enterprise policy and security data from local database.");
        
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        tool.put("inputSchema", params);
        
        remoteTools.add(tool);
        return remoteTools;
    }
}
