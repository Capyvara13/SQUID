package com.squid.core.optimization;

import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Servico de otimizacao com pre-compilacao e rotinas em Assembly.
 * 
 * Este servico atua como um acelerador opcional para operacoes
 * criptograficas e de manipulacao de dados da Merkle Tree.
 * 
 * Quando ativado, utiliza rotinas otimizadas para:
 * - calculo de hashes (BLAKE2b otimizado)
 * - verificacao de Merkle Tree
 * - manipulacao de grandes estruturas de dados
 */
@Service
public class OptimizationService {
    
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final Map<String, OptimizationRoutine> routines = new ConcurrentHashMap<>();
    private final OptimizationMetrics metrics = new OptimizationMetrics();
    
    // Native library loader status
    private boolean nativeLibraryLoaded = false;
    private String nativeLibraryError = null;
    
    @PostConstruct
    public void initialize() {
        // tenta carregar a biblioteca nativa de otimizacao
        tryLoadNativeLibrary();
        
        // registra as rotinas disponiveis
        registerRoutines();
    }
    
    /**
     * Tenta carregar a biblioteca nativa com rotinas em Assembly.
     * Se falhar, o servico opera em modo Java puro (menos performatico).
     */
    private void tryLoadNativeLibrary() {
        try {
            // tenta carregar squid-opt.dll (Windows) ou libsquid-opt.so (Linux)
            String osName = System.getProperty("os.name").toLowerCase();
            String libName;
            
            if (osName.contains("win")) {
                libName = "squid-opt";
            } else if (osName.contains("linux")) {
                libName = "squid-opt";
            } else {
                libName = "squid-opt";
            }
            
            // tenta carregar a biblioteca nativa
            System.loadLibrary(libName);
            nativeLibraryLoaded = true;
            
            // inicializa as rotinas nativas
            initNativeRoutines();
            
        } catch (UnsatisfiedLinkError e) {
            nativeLibraryLoaded = false;
            nativeLibraryError = "Biblioteca nativa nao encontrada: " + e.getMessage();
        } catch (Exception e) {
            nativeLibraryLoaded = false;
            nativeLibraryError = "Erro ao carregar biblioteca nativa: " + e.getMessage();
        }
    }
    
    /**
     * Metodo nativo para inicializar rotinas otimizadas.
     * Implementado em codigo Assembly/C na biblioteca nativa.
     */
    private native void initNativeRoutines();
    
    /**
     * Metodo nativo para calcular hash BLAKE2b otimizado.
     */
    public native byte[] nativeBlake2bHash(byte[] data, int outputLength);
    
    /**
     * Metodo nativo para verificar Merkle Tree de forma otimizada.
     */
    public native boolean nativeVerifyMerkleTree(byte[][] leaves, byte[] expectedRoot);
    
    /**
     * Metodo nativo para calcular multiplos hashes em batch.
     */
    public native byte[][] nativeBatchHash(byte[][] dataArray, int outputLength);
    
    /**
     * Registra todas as rotinas de otimizacao disponiveis.
     */
    private void registerRoutines() {
        routines.put("blake2b_hash", new OptimizationRoutine(
            "blake2b_hash",
            "Calculo de hash BLAKE2b otimizado",
            nativeLibraryLoaded
        ));
        
        routines.put("merkle_verify", new OptimizationRoutine(
            "merkle_verify",
            "Verificacao de Merkle Tree otimizada",
            nativeLibraryLoaded
        ));
        
        routines.put("batch_hash", new OptimizationRoutine(
            "batch_hash",
            "Calculo de multiplos hashes em batch",
            nativeLibraryLoaded
        ));
        
        routines.put("simd_operations", new OptimizationRoutine(
            "simd_operations",
            "Operacoes vetoriais SIMD (AVX2/NEON)",
            nativeLibraryLoaded && hasSimdSupport()
        ));
    }
    
    /**
     * Verifica se o processador suporta instrucoes SIMD.
     */
    private boolean hasSimdSupport() {
        // simplificado: verifica propriedades da JVM
        String arch = System.getProperty("os.arch").toLowerCase();
        return arch.contains("x86_64") || arch.contains("amd64") || arch.contains("aarch64");
    }
    
    /**
     * Ativa ou desativa o servico de otimizacao.
     */
    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
        metrics.recordActivation(enabled);
    }
    
    /**
     * Verifica se o servico esta ativado.
     */
    public boolean isEnabled() {
        return enabled.get();
    }
    
    /**
     * Verifica se a biblioteca nativa foi carregada com sucesso.
     */
    public boolean isNativeLibraryLoaded() {
        return nativeLibraryLoaded;
    }
    
    /**
     * Retorna o erro de carregamento da biblioteca nativa, se houver.
     */
    public String getNativeLibraryError() {
        return nativeLibraryError;
    }
    
    /**
     * Calcula hash BLAKE2b usando a implementacao mais rapida disponivel.
     */
    public byte[] hashBlake2b(byte[] data, int outputLength) {
        long startTime = System.nanoTime();
        
        byte[] result;
        if (enabled.get() && nativeLibraryLoaded) {
            // usa rotina nativa otimizada
            result = nativeBlake2bHash(data, outputLength);
            metrics.recordNativeCall("blake2b_hash");
        } else {
            // fallback para implementacao Java
            result = javaBlake2bHash(data, outputLength);
            metrics.recordJavaCall("blake2b_hash");
        }
        
        metrics.recordLatency("blake2b_hash", System.nanoTime() - startTime);
        return result;
    }
    
    /**
     * Implementacao Java pura de BLAKE2b (fallback).
     */
    private byte[] javaBlake2bHash(byte[] data, int outputLength) {
        // delega para a implementacao existente em MerkleTree
        org.bouncycastle.crypto.digests.Blake2bDigest digest = 
            new org.bouncycastle.crypto.digests.Blake2bDigest(outputLength * 8);
        digest.update(data, 0, data.length);
        byte[] result = new byte[digest.getDigestSize()];
        digest.doFinal(result, 0);
        return result;
    }
    
    /**
     * Verifica uma Merkle Tree de forma otimizada.
     */
    public boolean verifyMerkleTree(byte[][] leaves, byte[] expectedRoot) {
        long startTime = System.nanoTime();
        
        boolean result;
        if (enabled.get() && nativeLibraryLoaded) {
            result = nativeVerifyMerkleTree(leaves, expectedRoot);
            metrics.recordNativeCall("merkle_verify");
        } else {
            result = javaVerifyMerkleTree(leaves, expectedRoot);
            metrics.recordJavaCall("merkle_verify");
        }
        
        metrics.recordLatency("merkle_verify", System.nanoTime() - startTime);
        return result;
    }
    
    /**
     * Implementacao Java pura de verificacao de Merkle Tree (fallback).
     */
    private boolean javaVerifyMerkleTree(byte[][] leaves, byte[] expectedRoot) {
        if (leaves == null || leaves.length == 0) {
            return false;
        }
        
        // constroi a arvore e verifica a raiz
        java.util.List<byte[]> currentLevel = new java.util.ArrayList<>();
        for (byte[] leaf : leaves) {
            currentLevel.add(javaBlake2bHash(leaf, 32));
        }
        
        while (currentLevel.size() > 1) {
            java.util.List<byte[]> nextLevel = new java.util.ArrayList<>();
            for (int i = 0; i < currentLevel.size(); i += 2) {
                byte[] left = currentLevel.get(i);
                byte[] right = (i + 1 < currentLevel.size()) ? 
                    currentLevel.get(i + 1) : left;
                nextLevel.add(hashPair(left, right));
            }
            currentLevel = nextLevel;
        }
        
        return java.util.Arrays.equals(currentLevel.get(0), expectedRoot);
    }
    
    /**
     * Hash de par de valores (left || right).
     */
    private byte[] hashPair(byte[] left, byte[] right) {
        org.bouncycastle.crypto.digests.Blake2bDigest digest = 
            new org.bouncycastle.crypto.digests.Blake2bDigest(256);
        digest.update(left, 0, left.length);
        digest.update(right, 0, right.length);
        byte[] result = new byte[digest.getDigestSize()];
        digest.doFinal(result, 0);
        return result;
    }
    
    /**
     * Calcula multiplos hashes em batch.
     */
    public byte[][] batchHash(byte[][] dataArray, int outputLength) {
        long startTime = System.nanoTime();
        
        byte[][] result;
        if (enabled.get() && nativeLibraryLoaded) {
            result = nativeBatchHash(dataArray, outputLength);
            metrics.recordNativeCall("batch_hash");
        } else {
            result = new byte[dataArray.length][];
            for (int i = 0; i < dataArray.length; i++) {
                result[i] = javaBlake2bHash(dataArray[i], outputLength);
            }
            metrics.recordJavaCall("batch_hash");
        }
        
        metrics.recordLatency("batch_hash", System.nanoTime() - startTime);
        return result;
    }
    
    /**
     * Retorna o status completo do servico de otimizacao.
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new java.util.HashMap<>();
        status.put("enabled", enabled.get());
        status.put("nativeLibraryLoaded", nativeLibraryLoaded);
        status.put("nativeLibraryError", nativeLibraryError);
        status.put("routines", getRoutinesStatus());
        status.put("metrics", metrics.getSnapshot());
        return status;
    }
    
    /**
     * Retorna o status de cada rotina de otimizacao.
     */
    public Map<String, Object> getRoutinesStatus() {
        Map<String, Object> routinesStatus = new java.util.HashMap<>();
        for (Map.Entry<String, OptimizationRoutine> entry : routines.entrySet()) {
            OptimizationRoutine routine = entry.getValue();
            Map<String, Object> routineStatus = new java.util.HashMap<>();
            routineStatus.put("name", routine.getName());
            routineStatus.put("description", routine.getDescription());
            routineStatus.put("available", routine.isAvailable());
            routineStatus.put("callCount", routine.getCallCount());
            routinesStatus.put(entry.getKey(), routineStatus);
        }
        return routinesStatus;
    }
    
    /**
     * Retorna as metricas de performance.
     */
    public OptimizationMetrics getMetrics() {
        return metrics;
    }
    
    /**
     * Classe interna representando uma rotina de otimizacao.
     */
    public static class OptimizationRoutine {
        private final String name;
        private final String description;
        private final boolean available;
        private final AtomicLong callCount = new AtomicLong(0);
        
        public OptimizationRoutine(String name, String description, boolean available) {
            this.name = name;
            this.description = description;
            this.available = available;
        }
        
        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isAvailable() { return available; }
        public long getCallCount() { return callCount.get(); }
        public void incrementCallCount() { callCount.incrementAndGet(); }
    }
    
    /**
     * Classe para coletar metricas de performance.
     */
    public static class OptimizationMetrics {
        private final Map<String, AtomicLong> nativeCalls = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> javaCalls = new ConcurrentHashMap<>();
        private final Map<String, java.util.concurrent.atomic.AtomicLongArray> latencies = 
            new ConcurrentHashMap<>();
        private final AtomicLong totalNativeCalls = new AtomicLong(0);
        private final AtomicLong totalJavaCalls = new AtomicLong(0);
        private long lastActivationTime = 0;
        private boolean wasActivated = false;
        
        public void recordNativeCall(String routine) {
            nativeCalls.computeIfAbsent(routine, k -> new AtomicLong(0)).incrementAndGet();
            totalNativeCalls.incrementAndGet();
        }
        
        public void recordJavaCall(String routine) {
            javaCalls.computeIfAbsent(routine, k -> new AtomicLong(0)).incrementAndGet();
            totalJavaCalls.incrementAndGet();
        }
        
        public void recordLatency(String routine, long nanoseconds) {
            // mantem uma amostra das ultimas 100 latencias
            latencies.computeIfAbsent(routine, k -> 
                new java.util.concurrent.atomic.AtomicLongArray(100));
            java.util.concurrent.atomic.AtomicLongArray array = latencies.get(routine);
            // simples: armazena na posicao baseada no total de chamadas modulo 100
            int index = (int) (totalNativeCalls.get() + totalJavaCalls.get()) % 100;
            array.set(index, nanoseconds);
        }
        
        public void recordActivation(boolean enabled) {
            if (enabled && !wasActivated) {
                lastActivationTime = System.currentTimeMillis();
            }
            wasActivated = enabled;
        }
        
        public Map<String, Object> getSnapshot() {
            Map<String, Object> snapshot = new java.util.HashMap<>();
            snapshot.put("totalNativeCalls", totalNativeCalls.get());
            snapshot.put("totalJavaCalls", totalJavaCalls.get());
            snapshot.put("nativeCallsByRoutine", new HashMap<>(nativeCalls));
            snapshot.put("javaCallsByRoutine", new HashMap<>(javaCalls));
            snapshot.put("lastActivationTime", lastActivationTime);
            snapshot.put("isActive", wasActivated);
            return snapshot;
        }
    }
}
