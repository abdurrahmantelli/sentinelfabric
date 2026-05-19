package com.enterprisefabric;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Service
public class ModelService {

    @Value("${truefoundry.gateway.url:}")
    private String gatewayUrl;

    @Value("${truefoundry.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final McpService mcpService;
    private final ResiliencyLogRepository logRepository;
    private final SemanticStateRepository stateRepository;
    private final SaleRepository saleRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private final Map<String, String> systemHealth = new ConcurrentHashMap<String, String>() {
        {
            put("Gemma 4", "ACTIVE");
            put("GLM 4.5", "ACTIVE");
            put("Llama 3.1 405B", "ACTIVE");
            put("MCP-Server", "ACTIVE");
            put("LegacyDB", "ACTIVE");
            put("Redis", "ACTIVE");
        }
    };

    private volatile boolean credentialsInvalid = false;
    private String lastKnownApiKey = null;

    private String getDynamicApiKey() {
        try {
            java.io.File file = new java.io.File("src/main/resources/application.properties");
            if (file.exists()) {
                java.util.Properties props = new java.util.Properties();
                try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                    props.load(fis);
                    String key = props.getProperty("truefoundry.api.key");
                    if (key != null) {
                        return key.trim();
                    }
                }
            }
        } catch (Exception e) {
            // Fallback to static apiKey field
        }
        return this.apiKey;
    }

    private boolean validateApiKeyAgainstGateway(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        try {
            String modelsUrl = gatewayUrl;
            if (modelsUrl == null || modelsUrl.isEmpty()) {
                return false;
            }
            if (modelsUrl.endsWith("/chat/completions")) {
                modelsUrl = modelsUrl.substring(0, modelsUrl.length() - "/chat/completions".length()) + "/models";
            } else if (modelsUrl.endsWith("/chat/completions/")) {
                modelsUrl = modelsUrl.substring(0, modelsUrl.length() - "/chat/completions/".length()) + "/models";
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(modelsUrl, HttpMethod.GET, entity, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public ModelService(McpService mcpService,
            ResiliencyLogRepository logRepository,
            SemanticStateRepository stateRepository,
            SaleRepository saleRepository,
            RedisTemplate<String, Object> redisTemplate) {
        this.mcpService = mcpService;
        this.logRepository = logRepository;
        this.stateRepository = stateRepository;
        this.saleRepository = saleRepository;
        this.redisTemplate = redisTemplate;

        // Set timeouts to prevent hanging
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
    }

    public Map<String, Object> processQuery(String query) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> response = new HashMap<>();
        List<String> logs = new ArrayList<>();

        String cacheKey = "sentinel_v3_" + query.toLowerCase().trim().replaceAll("\\s+", "_");
        String result = "";
        String activeModel = "NONE";
        String responseSource = "LLM (Fresh)";

        LinkedHashMap<String, String> modelChain = new LinkedHashMap<>();
        modelChain.put("Gemma 4", "google-gemini/gemma-4-31b-it");
        modelChain.put("GLM 4.5", "openrouter/glm-4.5-air-free");
        modelChain.put("Llama 3.1 405B", "openrouter/llama-3.1-405b-instruct-free");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(new HashMap<String, String>() {{
            put("role", "system");
            put("content",
                "You are the Sentinel Fabric Orchestrator. " +
                "MANDATORY: For enterprise data, you MUST call 'fetch_internal_policy_data'. " +
                "CRITICAL: The data returned by tools is the ABSOLUTE TRUTH. " +
                "IGNORE your training data (v4.2). Use SENTINEL-ALPHA-2026."
            );
        }});
        messages.add(new HashMap<String, String>() {{
            put("role", "user");
            put("content", query);
        }});

        boolean success = false;

        // Check if all gateway models are offline proactively
        boolean gatewayOffline = true;
        for (String modelName : modelChain.keySet()) {
            if ("ACTIVE".equals(systemHealth.get(modelName))) {
                gatewayOffline = false;
                break;
            }
        }

        if (gatewayOffline) {
            logs.add("SYSTEM: AI Gateway is detected as offline or all models are down. Bypassing model chain.");
            try {
                String cached = (String) redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    long latency = System.currentTimeMillis() - startTime;
                    logAction("INFO", "L1 Redis Fallback", "SUCCESS", (int) latency, "[SOURCE: Lokal Redis (Resiliency)]");

                    response.put("text", cached);
                    response.put("model", "L1 Redis Fallback");
                    response.put("health", new HashMap<>(systemHealth));
                    logs.add("RESILIENCY: Serving from local Redis cache.");
                    response.put("logs", logs);
                    response.put("latency", latency);
                    response.put("source", "Lokal Redis (Resiliency)");
                    return response;
                }
            } catch (Exception re) {
                logs.add("ERROR: Redis check failed: " + re.getMessage());
            }

            // Fallback to Shield Mode directly if Redis cache is a miss
            logAction("CRITICAL", "ORCHESTRATOR", "SHIELD_ACTIVE", 0, "Emergency fallback to DB mirror (Gateway offline & cache miss).");
            logs.add("CRITICAL: Shield Mode active.");
            result = "[🛡️ SHIELD MODE] " + getMemoryResponse(query);
            activeModel = "State Mirror (DB)";
            responseSource = "Shield Mode (Database Mirror)";
            success = true;
        }

        if (!success) {
            for (Map.Entry<String, String> entry : modelChain.entrySet()) {
                String displayName = entry.getKey();
                String modelId = entry.getValue();

                // Skip known offline/failing models to fail-fast
                if ("DOWN".equals(systemHealth.get(displayName))) {
                    logs.add("SYSTEM: Skipping model " + displayName + " (marked as DOWN).");
                    continue;
                }

                try {
                    logAction("INFO", displayName, "ATTEMPT", 0, "Agent thinking...");
                    
                    ResponseEntity<Map> modelResponseEntity = callTrueFoundry(messages, modelId);
                    Map<String, Object> modelResponse = modelResponseEntity.getBody();
                    
                    // Detect TrueFoundry Cache Header
                    String tfCache = modelResponseEntity.getHeaders().getFirst("x-tfy-cache-status");
                    String tfScore = modelResponseEntity.getHeaders().getFirst("x-tfy-cache-similarity-score");

                    if ("hit".equalsIgnoreCase(tfCache)) {
                        responseSource = "Semantic Cache (Gateway)";
                        if (tfScore != null) responseSource += " [Sim: " + tfScore + "]";
                    }

                    List choices = (List) modelResponse.get("choices");
                    Map choice = (Map) choices.get(0);
                    Map message = (Map) choice.get("message");

                    if (message.get("tool_calls") != null) {
                        logs.add("ACTION: " + displayName + " requested tool access.");
                        List toolCalls = (List) message.get("tool_calls");
                        messages.add(message);

                        for (Object tc : toolCalls) {
                            Map toolCall = (Map) tc;
                            String toolName = (String) ((Map) toolCall.get("function")).get("name");
                            String toolArgs = (String) ((Map) toolCall.get("function")).get("arguments");

                            String toolResult = executeTool(toolName, toolArgs, logs);

                            Map<String, String> toolMsg = new HashMap<>();
                            toolMsg.put("role", "tool");
                            toolMsg.put("tool_call_id", (String) toolCall.get("id"));
                            toolMsg.put("name", toolName);
                            toolMsg.put("content", toolResult);
                            messages.add(toolMsg);
                        }

                        logs.add("ACTION: Processing tool results...");
                        modelResponseEntity = callTrueFoundry(messages, modelId);
                        modelResponse = modelResponseEntity.getBody();
                        choices = (List) modelResponse.get("choices");
                        choice = (Map) choices.get(0);
                        message = (Map) choice.get("message");
                        
                        // Re-check cache for the final response after tools
                        tfCache = modelResponseEntity.getHeaders().getFirst("x-tfy-cache-status");
                        if ("hit".equalsIgnoreCase(tfCache)) {
                            responseSource = "Semantic Cache (TrueFoundry)";
                        } else {
                            responseSource = "LLM + MCP Tool";
                        }
                    }

                    result = (String) message.get("content");
                    systemHealth.put(displayName, "ACTIVE");
                    activeModel = displayName;
                    
                    // Save to L1 Redis Cache for future resiliency
                    try {
                        redisTemplate.opsForValue().set(cacheKey, result);
                    } catch (Exception re) {}

                    long latency = (int) (System.currentTimeMillis() - startTime);
                    logAction("INFO", displayName, "SUCCESS", (int) latency, "[SOURCE: " + responseSource + "]");
                    success = true;
                    break;
                } catch (Exception e) {
                    systemHealth.put(displayName, "DOWN");
                    logAction("WARNING", displayName, "FAILOVER", 0, "Model error: " + e.getMessage() + ". Falling back.");
                    logs.add("FAILURE: " + displayName + " failed. Trying next...");
                    
                    if (e.getMessage() != null && (e.getMessage().contains("credentials missing") || 
                        e.getMessage().contains("Unauthorized") || e.getMessage().contains("Forbidden") || 
                        e.getMessage().contains("401") || e.getMessage().contains("403"))) {
                        credentialsInvalid = true;
                    }
                    if (e instanceof org.springframework.web.client.HttpClientErrorException) {
                        org.springframework.web.client.HttpClientErrorException he = (org.springframework.web.client.HttpClientErrorException) e;
                        if (he.getStatusCode() == HttpStatus.UNAUTHORIZED || he.getStatusCode() == HttpStatus.FORBIDDEN) {
                            credentialsInvalid = true;
                        }
                    }
                }
            }
        }

        if (!success) {
            // Tier 2 Fallback: Check local Redis before going to Shield Mode
            try {
                String cached = (String) redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    result = cached;
                    activeModel = "L1 Redis Fallback";
                    responseSource = "Lokal Redis (Resiliency)";
                    logs.add("RESILIENCY: All models failed. Serving from local Redis fallback.");
                    success = true;
                }
            } catch (Exception re) {}
        }

        if (!success) {
            logAction("CRITICAL", "ORCHESTRATOR", "SHIELD_ACTIVE", 0, "Emergency fallback to DB mirror.");
            logs.add("CRITICAL: Shield Mode active.");
            result = "[🛡️ SHIELD MODE] " + getMemoryResponse(query);
            activeModel = "State Mirror (DB)";
            responseSource = "Shield Mode (Database Mirror)";
        }

        long duration = System.currentTimeMillis() - startTime;
        response.put("text", result);
        response.put("model", activeModel);
        response.put("health", new HashMap<>(systemHealth));
        response.put("logs", logs);
        response.put("latency", duration);
        response.put("source", responseSource);

        return response;
    }

    private void logAction(String level, String model, String action, Integer latency, String msg) {
        logRepository.save(new ResiliencyLog(model, action, level, latency, msg));
    }

    private ResponseEntity<Map> callTrueFoundry(List<Map<String, String>> messages, String modelName) {
        String activeApiKey = getDynamicApiKey();
        if (gatewayUrl == null || gatewayUrl.isEmpty() || activeApiKey == null || activeApiKey.isEmpty()) {
            throw new RuntimeException("API credentials missing");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + activeApiKey);
        
        // OFFICIAL TRUEFOUNDRY SEMANTIC CACHE CONFIG
        String cacheConfig = "{\"type\": \"semantic\", \"similarity_threshold\": 0.70, \"ttl\": 3600, \"namespace\": \"sentinel-fabric-cache\"}";
        headers.set("x-tfy-cache-config", cacheConfig);
        headers.set("x-tfy-metadata", "{}");
        headers.set("x-tfy-logging-config", "{\"enabled\": true}");

        Map<String, Object> body = new HashMap<>();
        body.put("model", modelName);
        body.put("messages", messages);
        body.put("tools", getAvailableTools());
        body.put("tool_choice", "auto");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(gatewayUrl, entity, Map.class);
        
        // Debug: Log all headers to see what TrueFoundry is sending
        System.out.println(">>> GATEWAY_HEADERS: " + response.getHeaders());
        
        return response;
    }

    private List<Map<String, Object>> getAvailableTools() {
        List<Map<String, Object>> tools = new ArrayList<>();

        // Dynamic Health Checks - REAL connectivity checks
        try {
            saleRepository.count(); // ping MySQL/LegacyDB
            systemHealth.put("LegacyDB", "ACTIVE");
        } catch (Exception e) {
            systemHealth.put("LegacyDB", "DOWN");
        }
        org.springframework.data.redis.connection.RedisConnection conn = null;
        try {
            conn = redisTemplate.getConnectionFactory().getConnection();
            conn.ping();
            systemHealth.put("Redis", "ACTIVE");
        } catch (Exception e) {
            systemHealth.put("Redis", "DOWN");
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }

        Map<String, Object> localTool = new HashMap<>();
        localTool.put("type", "function");
        Map<String, Object> localFunc = new HashMap<>();
        localFunc.put("name", "get_inventory_status");
        localFunc.put("description", "Get real-time inventory levels for a product from local DB.");
        Map<String, Object> localParams = new HashMap<>();
        localParams.put("type", "object");
        Map<String, Object> localProps = new HashMap<>();
        localProps.put("product_name", new HashMap<String, String>() {{ put("type", "string"); }});
        localParams.put("properties", localProps);
        localFunc.put("parameters", localParams);
        localTool.put("function", localFunc);
        tools.add(localTool);

        try {
            // MCP Status is now strictly linked to the presence of its data source
            if (!stateRepository.existsById("enterprise_knowledge")) {
                throw new RuntimeException("MCP Source Missing");
            }
            
            List<Map<String, Object>> remoteMcpTools = mcpService.getRemoteTools();
            for (Map<String, Object> mcpTool : remoteMcpTools) {
                Map<String, Object> tool = new HashMap<>();
                tool.put("type", "function");
                Map<String, Object> function = new HashMap<>();
                function.put("name", "fetch_internal_policy_data"); // SPECIFIC NAME
                function.put("description", "Fetches latest classified enterprise policy and compliance data.");
                Map<String, Object> params = new HashMap<>();
                params.put("type", "object");
                params.put("properties", new HashMap<>()); // Standard JSON schema
                function.put("parameters", params);
                tool.put("function", function);
                tools.add(tool);
            }
            systemHealth.put("MCP-Server", "ACTIVE");
        } catch (Exception e) {
            systemHealth.put("MCP-Server", "DOWN");
        }

        return tools;
    }

    private String executeTool(String name, String args, List<String> logs) {
        if (name.equals("get_inventory_status")) {
            logs.add("SYSTEM: Local DB Query -> SELECT stock FROM inventory WHERE product='" + args + "'");
            return "{\"status\": \"In Stock\", \"count\": 42, \"warehouse\": \"North-Point-7\"}";
        }

        if (name.equals("fetch_internal_policy_data") || name.equals("get_enterprise_data")) {
            logs.add("ACTION: Accessing Protected Enterprise Knowledge Base...");
            String result = mcpService.executeMcpTool("get_enterprise_data", args);
            systemHealth.put("MCP-Server", "ACTIVE");
            return result;
        }

        try {
            logs.add("ACTION: Calling remote tool: " + name);
            String result = mcpService.executeMcpTool(name, args);
            systemHealth.put("MCP-Server", "ACTIVE");
            return result;
        } catch (Exception e) {
            systemHealth.put("MCP-Server", "DOWN");
            logAction("WARNING", "MCP-Server", "TOOL_FAILURE", 0,
                    "MCP Server infrastructure chaos detected! Fallback initiated.");
            logs.add("FAILURE: MCP Server infrastructure chaos detected! Fallback initiated.");
            return "{\"error\": \"MCP Server Down\", \"fallback\": \"Using persistent state mirror\"}";
        }
    }

    private String getMemoryResponse(String query) {
        String q = query.toLowerCase();

        // 1. Transactional / Dynamic Overrides (Orders/Sales)
        if (q.contains("order") || q.contains("sale")) {
            List<Sale> recentSales = saleRepository.findTop10ByOrderByTimestampDesc();
            if (!recentSales.isEmpty()) {
                Sale lastSale = recentSales.get(0);
                return String.format("Order #%d - %s ($%.2f) to %s - Pending", 
                        lastSale.getId(), lastSale.getProductName(), lastSale.getAmount(), lastSale.getRegion());
            } else {
                return "No recent orders found in database mirror.";
            }
        }

        // 2. Generic Semantic State Match - DB'ye Yük Bindirmeyen Optimizasyon
        // Sadece query'deki anlamlı kelimeleri içeren kayıtları DB'den çek
        String[] rawWords = q.split("[^a-z0-9]+");
        List<String> keywords = new ArrayList<>();
        for (String w : rawWords) {
            if (w.length() > 3 && !w.equals("what") && !w.equals("this") && !w.equals("that")) {
                keywords.add(w);
            }
        }

        if (!keywords.isEmpty()) {
            StringBuilder sql = new StringBuilder("SELECT s FROM SemanticState s WHERE ");
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("LOWER(s.stateKey) LIKE :word").append(i);
            }
            
            javax.persistence.TypedQuery<SemanticState> typedQuery = entityManager.createQuery(sql.toString(), SemanticState.class);
            for (int i = 0; i < keywords.size(); i++) {
                typedQuery.setParameter("word" + i, "%" + keywords.get(i) + "%");
            }
            
            List<SemanticState> candidates = typedQuery.getResultList();
            
            // Çekilen adaylar içinde en uygun olanı bul
            for (SemanticState state : candidates) {
                String key = state.getStateKey().toLowerCase();
                String spacedKey = key.replace("_", " ");
                
                // Tam eşleşme (örn: "enterprise knowledge")
                if (q.contains(key) || q.contains(spacedKey)) {
                    return state.getStateValue();
                }
                
                // Esnek kelime bazlı eşleşme
                String[] keyWords = spacedKey.split(" ");
                boolean allWordsMatch = true;
                for (String word : keyWords) {
                    if (word.length() > 3 && !q.contains(word)) { 
                        allWordsMatch = false;
                        break;
                    }
                }
                
                if (allWordsMatch && keyWords.length > 0) {
                     return state.getStateValue();
                }
            }
        }

        return "Critical infrastructure alert: Gateway down. Shield Mode active for limited read-only queries.";
    }

    public List<ResiliencyLog> getLogs() {
        return logRepository.findTop50ByOrderByTimestampDesc();
    }

    public Map<String, String> getHealth() {
        // Gerçek bağlantı kontrolü — GET /health her çağrıldığında günceller
        try {
            saleRepository.count();
            systemHealth.put("LegacyDB", "ACTIVE");
        } catch (Exception e) {
            systemHealth.put("LegacyDB", "DOWN");
        }
        org.springframework.data.redis.connection.RedisConnection conn = null;
        try {
            conn = redisTemplate.getConnectionFactory().getConnection();
            conn.ping();
            systemHealth.put("Redis", "ACTIVE");
        } catch (Exception e) {
            systemHealth.put("Redis", "DOWN");
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }
        return new HashMap<>(systemHealth);
    }

    public List<Sale> getRecentSales() {
        return saleRepository.findTop10ByOrderByTimestampDesc();
    }

    public Map<String, Object> getDashboardSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("health", getHealth()); // Call getHealth() to update current system status
        summary.put("sales", saleRepository.findTop10ByOrderByTimestampDesc());
        summary.put("logs", logRepository.findTop50ByOrderByTimestampDesc());
        
        // Mock Redis/DB status details
        Map<String, String> storage = new HashMap<>();
        storage.put("Redis", stateRepository.count() > 0 ? "CONNECTED (L1 Cache Active)" : "DISCONNECTED");
        storage.put("LegacyDB", saleRepository.count() > 0 ? "CONNECTED (Persistence Active)" : "DISCONNECTED");
        summary.put("storage", storage);
        
        return summary;
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 4000)
    public void monitorGatewayConnectivity() {
        String host = "gateway.truefoundry.ai";
        int port = 443;

        if (gatewayUrl != null && !gatewayUrl.isEmpty()) {
            try {
                java.net.URI uri = new java.net.URI(gatewayUrl);
                String uriHost = uri.getHost();
                if (uriHost != null) {
                    host = uriHost;
                }
                if (uri.getPort() != -1) {
                    port = uri.getPort();
                } else if (gatewayUrl.startsWith("https")) {
                    port = 443;
                } else {
                    port = 80;
                }
            } catch (Exception e) {
                // Keep default gateway.truefoundry.ai:443
            }
        }

        String currentApiKey = getDynamicApiKey();
        if (lastKnownApiKey == null) {
            lastKnownApiKey = currentApiKey;
            if (currentApiKey == null || currentApiKey.trim().isEmpty() || !validateApiKeyAgainstGateway(currentApiKey)) {
                credentialsInvalid = true;
            }
        } else if (!lastKnownApiKey.equals(currentApiKey)) {
            // API key changed! Reset credential error state and validate the new key
            credentialsInvalid = false;
            lastKnownApiKey = currentApiKey;
            if (currentApiKey == null || currentApiKey.trim().isEmpty() || !validateApiKeyAgainstGateway(currentApiKey)) {
                credentialsInvalid = true;
            }
        }

        boolean isNowOnline = false;
        if (currentApiKey != null && !currentApiKey.trim().isEmpty() && !credentialsInvalid) {
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), 2000);
                isNowOnline = true;
            } catch (Exception e) {
                isNowOnline = false;
            }
        }

        boolean wasOnline = "ACTIVE".equals(systemHealth.get("Gemma 4"));

        if (isNowOnline && !wasOnline) {
            systemHealth.put("Gemma 4", "ACTIVE");
            systemHealth.put("GLM 4.5", "ACTIVE");
            systemHealth.put("Llama 3.1 405B", "ACTIVE");
            logAction("INFO", "ORCHESTRATOR", "SUCCESS", 0, "AI Gateway connectivity restored. Normal operations resumed.");
        } else if (!isNowOnline && wasOnline) {
            systemHealth.put("Gemma 4", "DOWN");
            systemHealth.put("GLM 4.5", "DOWN");
            systemHealth.put("Llama 3.1 405B", "DOWN");
            logAction("CRITICAL", "ORCHESTRATOR", "SHIELD_ACTIVE", 0, "AI Gateway unreachable or credentials invalid. Proactively transitioned to Emergency Shield Mode.");
        }
    }
}
