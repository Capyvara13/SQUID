package com.squid.core.lock;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Gerenciador de locks para instancias durante operacoes de atualizacao.
 * 
 * Durante alteracoes estruturais na Merkle Tree, a instancia e protegida
 * contra concorrencia para evitar race conditions que possam gerar
 * arvores inconsistentes.
 * 
 * Implementa:
 * - Lock temporario por instancia
 * - Fila de operacoes sequenciais
 * - Timeout automatico para evitar deadlocks
 */
public class InstanceLockManager {
    
    // locks por instancia
    private final Map<String, InstanceLock> instanceLocks = new ConcurrentHashMap<>();
    
    // timeout padrao para locks (30 segundos)
    private static final long DEFAULT_LOCK_TIMEOUT_MS = 30000;
    
    // timeout maximo para operacoes criticas (5 minutos)
    private static final long MAX_LOCK_TIMEOUT_MS = 300000;
    
    public InstanceLockManager() {
        // inicia thread de limpeza de locks expirados
        startCleanupThread();
    }
    
    /**
     * Adquire um lock de leitura para uma instancia.
     * Multiplos leitores podem acessar simultaneamente.
     */
    public boolean acquireReadLock(String instanceId, String operationId) {
        InstanceLock lock = getOrCreateLock(instanceId);
        return lock.acquireReadLock(operationId);
    }
    
    /**
     * Adquire um lock de escrita para uma instancia.
     * Apenas um escritor pode acessar por vez.
     */
    public boolean acquireWriteLock(String instanceId, String operationId, long timeoutMs) {
        InstanceLock lock = getOrCreateLock(instanceId);
        return lock.acquireWriteLock(operationId, timeoutMs);
    }
    
    /**
     * Adquire um lock de escrita com timeout padrao.
     */
    public boolean acquireWriteLock(String instanceId, String operationId) {
        return acquireWriteLock(instanceId, operationId, DEFAULT_LOCK_TIMEOUT_MS);
    }
    
    /**
     * Libera o lock de uma instancia.
     */
    public void releaseLock(String instanceId, String operationId) {
        InstanceLock lock = instanceLocks.get(instanceId);
        if (lock != null) {
            lock.releaseLock(operationId);
        }
    }
    
    /**
     * Verifica se uma instancia esta bloqueada para escrita.
     */
    public boolean isWriteLocked(String instanceId) {
        InstanceLock lock = instanceLocks.get(instanceId);
        return lock != null && lock.isWriteLocked();
    }
    
    /**
     * Verifica se uma instancia esta bloqueada (leitura ou escrita).
     */
    public boolean isLocked(String instanceId) {
        InstanceLock lock = instanceLocks.get(instanceId);
        return lock != null && lock.isLocked();
    }
    
    /**
     * Retorna o status de lock de uma instancia.
     */
    public LockStatus getLockStatus(String instanceId) {
        InstanceLock lock = instanceLocks.get(instanceId);
        if (lock == null) {
            return new LockStatus(instanceId, false, false, 0, null, null);
        }
        return lock.getStatus();
    }
    
    /**
     * Forca a liberacao de todos os locks de uma instancia.
     * Use com cuidado - apenas para recuperacao de deadlock.
     */
    public void forceReleaseAll(String instanceId) {
        InstanceLock lock = instanceLocks.get(instanceId);
        if (lock != null) {
            lock.forceReleaseAll();
        }
    }
    
    /**
     * Aguarda ate que o lock de escrita seja liberado.
     */
    public boolean waitForWriteLock(String instanceId, long timeoutMs) {
        long startTime = System.currentTimeMillis();
        while (isWriteLocked(instanceId)) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                return false; // timeout
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }
    
    /**
     * Cria ou retorna um lock existente para a instancia.
     */
    private InstanceLock getOrCreateLock(String instanceId) {
        return instanceLocks.computeIfAbsent(instanceId, InstanceLock::new);
    }
    
    /**
     * Inicia thread de limpeza de locks expirados.
     */
    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    Thread.sleep(60000); // limpa a cada minuto
                    cleanupExpiredLocks();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.setName("InstanceLock-Cleanup");
        cleanupThread.start();
    }
    
    /**
     * Remove locks expirados.
     */
    private void cleanupExpiredLocks() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, InstanceLock> entry : instanceLocks.entrySet()) {
            InstanceLock lock = entry.getValue();
            if (lock.isExpired(now)) {
                lock.forceReleaseAll();
                instanceLocks.remove(entry.getKey());
            }
        }
    }
    
    /**
     * Classe interna representando o lock de uma instancia.
     */
    private static class InstanceLock {
        private final String instanceId;
        private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);
        private final Map<String, LockInfo> activeOperations = new ConcurrentHashMap<>();
        private volatile long lastActivityTime;
        
        public InstanceLock(String instanceId) {
            this.instanceId = instanceId;
            this.lastActivityTime = System.currentTimeMillis();
        }
        
        public boolean acquireReadLock(String operationId) {
            try {
                boolean acquired = rwLock.readLock().tryLock(5, TimeUnit.SECONDS);
                if (acquired) {
                    activeOperations.put(operationId, new LockInfo(
                        operationId, LockType.READ, System.currentTimeMillis(),
                        Thread.currentThread().getName()
                    ));
                    updateActivity();
                }
                return acquired;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        public boolean acquireWriteLock(String operationId, long timeoutMs) {
            try {
                boolean acquired = rwLock.writeLock().tryLock(timeoutMs, TimeUnit.MILLISECONDS);
                if (acquired) {
                    activeOperations.put(operationId, new LockInfo(
                        operationId, LockType.WRITE, System.currentTimeMillis(),
                        Thread.currentThread().getName()
                    ));
                    updateActivity();
                }
                return acquired;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        public void releaseLock(String operationId) {
            LockInfo info = activeOperations.remove(operationId);
            if (info != null) {
                if (info.getType() == LockType.READ) {
                    rwLock.readLock().unlock();
                } else {
                    rwLock.writeLock().unlock();
                }
                updateActivity();
            }
        }
        
        public boolean isWriteLocked() {
            return rwLock.isWriteLocked();
        }
        
        public boolean isLocked() {
            return rwLock.getReadLockCount() > 0 || rwLock.isWriteLocked();
        }
        
        public boolean isExpired(long currentTime) {
            // expira se inativo por mais de 10 minutos
            return (currentTime - lastActivityTime) > 600000;
        }
        
        public void forceReleaseAll() {
            // libera todos os locks
            while (rwLock.getReadHoldCount() > 0) {
                rwLock.readLock().unlock();
            }
            if (rwLock.isWriteLockedByCurrentThread()) {
                rwLock.writeLock().unlock();
            }
            activeOperations.clear();
        }
        
        public LockStatus getStatus() {
            return new LockStatus(
                instanceId,
                isLocked(),
                isWriteLocked(),
                rwLock.getReadLockCount(),
                activeOperations.values().stream()
                    .filter(i -> i.getType() == LockType.WRITE)
                    .findFirst()
                    .map(LockInfo::getOperationId)
                    .orElse(null),
                new ConcurrentHashMap<>(activeOperations)
            );
        }
        
        private void updateActivity() {
            lastActivityTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Informacoes sobre um lock ativo.
     */
    private static class LockInfo {
        private final String operationId;
        private final LockType type;
        private final long acquireTime;
        private final String threadName;
        
        public LockInfo(String operationId, LockType type, long acquireTime, String threadName) {
            this.operationId = operationId;
            this.type = type;
            this.acquireTime = acquireTime;
            this.threadName = threadName;
        }
        
        public String getOperationId() { return operationId; }
        public LockType getType() { return type; }
        public long getAcquireTime() { return acquireTime; }
        public String getThreadName() { return threadName; }
    }
    
    /**
     * Tipo de lock.
     */
    private enum LockType {
        READ,
        WRITE
    }
    
    /**
     * Status de lock de uma instancia.
     */
    public static class LockStatus {
        private final String instanceId;
        private final boolean locked;
        private final boolean writeLocked;
        private final int readLockCount;
        private final String currentWriteOperation;
        private final Map<String, LockInfo> activeOperations;
        
        public LockStatus(String instanceId, boolean locked, boolean writeLocked,
                         int readLockCount, String currentWriteOperation,
                         Map<String, LockInfo> activeOperations) {
            this.instanceId = instanceId;
            this.locked = locked;
            this.writeLocked = writeLocked;
            this.readLockCount = readLockCount;
            this.currentWriteOperation = currentWriteOperation;
            this.activeOperations = activeOperations != null ? 
                new ConcurrentHashMap<>(activeOperations) : new ConcurrentHashMap<>();
        }
        
        public String getInstanceId() { return instanceId; }
        public boolean isLocked() { return locked; }
        public boolean isWriteLocked() { return writeLocked; }
        public int getReadLockCount() { return readLockCount; }
        public String getCurrentWriteOperation() { return currentWriteOperation; }
        public Map<String, LockInfo> getActiveOperations() { 
            return new ConcurrentHashMap<>(activeOperations); 
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new ConcurrentHashMap<>();
            map.put("instanceId", instanceId);
            map.put("locked", locked);
            map.put("writeLocked", writeLocked);
            map.put("readLockCount", readLockCount);
            map.put("currentWriteOperation", currentWriteOperation);
            map.put("activeOperations", activeOperations.keySet());
            return map;
        }
    }
}
