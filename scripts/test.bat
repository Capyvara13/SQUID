@echo off
REM SQUID Testing Script for Windows

echo Running SQUID Tests...

REM Test Java backend
echo Testing Java backend...
cd java-backend
call mvnw.cmd test
cd ..

REM Test Python AI service
echo Testing Python AI service...
cd python-ia
python test_vectors.py
cd ..

REM Integration tests
echo Running integration tests...
docker-compose up -d
timeout /t 30

REM Test endpoints
echo Testing Java backend health...
curl -f http://localhost:8080/api/v1/health

echo Testing Python AI service health...
curl -f http://localhost:5000/health

echo Testing test vector generation...
curl -f http://localhost:8080/api/v1/testvectors > test-vectors\java_output.json
curl -f http://localhost:5000/test/vectors > test-vectors\python_output.json

REM Cleanup
docker-compose down

echo All tests passed!
