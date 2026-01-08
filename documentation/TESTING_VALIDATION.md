# SQUID Advanced Inspection Module - Testing & Validation Guide

## 🧪 Pre-Deployment Testing

### 1. Backend Service Validation

#### Check Java Backend
```bash
# Terminal 1 - Start Java backend
cd java-backend
mvn clean install
mvn spring-boot:run

# Terminal 2 - Test endpoints
curl http://localhost:8080/api/v1/health
```

**Expected Response:**
```json
{
  "status": "running",
  "service": "SQUID Core"
}
```

#### Check Python AI Service
```bash
# Terminal 3 - Start Python service
cd python-ia
python app.py

# Terminal 4 - Test endpoints
curl http://localhost:5000/health
```

**Expected Response:**
```json
{
  "status": "healthy",
  "service": "SQUID-AI"
}
```

---

### 2. Model Management Testing

#### Test 1: List Models
```bash
curl http://localhost:8080/api/v1/models/list
```

**Expected:** JSON array with at least one model (default 1.0.0)

#### Test 2: Get Active Model
```bash
curl http://localhost:8080/api/v1/models/active
```

**Expected:** ModelMetadata object with is_active = true

#### Test 3: Register New Model
```bash
curl -X POST http://localhost:8080/api/v1/models/register \
  -H "Content-Type: application/json" \
  -d '{
    "version": "1.5.0",
    "architecture": "PyTorch[13->256->128->64->4]",
    "description": "Test model",
    "metrics": {"accuracy": 0.95, "loss": 0.12, "f1_score": 0.94}
  }'
```

**Expected:** 201 Created with model ID and metadata

#### Test 4: Switch Model
```bash
curl -X POST http://localhost:8080/api/v1/models/switch \
  -H "Content-Type: application/json" \
  -d '{
    "version": "1.5.0",
    "reason": "Testing switch",
    "initiator": "test-user"
  }'
```

**Expected:** New active model returned

#### Test 5: Get Model History
```bash
curl http://localhost:8080/api/v1/models/history/all
```

**Expected:** Array of ModelHistoryEntry objects with REGISTER and SWITCH events

---

### 3. Encrypted Data Testing

#### Test 1: Preview Encrypted Data
```bash
# First, create base64 encoded test data
TEST_DATA=$(echo "sensitive-information" | base64)

curl -X POST http://localhost:8080/api/v1/encrypted/preview \
  -H "Content-Type: application/json" \
  -H "X-User: test-user" \
  -H "X-IP: 192.168.1.1" \
  -d "{
    \"encryptedData\": \"$TEST_DATA\",
    \"encryptionKey\": \"test-key\"
  }"
```

**Expected:** Session ID, data hash, and preview

#### Test 2: Verify Session Expiry
```bash
# Get the session ID from previous response
SESSION_ID="<from-previous-response>"

# Wait 300+ seconds, then try to access
curl http://localhost:8080/api/v1/encrypted/session/$SESSION_ID
```

**Expected:** After 5 minutes, should return "Session expired" error

#### Test 3: Upload Multiple Items
```bash
curl -X POST http://localhost:8080/api/v1/encrypted/upload \
  -H "Content-Type: application/json" \
  -H "X-User: test-user" \
  -d '{
    "dataItems": ["item1", "item2", "item3"],
    "encryptionKey": "test-key"
  }'
```

**Expected:** Upload ID, encrypted items, and hashes

#### Test 4: Check Audit Log
```bash
curl http://localhost:8080/api/v1/encrypted/audit/all
```

**Expected:** Array of AuditLogEntry objects with actions: PREVIEW, UPLOAD

#### Test 5: Get Stats
```bash
curl http://localhost:8080/api/v1/encrypted/stats
```

**Expected:** Statistics object with counts

---

### 4. Merkle Tree Testing

#### Test 1: Get Tree Status
```bash
curl http://localhost:8080/api/v1/merkle/status
```

**Expected:** Root hash, event count, initialization status

#### Test 2: Add Leaves
```bash
curl -X POST http://localhost:8080/api/v1/merkle/add-leaves \
  -H "Content-Type: application/json" \
  -d '{
    "leaves": ["leaf1", "leaf2", "leaf3"],
    "reason": "Test data insertion"
  }'
```

**Expected:** Previous root, new root, leaves count

#### Test 3: Update Leaves
```bash
curl -X PUT http://localhost:8080/api/v1/merkle/update-leaves \
  -H "Content-Type: application/json" \
  -d '{
    "updates": {
      "0": "updated-leaf-1",
      "1": "updated-leaf-2"
    },
    "reason": "Model retraining"
  }'
```

**Expected:** Root changed, update count returned

#### Test 4: Verify Integrity
```bash
curl -X POST http://localhost:8080/api/v1/merkle/verify
```

**Expected:** {"is_valid": true}

#### Test 5: Rotate Keys
```bash
curl -X POST http://localhost:8080/api/v1/merkle/rotate-keys \
  -H "Content-Type: application/json" \
  -d '{"reason": "Security rotation"}'
```

**Expected:** Root changed, all leaves affected

#### Test 6: Get History
```bash
curl http://localhost:8080/api/v1/merkle/history
```

**Expected:** Array of MerkleTreeTransitionEvent objects

---

## 🎯 Dashboard Integration Testing

### 1. Navigation Test
- [ ] Open http://localhost:3000
- [ ] Click on "Advanced Inspection" section in sidebar
- [ ] Should see 3 options: Model Manager, Encrypted Data, Dynamic Merkle
- [ ] Each option should load without errors

### 2. Model Manager Test
- [ ] Navigate to Model Manager
- [ ] Should see current active model (1.0.0) highlighted in green
- [ ] Metrics should display correctly (Accuracy, F1, Loss)
- [ ] Click "Switch to this model" button
- [ ] Should show confirmation
- [ ] History tab should show SWITCH event

### 3. Encrypted Data Viewer Test

**Preview Tab:**
- [ ] Enter sample data in "Encrypted Data" field
- [ ] Enter key in "Encryption Key" field
- [ ] Click "Preview Single Element"
- [ ] Should show session ID and preview
- [ ] Session should expire after 5 minutes

**Upload Tab:**
- [ ] Add 3 data items
- [ ] Enter encryption key
- [ ] Click "Upload 3 Items"
- [ ] Should show upload ID

**Audit Log Tab:**
- [ ] Should show all PREVIEW and UPLOAD actions
- [ ] Statistics should show counts
- [ ] Should be able to filter by data hash

### 4. Dynamic Merkle Tree Test
- [ ] Add 3 leaves with reason "Test insertion"
- [ ] Should show previous and new root
- [ ] Click "Verify Integrity" - should return valid
- [ ] Click "Rotate Keys" - should update root
- [ ] History should show events
- [ ] Audit trail should list all transitions

---

## ✅ Validation Checklist

### Code Quality
- [ ] No compilation errors in Java
- [ ] No syntax errors in Python
- [ ] No TypeScript errors in Dashboard
- [ ] All imports are correct
- [ ] No unused imports

### Functionality
- [ ] All endpoints return proper status codes
- [ ] Error handling works correctly
- [ ] Data is formatted as expected
- [ ] Timestamps are included
- [ ] Hashes are consistent

### Security
- [ ] Audit logs are created
- [ ] Session IDs are unique
- [ ] Expired sessions are cleaned
- [ ] User info is tracked
- [ ] No plaintext passwords in logs

### Performance
- [ ] Response times < 500ms
- [ ] No memory leaks on long operations
- [ ] Dashboard updates smoothly
- [ ] No API timeouts

### Integration
- [ ] Java backend communicates with Dashboard
- [ ] Python service endpoints accessible
- [ ] Docker compose starts all services
- [ ] Ports don't conflict
- [ ] CORS is properly configured

---

## 🐛 Troubleshooting

### Issue: Port 8080 already in use
```bash
# Find process using port 8080
lsof -i :8080

# Kill process
kill -9 <PID>
```

### Issue: Python service won't start
```bash
# Check Python version
python --version

# Install missing dependencies
pip install -r python-ia/requirements.txt

# Try with verbose logging
python -u python-ia/app.py
```

### Issue: Dashboard won't load components
```bash
# Check if npm dependencies are installed
cd dashboard
npm install

# Clear cache and rebuild
rm -rf node_modules package-lock.json
npm install
npm run dev
```

### Issue: CORS errors
```bash
# Check that @CrossOrigin annotation is present
# in Java controllers

# Verify CORS headers in Python:
from flask_cors import CORS
CORS(app)
```

### Issue: Data not updating in real-time
- [ ] Check if auto-refresh is enabled (checkbox in components)
- [ ] Verify API endpoints are responding
- [ ] Check browser console for JavaScript errors
- [ ] Clear browser cache (Ctrl+Shift+Delete)

---

## 📊 Performance Baseline

### Expected Response Times
| Operation | Target | Acceptable |
|-----------|--------|-----------|
| List Models | 50ms | <200ms |
| Add Leaves | 100ms | <500ms |
| Preview Data | 75ms | <300ms |
| Verify Tree | 150ms | <1000ms |
| Upload Items | 200ms | <2000ms |

### Resource Usage
- Java Backend: ~500MB heap
- Python Service: ~300MB
- Dashboard: ~150MB (per browser tab)

---

## 🔄 Regression Testing

After making changes, verify:

1. **Original Features Still Work:**
   - [ ] Dashboard loads
   - [ ] Test vectors generation works
   - [ ] Original visualization works

2. **New Features Work:**
   - [ ] Model Management endpoints
   - [ ] Encrypted data operations
   - [ ] Merkle Tree updates

3. **No Breaking Changes:**
   - [ ] Existing API contracts unchanged
   - [ ] Database schema compatible
   - [ ] Configuration format supported

---

## 📝 Test Report Template

```
TEST REPORT - Advanced Inspection Module
========================================

Date: YYYY-MM-DD
Tester: [Name]
Environment: Docker / Local / Production

RESULTS:
--------
Model Management: [ ] PASS [ ] FAIL [ ] PARTIAL
  - List Models: [ ]
  - Switch Model: [ ]
  - Register Model: [ ]
  - Get History: [ ]

Encrypted Data: [ ] PASS [ ] FAIL [ ] PARTIAL
  - Preview: [ ]
  - Upload: [ ]
  - Audit Log: [ ]
  - Session Expiry: [ ]

Merkle Tree: [ ] PASS [ ] FAIL [ ] PARTIAL
  - Add Leaves: [ ]
  - Update Leaves: [ ]
  - Verify: [ ]
  - Rotate Keys: [ ]

Dashboard: [ ] PASS [ ] FAIL [ ] PARTIAL
  - Navigation: [ ]
  - Component Load: [ ]
  - Data Display: [ ]
  - Real-time Update: [ ]

ISSUES FOUND:
-----------
1. [Description]
   Severity: High/Medium/Low
   Status: Open/In Progress/Closed

NOTES:
------
[Any observations]

SIGNED OFF:
-----------
Tester: _______________
Date: _________________
```

---

## 🎓 Learning Resources

For developers new to SQUID:
- Review `AGENTS.md` for architecture patterns
- Check `README.md` for project overview
- See `ADVANCED_INSPECTION_MODULE.md` for feature details
- Review source code comments for implementation details

---

**Testing Complete When All Checks Pass ✓**
