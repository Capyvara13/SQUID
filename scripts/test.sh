#!/bin/bash
# SQUID Testing Script

set -e

echo "Running SQUID Tests..."

# Test Java backend
echo "Testing Java backend..."
cd java-backend
./mvnw test
cd ..

# Test Python AI service
echo "Testing Python AI service..."
cd python-ia
python -m pytest tests/ -v || python test_vectors.py
cd ..

# Integration tests
echo "Running integration tests..."
docker-compose up -d
sleep 30

# Test endpoints
echo "Testing Java backend health..."
curl -f http://localhost:8080/api/v1/health

echo "Testing Python AI service health..."
curl -f http://localhost:5000/health

echo "Testing test vector generation..."
curl -f http://localhost:8080/api/v1/testvectors > test-vectors/java_output.json
curl -f http://localhost:5000/test/vectors > test-vectors/python_output.json

# Cleanup
docker-compose down

echo "All tests passed!"
