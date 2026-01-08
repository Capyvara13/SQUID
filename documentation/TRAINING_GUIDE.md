# SQUID Model Training Guide

Complete guide for setting up your environment and training the SQUID AI model.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Environment Setup](#environment-setup)
3. [Data Preparation](#data-preparation)
4. [Training the Model](#training-the-model)
5. [Monitoring Training](#monitoring-training)
6. [Model Evaluation](#model-evaluation)
7. [Deployment](#deployment)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### System Requirements

- **Operating System**: Windows 10/11, Linux, or macOS
- **Python**: 3.8 or higher
- **RAM**: Minimum 8GB (16GB recommended)
- **GPU**: Optional but recommended (CUDA-compatible for faster training)
- **Disk Space**: At least 2GB free space

### Required Software

- Python 3.8+
- pip (Python package manager)
- Git (optional, for version control)

---

## Environment Setup

### Step 1: Create a Virtual Environment

Creating a virtual environment isolates your project dependencies from the system Python.

**On Windows (PowerShell):**
```powershell
cd C:\Users\User\Desktop\SQUID\python-ia
python -m venv venv
.\venv\Scripts\Activate.ps1
```

**On Linux/macOS:**
```bash
cd /path/to/SQUID/python-ia
python3 -m venv venv
source venv/bin/activate
```

You should see `(venv)` at the beginning of your command prompt.

### Step 2: Upgrade pip

```powershell
python -m pip install --upgrade pip
```

### Step 3: Install Dependencies

Install all required Python packages:

```powershell
pip install -r requirements.txt
```

This will install:
- PyTorch (deep learning framework)
- NumPy & SciPy (numerical computing)
- scikit-learn (machine learning utilities)
- XGBoost & LightGBM (tree-based models)
- Flask (web framework for the API)
- Other utilities

### Step 4: Verify Installation

Test that PyTorch is installed correctly:

```powershell
python -c "import torch; print(f'PyTorch version: {torch.__version__}'); print(f'CUDA available: {torch.cuda.is_available()}')"
```

Expected output:
```
PyTorch version: 1.11.0 (or higher)
CUDA available: True/False
```

### Step 5: Verify SQUID Modules

Test that all SQUID modules can be imported:

```powershell
python -c "from squid_model import SquidModel; from squid_formulas import SuperRelationCalculator; print('All modules imported successfully!')"
```

---

## Data Preparation

The training script can work with either **synthetic data** (generated automatically) or **real data** from your system.

### Option A: Using Synthetic Data (Recommended for First Training)

No preparation needed! The training script will generate synthetic data automatically.

The synthetic data includes:
- **10,000 samples** by default (configurable)
- **Various parameter combinations** (branching factors, depths, etc.)
- **Realistic feature distributions** based on SQUID formulas
- **Balanced label distribution** (VALID, DECOY, MUTATE, REASSIGN)

### Option B: Using Real Data

If you have real data from your Java backend, prepare it in the following format:

1. **Feature Schema**: Each sample should have 11 features:
   - `depth`: Tree depth (int)
   - `index`: Leaf index (int)
   - `index_hash`: Hash of index (int)
   - `local_entropy`: Entropy value 0-8 (float)
   - `timestamp`: Unix timestamp in milliseconds (int)
   - `global_L`: Total leaves (int)
   - `global_b`: Branching factor (int)
   - `global_m`: Tree depth (int)
   - `global_t`: Leaf bit length (int)
   - `last_access_count`: Access count (int)
   - `leaf_hist_score`: Historical score 0-1 (float)

2. **Save as NumPy archive** (`.npz` file):

```python
import numpy as np

# Prepare your data
features = np.array([...])  # Shape: (N, 11)
labels = np.array([...])    # Shape: (N,) - values 0-3
sr_values = np.array([...]) # Shape: (N,)
c_values = np.array([...])  # Shape: (N,)

# Save to file
np.savez('training_data.npz', 
         features=features, 
         labels=labels,
         sr_values=sr_values,
         c_values=c_values)
```

---

## Training the Model

### Basic Training (Synthetic Data)

Start with the default configuration:

```powershell
python train.py
```

This will:
- Generate 10,000 synthetic samples
- Use default hyperparameters (128-64 hidden layers, 100 epochs)
- Train on CPU (or GPU if available)
- Save the model to `./models/squid_model.pth`

### Training with Custom Parameters

#### Adjust Number of Samples
```powershell
python train.py --num-samples 50000
```

#### Use Your Own Data
```powershell
python train.py --data-path path/to/training_data.npz
```

#### Customize Model Architecture
```powershell
# Deeper network with 3 hidden layers
python train.py --hidden-dims 256 128 64

# Smaller network (faster training)
python train.py --hidden-dims 64 32
```

#### Adjust Training Duration
```powershell
# More epochs (better accuracy, longer training)
python train.py --epochs 200

# Larger batch size (faster but needs more RAM)
python train.py --batch-size 128
```

#### Learning Rate Tuning
```powershell
# Higher learning rate (faster convergence, risk of instability)
python train.py --lr 0.01

# Lower learning rate (more stable, slower convergence)
python train.py --lr 0.0001
```

#### Force CPU Training
```powershell
python train.py --no-cuda
```

### Complete Training Example

Here's a comprehensive training command with custom settings:

```powershell
python train.py `
  --num-samples 50000 `
  --epochs 150 `
  --batch-size 128 `
  --hidden-dims 256 128 64 `
  --lr 0.001 `
  --dropout 0.2 `
  --patience 20 `
  --seed 42 `
  --output-dir ./models/experiment_001
```

---

## Monitoring Training

### Real-time Console Output

During training, you'll see output like:

```
================================================================================
SQUID Model Training
================================================================================
Using device: cuda
Set random seed to 42
Generating 10000 synthetic training samples...
Generated 10000 samples
Label distribution: [6831 1823  896  450]
Dataset split: train=7000, val=1500, test=1500

Starting training...
Epoch [1/100] (12.3s) | Train Loss: 1.2341, Acc: 0.6234 | Val Loss: 1.1987, Acc: 0.6421, F1: 0.6389
Epoch [2/100] (11.8s) | Train Loss: 1.0234, Acc: 0.7012 | Val Loss: 0.9876, Acc: 0.7234, F1: 0.7198
...
Saved best model to ./models/best_model.pth
```

### Key Metrics to Watch

1. **Train Loss**: Should decrease over time (lower is better)
2. **Train Accuracy**: Should increase over time (higher is better)
3. **Val Loss**: Should decrease; if it increases, you may be overfitting
4. **Val Accuracy**: Should increase and be close to train accuracy
5. **F1 Score**: Balanced metric considering precision and recall

### Training Log File

All training output is also saved to `training.log` in the current directory:

```powershell
# View the log in real-time (PowerShell)
Get-Content training.log -Wait -Tail 20

# View the entire log
cat training.log
```

### TensorBoard (Optional)

For advanced monitoring, you can add TensorBoard support:

```python
# Add to train.py (optional enhancement)
from torch.utils.tensorboard import SummaryWriter

writer = SummaryWriter('runs/squid_experiment')
# Log metrics during training
writer.add_scalar('Loss/train', train_loss, epoch)
writer.add_scalar('Loss/val', val_loss, epoch)
```

Then view in browser:
```powershell
tensorboard --logdir=runs
```

---

## Model Evaluation

### Automatic Evaluation

After training completes, the script automatically evaluates on the test set:

```
================================================================================
Final Test Results
================================================================================
Test Loss: 0.8234
Test Accuracy: 0.7891
Test Precision: 0.7845
Test Recall: 0.7891
Test F1 Score: 0.7867

Confusion Matrix:
[[850  45  23  12]
 [ 38 142  15   8]
 [ 21  12  98   7]
 [ 15   8   9  67]]
```

### Understanding the Confusion Matrix

The confusion matrix shows predictions vs. actual labels:

```
              Predicted
              VALID  DECOY  MUTATE  REASSIGN
Actual VALID    850    45     23       12
       DECOY     38   142     15        8
       MUTATE    21    12     98        7
       REASSIGN  15     8      9       67
```

- **Diagonal values** (850, 142, 98, 67): Correct predictions
- **Off-diagonal values**: Misclassifications

### Manual Model Testing

Test the trained model interactively:

```python
import torch
from squid_model import SquidModel

# Load model
model = SquidModel()
model.load_model('./models/squid_model.pth')

# Test features
test_features = [{
    'depth': 3, 'index': 0, 'index_hash': 0,
    'local_entropy': 7.5, 'timestamp': 1640995200000,
    'global_L': 64, 'global_b': 4, 'global_m': 3,
    'global_t': 128, 'last_access_count': 0,
    'leaf_hist_score': 0.5
}]

# Predict
actions = model.predict_actions(test_features, sr=2.5, c=15.0)
print(f"Predicted actions: {actions}")
```

---

## Deployment

### Step 1: Copy Trained Model

After training, copy the model to the deployment directory:

```powershell
# Windows
Copy-Item .\models\squid_model.pth C:\Users\User\Desktop\SQUID\models\

# Linux/macOS
cp ./models/squid_model.pth /path/to/SQUID/models/
```

### Step 2: Update Application Configuration

Set the model path in your environment or Docker configuration:

**For local development:**
```powershell
$env:MODEL_PATH = "C:\Users\User\Desktop\SQUID\models\squid_model.pth"
python app.py
```

**For Docker:**
Update `docker-compose.yml`:
```yaml
python-ia:
  environment:
    - MODEL_PATH=/app/models/squid_model.pth
  volumes:
    - ./models:/app/models
```

### Step 3: Test Deployment

Start the service and test:

```powershell
# Start the service
python app.py

# In another terminal, test the endpoint
curl -X POST http://localhost:5000/decide `
  -H "Content-Type: application/json" `
  -d '{
    "seed_model_hash": "test123",
    "params": {"b": 4, "m": 3, "t": 128},
    "features": [{"depth": 3, "index": 0, ...}]
  }'
```

### Step 4: Docker Deployment

Build and deploy with Docker:

```powershell
# Build containers
docker-compose up --build

# Verify model is loaded
docker-compose exec python-ia curl http://localhost:5000/model/info
```

---

## Troubleshooting

### Issue: "ModuleNotFoundError: No module named 'torch'"

**Solution**: Install PyTorch
```powershell
pip install torch
```

### Issue: "CUDA out of memory"

**Solutions**:
1. Reduce batch size: `--batch-size 32`
2. Use CPU: `--no-cuda`
3. Reduce model size: `--hidden-dims 64 32`

### Issue: "RuntimeError: DataLoader worker is killed"

**Solution**: Reduce number of workers
```powershell
python train.py --num-workers 0
```

### Issue: Training loss not decreasing

**Solutions**:
1. Lower learning rate: `--lr 0.0001`
2. Increase training samples: `--num-samples 50000`
3. Adjust model architecture: `--hidden-dims 256 128`

### Issue: Overfitting (val loss increases while train loss decreases)

**Solutions**:
1. Increase dropout: `--dropout 0.3`
2. Add weight decay: `--weight-decay 0.0001`
3. Reduce model size: `--hidden-dims 64 32`
4. Generate more training data: `--num-samples 50000`

### Issue: Model predictions are all the same class

**Solutions**:
1. Check label distribution in training data
2. Use class weighting in loss function
3. Generate more balanced synthetic data
4. Adjust label generation heuristics

### Issue: Training is very slow

**Solutions**:
1. Enable GPU: Remove `--no-cuda` flag
2. Increase batch size: `--batch-size 128`
3. Reduce data: `--num-samples 5000`
4. Use fewer workers: `--num-workers 2`

---

## Advanced Topics

### Hyperparameter Tuning

Use a grid search or random search to find optimal hyperparameters:

```powershell
# Example: Try different learning rates
foreach ($lr in 0.001, 0.0005, 0.0001) {
    python train.py --lr $lr --output-dir "./models/lr_$lr"
}
```

### Cross-Validation

For more robust evaluation, implement k-fold cross-validation in the training script.

### Model Versioning

Keep track of different model versions:

```powershell
python train.py --output-dir "./models/v1.0_$(Get-Date -Format 'yyyyMMdd')"
```

### Continuous Training

Retrain periodically with new data:

```python
# Load existing model
model.load_model('./models/squid_model.pth')

# Continue training with new data
# (Add this capability to train.py)
```

---

## Quick Reference

### Common Training Commands

```powershell
# Quick test (small dataset, few epochs)
python train.py --num-samples 1000 --epochs 10

# Standard training
python train.py

# Production training (large dataset, optimized)
python train.py --num-samples 100000 --epochs 200 --batch-size 256

# CPU-only training
python train.py --no-cuda

# Custom architecture
python train.py --hidden-dims 512 256 128 --dropout 0.2
```

### File Locations

- **Training script**: `python-ia/train.py`
- **Model definition**: `python-ia/squid_model.py`
- **Formulas**: `python-ia/squid_formulas.py`
- **Utilities**: `python-ia/squid_utils.py`
- **Saved models**: `models/squid_model.pth`
- **Training logs**: `training.log`
- **Checkpoints**: `models/best_model.pth`

---

## Next Steps

1. ✅ Complete environment setup
2. ✅ Run basic training with synthetic data
3. ✅ Evaluate model performance
4. 📊 Generate real data from Java backend
5. 🔄 Retrain with real data
6. 🚀 Deploy trained model
7. 📈 Monitor production performance
8. 🔧 Iterate and improve

---

## Support

For issues or questions:
- Check the troubleshooting section
- Review training logs: `training.log`
- Check model info: `python -c "from squid_model import SquidModel; m = SquidModel(); print(m.get_architecture_info())"`
- Refer to `README.md` for system overview

---

**Last Updated**: 2025-01-03
**Version**: 1.0
