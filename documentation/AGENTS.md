# SQUID Development Guide for AI Agents

## Quick Commands

### Build & Test

```bash
# Windows
scripts\build.bat
scripts\test.bat

# Linux/MacOS  
./scripts/build.sh
./scripts/test.sh
```

### Run Services

```bash
# Start all services
docker-compose up -d

# Check logs
docker-compose logs -f java-backend
docker-compose logs -f python-ia

# Stop services
docker-compose down
```

### Test Determinism

```bash
# Verify reproducible outputs
./scripts/verify-determinism.sh

# Compare Java vs Python
python scripts/compare-vectors.py test-vectors/java.json test-vectors/python.json
```

## Project Structure

```Java
SQUID/
├── java-backend/          # Spring Boot REST API
│   ├── src/main/java/com/squid/core/
│   │   ├── controller/    # REST endpoints
│   │   ├── service/       # Business logic
│   │   ├── crypto/        # HKDF, Merkle, JSON canonicalization
│   │   └── model/         # Request/response DTOs
│   ├── pom.xml           # Maven dependencies
│   └── Dockerfile
├── python-ia/            # PyTorch AI service
│   ├── app.py            # Flask application
│   ├── squid_formulas.py # SR/C calculations
│   ├── squid_model.py    # PyTorch model
│   ├── squid_utils.py    # Utilities
│   ├── requirements.txt
│   └── Dockerfile
├── scripts/              # Build and test scripts
├── test-vectors/         # Deterministic test data
└── docker-compose.yml
```

## Key Implementation Details

### Cryptographic Stack

- **HKDF**: RFC 5869 with HMAC-SHA256
- **Merkle Tree**: BLAKE2b-256 hashing
- **JSON Canonicalization**: RFC 8785 compatible
- **PQC Simulation**: Placeholder for liboqs integration

### Deterministic Operations

- All RNG operations use seeds derived from input
- HKDF derivation is reproducible across implementations
- AI model uses fixed seed for consistent decisions

### Formula Implementation

Located in `python-ia/squid_formulas.py`:

- `SuperRelationCalculator.calculate(params)`
- `CorrelationCalculator.calculate(params)`
- `PolicyCalculator.generate_actions()`

### Test Vectors

Three deterministic test vectors (`tv1`, `tv2`, `tv3`) with:

- Different branching parameters (b, m, t)
- Reproducible across Java and Python
- Bytewise identical outputs

## Common Development Tasks

### Adding New Features

1. Update Java service in `java-backend/src/main/java/com/squid/core/`
2. Update Python service in `python-ia/`
3. Add tests and verify determinism
4. Update API documentation

### Debugging Mismatches

1. Check `scripts/compare-vectors.py` output
2. Compare intermediate values (root_key, leaves, merkle_root)
3. Verify HKDF derivation paths match
4. Check JSON canonicalization

### Performance Tuning

1. Monitor `docker-compose logs` for timing
2. Adjust branching parameters (b, m, t) in test cases
3. Profile Java service with JProfiler
4. Profile Python service with cProfile

## Security Considerations

### Production Deployment

- Replace PQC simulation with liboqs
- Implement KMS integration (AWS KMS/HashiCorp Vault)
- Enable mTLS between services
- Set up proper key rotation procedures

### Code Review Checklist

- [ ] Deterministic operations verified
- [ ] No secrets in logs or responses
- [ ] Input validation implemented
- [ ] Error handling doesn't leak information
- [ ] Test vectors updated and verified

## Troubleshooting

### Services Won't Start

1. Check Docker is running
2. Verify ports 8080 and 5000 are available
3. Check `docker-compose logs` for errors
4. Ensure Java 11+ and Python 3.8+ installed

### Test Vector Mismatches

1. Run `./scripts/verify-determinism.sh`
2. Check seed derivation in both implementations
3. Verify HKDF info strings match exactly
4. Compare JSON canonicalization outputs

### Performance Issues

1. Increase Docker memory allocation
2. Reduce branching parameters for testing
3. Check for infinite loops in AI model
4. Monitor CPU/memory usage

## Extension Points

### New AI Models

- Implement in `squid_model.py`
- Support XGBoost, LightGBM, or custom PyTorch
- Maintain deterministic behavior

### Alternative PQC Algorithms

- Replace simulated PQC in `PQCService.java`
- Update signature verification logic
- Test with new key formats

### Additional Endpoints

- Follow REST conventions
- Add to both services if needed
- Update integration tests
