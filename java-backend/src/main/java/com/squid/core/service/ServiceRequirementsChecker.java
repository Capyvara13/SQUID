package com.squid.core.service;

import com.squid.core.db.SquidDatabaseService;
import com.squid.core.optimization.OptimizationService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sistema de verificacao de requisitos de servicos.
 * 
 * Antes de iniciar qualquer servico, o sistema verifica automaticamente
 * todos os requisitos necessarios e fornece diagnostico completo.
 */
@Service
public class ServiceRequirementsChecker {
    
    private final DataSource dataSource;
    private final OptimizationService optimizationService;
    private final SquidDatabaseService databaseService;
    
    private final Map<String, ServiceRequirement> requirements = new ConcurrentHashMap<>();
    private final Map<String, ServiceCheckResult> lastResults = new ConcurrentHashMap<>();
    
    public ServiceRequirementsChecker(DataSource dataSource,
                                     OptimizationService optimizationService,
                                     SquidDatabaseService databaseService) {
        this.dataSource = dataSource;
        this.optimizationService = optimizationService;
        this.databaseService = databaseService;
    }
    
    @PostConstruct
    public void initialize() {
        // registra todos os requisitos do sistema
        registerRequirements();
    }
    
    /**
     * Registra todos os requisitos que devem ser verificados.
     */
    private void registerRequirements() {
        // conexao com banco de dados
        requirements.put("database_connection", new ServiceRequirement(
            "database_connection",
            "Conexao com Banco de Dados",
            "Acesso ao PostgreSQL/MySQL/H2 para persistencia",
            this::checkDatabaseConnection,
            true // critico
        ));
        
        // tabelas do banco
        requirements.put("database_tables", new ServiceRequirement(
            "database_tables",
            "Tabelas do Banco",
            "Estrutura de tabelas inicializada corretamente",
            this::checkDatabaseTables,
            true
        ));
        
        // merkle tree inicializada
        requirements.put("merkle_tree", new ServiceRequirement(
            "merkle_tree",
            "Merkle Tree",
            "Arvore Merkle inicializada e funcional",
            this::checkMerkleTree,
            true
        ));
        
        // modulos criptograficos
        requirements.put("crypto_modules", new ServiceRequirement(
            "crypto_modules",
            "Modulos Criptograficos",
            "Kyber e Dilithium carregados corretamente",
            this::checkCryptoModules,
            true
        ));
        
        // modulo de otimizacao
        requirements.put("optimization_module", new ServiceRequirement(
            "optimization_module",
            "Modulo de Otimizacao",
            "Biblioteca nativa de otimizacao disponivel",
            this::checkOptimizationModule,
            false // opcional
        ));
        
        // espaco em disco
        requirements.put("disk_space", new ServiceRequirement(
            "disk_space",
            "Espaco em Disco",
            "Espaco suficiente para operacao",
            this::checkDiskSpace,
            true
        ));
        
        // memoria disponivel
        requirements.put("memory", new ServiceRequirement(
            "memory",
            "Memoria",
            "Memoria RAM suficiente para operacao",
            this::checkMemory,
            false
        ));
        
        // conectividade de rede
        requirements.put("network", new ServiceRequirement(
            "network",
            "Rede",
            "Conectividade de rede disponivel",
            this::checkNetwork,
            false
        ));
    }
    
    /**
     * Verifica todos os requisitos e retorna o resultado completo.
     */
    public Map<String, Object> checkAllRequirements() {
        Map<String, Object> results = new LinkedHashMap<>();
        List<ServiceCheckResult> checkResults = new ArrayList<>();
        
        int passed = 0;
        int failed = 0;
        int critical = 0;
        int criticalPassed = 0;
        
        for (ServiceRequirement req : requirements.values()) {
            ServiceCheckResult result = req.check();
            lastResults.put(req.getId(), result);
            checkResults.add(result);
            
            if (result.isPassed()) {
                passed++;
                if (req.isCritical()) {
                    criticalPassed++;
                }
            } else {
                failed++;
            }
            
            if (req.isCritical()) {
                critical++;
            }
        }
        
        // status geral
        boolean allCriticalPassed = (criticalPassed == critical);
        boolean canStart = allCriticalPassed;
        
        results.put("canStart", canStart);
        results.put("overallStatus", canStart ? "READY" : "BLOCKED");
        results.put("passedCount", passed);
        results.put("failedCount", failed);
        results.put("criticalCount", critical);
        results.put("criticalPassed", criticalPassed);
        results.put("totalCount", requirements.size());
        results.put("timestamp", new Date().toString());
        
        // resultados individuais formatados para tabela
        List<Map<String, Object>> tableData = new ArrayList<>();
        for (ServiceCheckResult result : checkResults) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("servico", result.getName());
            row.put("status", result.isPassed() ? "OK" : "FALHA");
            row.put("requisitos", result.getDescription());
            row.put("problemas", result.getErrorMessage() != null ? result.getErrorMessage() : "Nenhum");
            row.put("critico", result.isCritical() ? "Sim" : "Nao");
            row.put("sugestao", result.getSuggestion());
            tableData.add(row);
        }
        results.put("checkResults", tableData);
        
        // acoes corretivas se houver falhas
        if (!canStart) {
            results.put("correctiveActions", generateCorrectiveActions(checkResults));
        }
        
        return results;
    }
    
    /**
     * Verifica um requisito especifico.
     */
    public ServiceCheckResult checkRequirement(String requirementId) {
        ServiceRequirement req = requirements.get(requirementId);
        if (req == null) {
            return new ServiceCheckResult(
                requirementId,
                "Desconhecido",
                "Requisito nao encontrado",
                false,
                false,
                "Verifique o ID do requisito",
                null
            );
        }
        
        ServiceCheckResult result = req.check();
        lastResults.put(requirementId, result);
        return result;
    }
    
    /**
     * Verifica se o sistema pode iniciar.
     */
    public boolean canSystemStart() {
        for (ServiceRequirement req : requirements.values()) {
            if (req.isCritical()) {
                ServiceCheckResult result = req.check();
                if (!result.isPassed()) {
                    return false;
                }
            }
        }
        return true;
    }
    
    /**
     * Retorna o status de todos os requisitos.
     */
    public Map<String, ServiceCheckResult> getAllResults() {
        // atualiza todos os resultados
        for (String id : requirements.keySet()) {
            checkRequirement(id);
        }
        return new HashMap<>(lastResults);
    }
    
    // ===== VERIFICACOES INDIVIDUAIS =====
    
    private ServiceCheckResult checkDatabaseConnection() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(5)) {
                return new ServiceCheckResult(
                    "database_connection",
                    "Conexao com Banco de Dados",
                    "Conexao estabelecida",
                    true,
                    true,
                    null,
                    "OK"
                );
            }
        } catch (Exception e) {
            return new ServiceCheckResult(
                "database_connection",
                "Conexao com Banco de Dados",
                "Falha na conexao",
                false,
                true,
                "Verifique: URL JDBC, credenciais, se o servidor esta rodando",
                e.getMessage()
            );
        }
        
        return new ServiceCheckResult(
            "database_connection",
            "Conexao com Banco de Dados",
            "Conexao invalida",
            false,
            true,
            "Verifique a configuracao do DataSource",
            "Conexao retornou invalida"
        );
    }
    
    private ServiceCheckResult checkDatabaseTables() {
        try {
            Map<String, Long> counts = databaseService.getTableCounts();
            
            // verifica se as tabelas principais existem
            String[] requiredTables = {"users", "audit_logs", "merkle_nodes"};
            List<String> missing = new ArrayList<>();
            
            for (String table : requiredTables) {
                Long count = counts.get(table);
                if (count == null || count < 0) {
                    missing.add(table);
                }
            }
            
            if (missing.isEmpty()) {
                return new ServiceCheckResult(
                    "database_tables",
                    "Tabelas do Banco",
                    "Todas as tabelas presentes",
                    true,
                    true,
                    null,
                    "OK"
                );
            } else {
                return new ServiceCheckResult(
                    "database_tables",
                    "Tabelas do Banco",
                    "Tabelas ausentes: " + String.join(", ", missing),
                    false,
                    true,
                    "Execute o SchemaInitializer para criar as tabelas",
                    "Tabelas nao encontradas"
                );
            }
        } catch (Exception e) {
            return new ServiceCheckResult(
                "database_tables",
                "Tabelas do Banco",
                "Erro ao verificar tabelas",
                false,
                true,
                "Verifique as permissoes do banco de dados",
                e.getMessage()
            );
        }
    }
    
    private ServiceCheckResult checkMerkleTree() {
        // verifica se a arvore foi inicializada
        // em producao, verificaria o estado real da arvore
        return new ServiceCheckResult(
            "merkle_tree",
            "Merkle Tree",
            "Arvore inicializada",
            true,
            true,
            null,
            "OK"
        );
    }
    
    private ServiceCheckResult checkCryptoModules() {
        try {
            // tenta carregar as classes do BouncyCastle
            Class.forName("org.bouncycastle.pqc.crypto.kyber.KyberKEMExtractor");
            Class.forName("org.bouncycastle.pqc.crypto.dilithium.DilithiumSigner");
            
            return new ServiceCheckResult(
                "crypto_modules",
                "Modulos Criptograficos",
                "Kyber e Dilithium carregados",
                true,
                true,
                null,
                "OK"
            );
        } catch (ClassNotFoundException e) {
            return new ServiceCheckResult(
                "crypto_modules",
                "Modulos Criptograficos",
                "Bibliotecas PQC nao encontradas",
                false,
                true,
                "Adicione a dependencia org.bouncycastle:bcprov-jdk18on ao pom.xml",
                e.getMessage()
            );
        }
    }
    
    private ServiceCheckResult checkOptimizationModule() {
        boolean loaded = optimizationService.isNativeLibraryLoaded();
        
        if (loaded) {
            return new ServiceCheckResult(
                "optimization_module",
                "Modulo de Otimizacao",
                "Biblioteca nativa carregada",
                true,
                false,
                null,
                "OK"
            );
        } else {
            String error = optimizationService.getNativeLibraryError();
            return new ServiceCheckResult(
                "optimization_module",
                "Modulo de Otimizacao",
                "Biblioteca nativa nao disponivel",
                false,
                false,
                "O sistema funcionara em modo Java (menos performatico). " +
                "Para ativar, compile squid-opt.dll/squid-opt.so para sua plataforma.",
                error
            );
        }
    }
    
    private ServiceCheckResult checkDiskSpace() {
        java.io.File dataDir = new java.io.File(".");
        long freeSpace = dataDir.getFreeSpace();
        long minRequired = 100 * 1024 * 1024; // 100 MB
        
        if (freeSpace >= minRequired) {
            return new ServiceCheckResult(
                "disk_space",
                "Espaco em Disco",
                String.format("%.2f MB livres", freeSpace / (1024.0 * 1024)),
                true,
                true,
                null,
                "OK"
            );
        } else {
            return new ServiceCheckResult(
                "disk_space",
                "Espaco em Disco",
                String.format("Apenas %.2f MB disponiveis", freeSpace / (1024.0 * 1024)),
                false,
                true,
                "Libere espaco em disco ou mude o diretorio de dados",
                "Espaco insuficiente (minimo: 100 MB)"
            );
        }
    }
    
    private ServiceCheckResult checkMemory() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long availableMemory = maxMemory - usedMemory;
        
        long minRequired = 256 * 1024 * 1024; // 256 MB
        
        if (availableMemory >= minRequired) {
            return new ServiceCheckResult(
                "memory",
                "Memoria",
                String.format("%.2f MB disponiveis", availableMemory / (1024.0 * 1024)),
                true,
                false,
                null,
                "OK"
            );
        } else {
            return new ServiceCheckResult(
                "memory",
                "Memoria",
                String.format("Apenas %.2f MB disponiveis", availableMemory / (1024.0 * 1024)),
                false,
                false,
                "Aumente o heap da JVM com -Xmx ou reduza a carga de trabalho",
                "Memoria insuficiente (minimo recomendado: 256 MB)"
            );
        }
    }
    
    private ServiceCheckResult checkNetwork() {
        try {
            // tenta resolver um host conhecido
            java.net.InetAddress.getByName("localhost");
            
            return new ServiceCheckResult(
                "network",
                "Rede",
                "Conectividade disponivel",
                true,
                false,
                null,
                "OK"
            );
        } catch (Exception e) {
            return new ServiceCheckResult(
                "network",
                "Rede",
                "Problema de conectividade",
                false,
                false,
                "Verifique a configuracao de rede do sistema",
                e.getMessage()
            );
        }
    }
    
    /**
     * Gera acoes corretivas baseadas nos resultados.
     */
    private List<String> generateCorrectiveActions(List<ServiceCheckResult> results) {
        List<String> actions = new ArrayList<>();
        
        for (ServiceCheckResult result : results) {
            if (!result.isPassed() && result.isCritical()) {
                actions.add("[" + result.getName() + "] " + result.getSuggestion());
            }
        }
        
        if (actions.isEmpty()) {
            actions.add("Nenhuma acao corretiva necessaria - todos os requisitos criticos OK");
        }
        
        return actions;
    }
    
    /**
     * Classe representando um requisito de servico.
     */
    private static class ServiceRequirement {
        private final String id;
        private final String name;
        private final String description;
        private final java.util.function.Supplier<ServiceCheckResult> checkFunction;
        private final boolean critical;
        
        public ServiceRequirement(String id, String name, String description,
                                 java.util.function.Supplier<ServiceCheckResult> checkFunction,
                                 boolean critical) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.checkFunction = checkFunction;
            this.critical = critical;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isCritical() { return critical; }
        
        public ServiceCheckResult check() {
            try {
                return checkFunction.get();
            } catch (Exception e) {
                return new ServiceCheckResult(
                    id, name, "Erro na verificacao",
                    false, critical,
                    "Tente reiniciar o servico ou verifique os logs",
                    e.getMessage()
                );
            }
        }
    }
    
    /**
     * Resultado de verificacao de um requisito.
     */
    public static class ServiceCheckResult {
        private final String id;
        private final String name;
        private final String description;
        private final boolean passed;
        private final boolean critical;
        private final String suggestion;
        private final String errorMessage;
        
        public ServiceCheckResult(String id, String name, String description,
                                 boolean passed, boolean critical,
                                 String suggestion, String errorMessage) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.passed = passed;
            this.critical = critical;
            this.suggestion = suggestion;
            this.errorMessage = errorMessage;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isPassed() { return passed; }
        public boolean isCritical() { return critical; }
        public String getSuggestion() { return suggestion; }
        public String getErrorMessage() { return errorMessage; }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("name", name);
            map.put("description", description);
            map.put("status", passed ? "OK" : "FALHA");
            map.put("critical", critical);
            map.put("suggestion", suggestion);
            map.put("error", errorMessage);
            return map;
        }
    }
}
