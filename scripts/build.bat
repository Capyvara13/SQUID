@echo off
REM SQUID Build Script for Windows

echo Building SQUID System...

REM Build Java backend
echo Building Java backend...
cd ..
cd java-backend
.\mvnw.cmd clean package -DskipTests
cd ..

REM Build Python AI service
echo Building Python AI service...
cd python-ia
pip install -r requirements.txt
python -m py_compile *.py
cd ..

REM Build Docker images
echo Building Docker images...
docker-compose build

echo Build completed successfully!
