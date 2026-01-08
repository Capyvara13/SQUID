#!/bin/bash
# SQUID Build Script

set -e

echo "Building SQUID System..."

# Build Java backend
echo "Building Java backend..."
cd java-backend
./mvnw clean package -DskipTests
cd ..

# Build Python AI service
echo "Building Python AI service..."
cd python-ia
pip install -r requirements.txt
python -m py_compile *.py
cd ..

# Build Docker images
echo "Building Docker images..."
docker-compose build

echo "Build completed successfully!"
