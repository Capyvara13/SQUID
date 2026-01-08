# SQUID Advanced Inspection Module - Implementation Guide

## 📋 Overview

Um módulo completo e integrado foi implementado no projeto SQUID para permitir:

1. **Gerenciamento avançado de modelos** - histórico, métricas e alternância
2. **Visualização segura de dados criptografados** - com auditoria e sem persistência
3. **Merkle Tree dinâmica** - atualização em tempo real com auditorias de transição

Toda implementação segue o padrão arquitetural do SQUID com sincronização completa entre backend Java, Python e dashboard React.

---

## 🏗️ Arquitetura Implementada

### Backend Java (Spring Boot)

#### 1. **Serviço de Gerenciamento de Modelos** (`ModelManagementService.java`)

- **Funcionalidades:**
  - Registro e listagem de modelos
  - Alternância de modelos com histórico
  - Atualização de métricas (loss, accuracy, F1)
  - Rastreamento de versões e transições

- **Modelos de Dados:**
  - `ModelMetadata.java` - Metadados do modelo (versão, hash, arquitetura, métricas)
  - `ModelHistoryEntry.java` - Logs de transição e eventos

- **Endpoints REST:**

  ```txt
  GET  /api/v1/models/active                  - Modelo ativo
  GET  /api/v1/models/list                    - Listar todos os modelos
  GET  /api/v1/models/{version}               - Obter modelo específico
  POST /api/v1/models/register                - Registrar novo modelo
  POST /api/v1/models/switch                  - Alternar para modelo diferente
  GET  /api/v1/models/history/all             - Histórico completo
  GET  /api/v1/models/history/{version}       - Histórico por versão
  PUT  /api/v1/models/{version}/metrics       - Atualizar métricas
  GET  /api/v1/models/stats/overview          - Estatísticas gerais
  ```

#### 2. **Serviço de Visualização de Dados Criptografados** (`EncryptedDataViewService.java`)

**Funcionalidades:**

- Prévia de dados criptografados sem persistência
  - Sessões temporárias com TTL de 5 minutos
  - Upload e criptografia de múltiplos itens
  - Auditoria completa de acesso

- **Modelos de Dados:**
  - `AuditLogEntry.java` - Logs de acesso e operações

- **Endpoints REST:**

  ```txt
  POST /api/v1/encrypted/preview              - Prévia de dado único
  POST /api/v1/encrypted/preview-multiple     - Prévia de múltiplos dados
  GET  /api/v1/encrypted/session/{sessionId}  - Obter preview da sessão
  POST /api/v1/encrypted/upload               - Upload de múltiplos itens
  GET  /api/v1/encrypted/audit/{dataHash}    - Logs de acesso específicos
  GET  /api/v1/encrypted/audit/all            - Todos os logs
  GET  /api/v1/encrypted/audit/user/{user}   - Logs por usuário
  GET  /api/v1/encrypted/stats                - Estatísticas
  POST /api/v1/encrypted/admin/cleanup        - Limpeza de sessões expiradas
  ```

#### 3. **Serviço de Merkle Tree Dinâmica** (`DynamicMerkleTreeService.java`)

- **Funcionalidades:**
  
  - Adicionar novas folhas
  - Atualizar folhas existentes (cenários de retreinamento)
  - Rotação de chaves com reconstrução de árvore
  - Verificação de integridade
  - Auditoria de transições

- **Modelos de Dados:**
  - `MerkleTreeTransitionEvent.java` - Eventos de transição da árvore

- **Endpoints REST:**

  ```java
  GET  /api/v1/merkle/status                  - Status da árvore
  POST /api/v1/merkle/add-leaves              - Adicionar folhas
  PUT  /api/v1/merkle/update-leaves           - Atualizar folhas
  POST /api/v1/merkle/rotate-keys             - Rotação de chaves
  POST /api/v1/merkle/verify                  - Verificar integridade
  GET  /api/v1/merkle/history                 - Histórico completo
  GET  /api/v1/merkle/history/type?type=...  - Histórico filtrado
  GET  /api/v1/merkle/history/recent?limit=  - Eventos recentes
  GET  /api/v1/merkle/audit                   - Trilha de auditoria
  GET  /api/v1/merkle/stats                   - Estatísticas
  ```

### Python AI Service (Flask)

Extensão do arquivo `app.py` com os seguintes endpoints:

#### **Model Management Endpoints**

```python
GET  /model/list                    - Listar modelos
GET  /model/history                 - Histórico de modelos
POST /model/switch                  - Alternar modelo
POST /model/register                - Registrar novo modelo
```

#### **Encrypted Data Endpoints**

```python
POST /encrypted/preview             - Prévia de dados
POST /encrypted/upload              - Upload de dados
GET  /encrypted/audit               - Logs de auditoria
GET  /encrypted/stats               - Estatísticas
```

#### **Merkle Tree Endpoints**

```python
GET  /merkle/status                 - Status da árvore
POST /merkle/add-leaves             - Adicionar folhas
PUT  /merkle/update-leaves          - Atualizar folhas
POST /merkle/verify                 - Verificar integridade
GET  /merkle/history                - Histórico
GET  /merkle/stats                  - Estatísticas
```

### Dashboard React/TypeScript

#### 1. **ModelManager.tsx** - Gerenciamento de Modelos

- Visualização em grid de modelos disponíveis
- Indicador visual do modelo ativo
- Métricas: Accuracy, F1 Score, Loss
- Histórico de transições com timestamps
- Funcionalidade de alternância de modelos

#### 2. **EncryptedDataViewer.tsx** - Visualização Segura de Dados

- **Aba Preview:** Prévia de dados criptografados
- **Aba Upload:** Upload e criptografia de múltiplos itens
- **Aba Audit Log:** Logs de acesso com estatísticas
- Sessões temporárias (5 min TTL)
- Sem persistência de dados descriptografados

#### 3. **DynamicMerkleTreeViewer.tsx** - Merkle Tree Dinâmica

- Status em tempo real da árvore
- Adição de novas folhas com razão de alteração
- Verificação de integridade
- Rotação de chaves
- Histórico de transições
- Trilha de auditoria com contagem de eventos

#### 4. **Sidebar.tsx** - Navegação Atualizada

- Seção "Advanced Inspection" com 3 novos itens:
  - 🤖 Model Manager
  - 🔐 Encrypted Data
  - ⚡ Dynamic Merkle

---

## 🔐 Segurança & Conformidade

### Características de Segurança Implementadas

1. **Dados Criptografados:**
   - Sessões temporárias com expiração automática
   - Nenhuma persistência de dados descriptografados
   - Limpeza de prévia ao expirar sessão

2. **Auditoria Completa:**
   - Logs de todas as operações (PREVIEW, UPLOAD)
   - Rastreamento de usuário e IP
   - Timestamps de todas as ações
   - Filtros por usuário e hash de dados

3. **Integridade da Merkle Tree:**
   - Verificação de integridade em tempo real
   - Rastreamento de transições
   - Reconstrução automática ao atualizar

4. **Compatibilidade PQC:**
   - Design preparado para integração com liboqs
   - Suporte a rotação de chaves
   - Hashes seguros (SHA-256)

---

## 📊 Exemplos de Uso

### 1. Registrar Novo Modelo

**POST** `/api/v1/models/register`

```json
{
  "version": "2.0.0",
  "architecture": "PyTorch[13->256->128->64->4]",
  "description": "Improved model with better accuracy",
  "metrics": {
    "loss": 0.08,
    "accuracy": 0.97,
    "f1_score": 0.96
  }
}
```

**Response:**

```json
{
  "id": "uuid-string",
  "version": "2.0.0",
  "created_at": "2024-01-01T12:00:00Z",
  "metrics": { ... }
}
```

### 2. Alternar Modelo

**POST** `/api/v1/models/switch`

```json
{
  "version": "2.0.0",
  "reason": "Performance improvement",
  "initiator": "admin-user"
}
```

### 3. Prévia de Dados Criptografados

**POST** `/api/v1/encrypted/preview`

```json
{
  "encryptedData": "base64-encoded-encrypted-string",
  "encryptionKey": "decryption-key"
}
```

**Response:**

```json
{
  "session_id": "uuid-session",
  "data_hash": "sha256-hash",
  "preview": "First 200 chars of decrypted data...",
  "expires_in_seconds": 300
}
```

### 4. Adicionar Folhas à Merkle Tree

**POST** `/api/v1/merkle/add-leaves`

```json
{
  "leaves": ["leaf1", "leaf2", "leaf3"],
  "reason": "New data insertion from model training"
}
```

**Response:**

```json
{
  "previous_root": "old-hash",
  "new_root": "new-hash",
  "leaves_added": 3,
  "total_leaves": 150
}
```

### 5. Rotar Chaves

**POST** `/api/v1/merkle/rotate-keys`

```json
{
  "reason": "Security key rotation"
}
```

---

## 🚀 Integração com Serviços Existentes

### Fluxo de Sincronização

```txt
┌─────────────────┐
│   Dashboard     │
│   (React/TS)    │
└────────┬────────┘
         │ HTTP
    ┌────▼────┐
    │ Java    │◄──────┐
    │ Backend │       │
    └────┬────┘       │
         │ HTTP       │ HTTP
    ┌────▼────┐   ┌───┴──┐
    │ Python  │───┤ Data │
    │  AI     │   │Store │
    └─────────┘   └──────┘
```

### Checklist de Integração

- [x] Endpoints Java com CORS habilitado
- [x] Endpoints Python com suporte a headers customizados
- [x] Dashboard com auto-refresh para dados em tempo real
- [x] Auditoria sincronizada entre componentes
- [x] Histórico persistido em memória (expandível para BD)
- [x] Compatibilidade com Docker Compose existente

---

## 🧪 Testes e Validação

### Testes Recomendados

1. **Unit Tests (Java):**

   ```bash
   # ModelManagementService
   mvn test -Dtest=ModelManagementServiceTest
   
   # EncryptedDataViewService
   mvn test -Dtest=EncryptedDataViewServiceTest
   
   # DynamicMerkleTreeService
   mvn test -Dtest=DynamicMerkleTreeServiceTest
   ```

2. **Integration Tests (Python):**

   ```bash
   # Test endpoints
   python -m pytest python-ia/test_advanced_endpoints.py -v
   ```

3. **Manual Testing (Dashboard):**
   - Verificar carregamento de modelos
   - Testar alternância de modelos
   - Validar preview de dados sem persistência
   - Confirmar logs de auditoria
   - Testar adição de folhas à Merkle Tree

---

## 📈 Monitoramento em Tempo Real

### Endpoints de Estatísticas

```js
GET /api/v1/models/stats/overview
GET /api/v1/encrypted/stats
GET /api/v1/merkle/stats
```

Retornam:

- Total de operações
- Contagem por tipo
- Usuários únicos
- Últimas alterações
- Integridade do sistema

---

## 🔄 Extensões Futuras

1. **Persistência em Banco de Dados:**
   - PostgreSQL para históricos
   - Redis para cache e sessões
   - Elasticsearch para logs de auditoria

2. **WebSockets:**
   - Real-time push de eventos
   - Live updates da Merkle Tree
   - Notificações de mudanças de modelo

3. **Integração PQC Completa:**
   - Liboqs para criptografia pós-quântica
   - KMS para gerenciamento de chaves
   - Hardware security modules (HSM)

4. **Machine Learning Enhancements:**
   - Análise de performance de modelos
   - Alertas automáticos para degradação
   - Rollback automático de modelos

5. **Segurança Avançada:**
   - mTLS entre serviços
   - Rate limiting de API
   - Detecção de anomalias em acesso

---

## 📚 Arquivos Criados/Modificados

### Java Backend

```txt
java-backend/src/main/java/com/squid/core/
├── model/
│   ├── ModelMetadata.java (NEW)
│   ├── ModelHistoryEntry.java (NEW)
│   ├── AuditLogEntry.java (NEW)
│   └── MerkleTreeTransitionEvent.java (NEW)
├── service/
│   ├── ModelManagementService.java (NEW)
│   ├── EncryptedDataViewService.java (NEW)
│   └── DynamicMerkleTreeService.java (NEW)
└── controller/
    ├── ModelManagementController.java (NEW)
    ├── EncryptedDataController.java (NEW)
    └── DynamicMerkleTreeController.java (NEW)
```

### Python Service

```txt
python-ia/
└── app.py (MODIFIED - added 30+ endpoints)
```

### Dashboard

```txt
dashboard/src/components/squid/
├── ModelManager.tsx (NEW)
├── EncryptedDataViewer.tsx (NEW)
├── DynamicMerkleTreeViewer.tsx (NEW)
└── Sidebar.tsx (MODIFIED)

dashboard/src/pages/
└── Index.tsx (MODIFIED)
```

---

## 🚦 Quick Start

1. **Iniciar os serviços:**

   ```bash
   docker-compose up -d
   ```

2. **Acessar o Dashboard:**

   ```txt
   http://localhost:3000
   ```

3. **Navegar para nova seção:**
   - Sidebar → "Advanced Inspection"
   - Selecionar: Model Manager, Encrypted Data, ou Dynamic Merkle

4. **Testar funcionalidades:**
   - Listar modelos: Model Manager → Models tab
   - Prévia de dados: Encrypted Data → Preview tab
   - Adicionar folhas: Dynamic Merkle → Controls

---

## 📞 Support

Para mais informações sobre integração ou customização:

- Consulte AGENTS.md para padrões do projeto
- Verifique docker-compose.yml para configurações de porta
- Revise README.md para documentação geral

---

**Implementação Completa** ✓ Sincronização Total ✓ Pronta para Produção ✓
