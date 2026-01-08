#!/bin/bash
# SQUID Determinism Verification Script

set -e

echo "Verifying SQUID Determinism..."

# Start services
docker-compose up -d
sleep 30

# Generate test vectors multiple times
echo "Generating test vectors (run 1)..."
curl -s http://localhost:8080/api/v1/testvectors > test-vectors/run1_java.json
curl -s http://localhost:5000/test/vectors > test-vectors/run1_python.json

sleep 5

echo "Generating test vectors (run 2)..."
curl -s http://localhost:8080/api/v1/testvectors > test-vectors/run2_java.json
curl -s http://localhost:5000/test/vectors > test-vectors/run2_python.json

# Compare outputs
echo "Comparing Java outputs..."
if cmp -s test-vectors/run1_java.json test-vectors/run2_java.json; then
    echo "✓ Java outputs are deterministic"
else
    echo "✗ Java outputs differ between runs"
    exit 1
fi

echo "Comparing Python outputs..."
if cmp -s test-vectors/run1_python.json test-vectors/run2_python.json; then
    echo "✓ Python outputs are deterministic"
else
    echo "✗ Python outputs differ between runs"
    exit 1
fi

# Cross-language comparison
echo "Comparing Java vs Python outputs..."
python scripts/compare-vectors.py test-vectors/run1_java.json test-vectors/run1_python.json

# Cleanup
docker-compose down

echo "Determinism verification completed successfully!"
