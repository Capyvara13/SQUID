# S.Q.I.D. - Simulação Quântica Inspirada para Defesa de Dados

## Overview

S.Q.I.D. is a hybrid backend system (Java + Python) that generates, validates, and audits tokens/values derived from a seed key, with post-quantum resistance, Merkle auditing, and an AI layer that applies super-relations for marking/generating decoys, mutations, and remappings.

## Architecture

- **Java Backend (SQUID-Core)**: REST API for canonicalization, hybrid KEM, HKDF derivation, deterministic branching tree generation, Merkle construction, secure storage, and verification endpoints
- **Python IA Service (SQUID-AI)**: PyTorch service for computing features per leaf, calculating SR and C, inferring actions via interpretable model, and returning decisions to Java

## Quick Start

```bash
# Build and run services
docker-compose up --build

# Generate test vectors
docker-compose exec java-backend curl -X GET http://localhost:8080/testvectors

# Verify deterministic output
docker-compose exec python-ia python test_vectors.py
```

## Test Vectors

The system generates 3 deterministic test vectors that must match bytewise between Java and Python implementations:

1. **tv1**: Basic input canonicalization and leaf generation
2. **tv2**: Complex branching with decoy generation
3. **tv3**: Mutation and reassignment scenarios

## Security Features

- **Post-Quantum Cryptography**: Kyber KEM + Dilithium signatures
- **Deterministic Operations**: All operations reproducible from seed
- **Merkle Auditing**: Periodic publication of signed merkle roots
- **KMS Integration**: Secure key storage and rotation
- **Canonical Serialization**: JSON/CBOR canonical encoding

## KMS Integration

### AWS KMS Setup

```bash
export AWS_REGION=us-east-1
export SQUID_KMS_KEY_ID=alias/squid-master-key
```

### HashiCorp Vault Setup

```bash
export VAULT_ADDR=https://vault.example.com
export VAULT_TOKEN=hvs.xxxxx
export VAULT_KV_PATH=squid/keys
```

## Key Rotation

```bash
# Rotate master key
./scripts/rotate-keys.sh --new-version

# Update model with new signature
./scripts/update-model.sh --model-path models/v2.pth --sign
```

## Performance Benchmarks

Default parameters maintain T_build < 60s on target hardware:

- KDF time: ~10ms per leaf
- Merkle build: ~50ms for 1000 leaves
- IA decision: ~100ms per batch

## Formulas Implementation

### Super-Relation (SR)

```markdown
SR = (2T/L) × K^(M-1)/2 × (∑[p=1 to P_max] max(3/2)^p / (p^α × P(1-P))) × g(b)
```

where `g(b) = b³ - 1/(3b²)`

### Correlation Coefficient (C)

```markdown
C = (t × b^a × ∑[i=1 to m] b_i) / P^(2d+1)
```

### Policy Thresholds

- If `SR ≥ 1.0` and `C ≥ 10t`: High confidence (0.2-0.5 decoy rate)
- Otherwise: Low confidence (0.01-0.1 decoy rate)

## API Endpoints

### Java Backend (Port 8080)

- `POST /api/v1/generate` - Generate SQUID tokens
- `POST /api/v1/verify` - Verify token authenticity  
- `GET /api/v1/public/root` - Get current Merkle root
- `GET /api/v1/testvectors` - Generate test vectors
- `GET /api/v1/health` - Health check

### Python IA Service (Port 5000)

- `POST /decide` - Get AI decisions for leaves
- `GET /model/info` - Model information
- `GET /test/vectors` - Generate test vectors
- `GET /health` - Health check

## Development

### Prerequisites

- Java 11+
- Python 3.8+
- Docker & Docker Compose
- liboqs library (for production PQC)

### Build

```bash
# Quick build
./scripts/build.sh

# Manual build
cd java-backend && ./mvnw.cmd clean package
cd python-ia && pip install -r requirements.txt
```

### Test

```bash
# Run all tests
./scripts/test.sh

# Verify determinism
./scripts/verify-determinism.sh

# Compare implementations
python scripts/compare-vectors.py test-vectors/java.json test-vectors/python.json
```
