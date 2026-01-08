# SQUID Model Training - Quick Start

This guide will get you training the SQUID AI model in minutes.

## 🚀 Quick Start (Automated)

### Option 1: One-Command Setup and Training

Run the automated setup script from the SQUID root directory:

```powershell
cd C:\Users\User\Desktop\SQUID
.\setup_and_train.ps1
```

This will:

1. ✅ Check Python installation
2. ✅ Create virtual environment
3. ✅ Install all dependencies
4. ✅ Verify installation
5. ✅ Train the model with default settings

### Option 2: Quick Test (Fast Training)

Test the training pipeline quickly:

```powershell
.\setup_and_train.ps1 -QuickTest
```

Uses only 1,000 samples and 10 epochs (~2-5 minutes).

### Option 3: Custom Training

```powershell
.\setup_and_train.ps1 -NumSamples 50000 -Epochs 200
```

---

## 📋 Manual Setup (Step-by-Step)

If you prefer manual control:

### 1. Navigate to Directory

```powershell
cd C:\Users\User\Desktop\SQUID\python-ia
```

### 2. Create Virtual Environment

```powershell
python -m venv venv
.\venv\Scripts\Activate.ps1
```

### 3. Install Dependencies

```powershell
pip install -r requirements.txt
```

### 4. Train Model

```powershell
python train.py
```

---

## 🎯 Training Commands

### Basic Training

```powershell
python train.py

**Result**: 10,000 samples, 100 epochs, ~15-30 minutes

### Quick Test

```powershell
python train.py --num-samples 1000 --epochs 10
```

**Result**: 1,000 samples, 10 epochs, ~2-5 minutes

### Production Training

```powershell
python train.py --num-samples 100000 --epochs 200 --batch-size 256
```

**Result**: 100,000 samples, 200 epochs, ~2-4 hours

### Custom Architecture

```powershell
python train.py --hidden-dims 256 128 64 --dropout 0.2
```

**Result**: Deeper network with 3 hidden layers

---

## 📊 What Happens During Training

1. **Data Generation**: Creates synthetic training data with realistic features
2. **Model Creation**: Builds neural network (default: 128→64 hidden layers)
3. **Training Loop**: Trains for specified epochs with validation
4. **Evaluation**: Tests on held-out test set
5. **Model Saving**: Saves trained model to `./models/squid_model.pth`

### Expected Output

```powershell
================================================================================
SQUID Model Training
================================================================================
Using device: cpu
Set random seed to 42
Generating 10000 synthetic training samples...
Generated 10000 samples
Label distribution: [6831 1823  896  450]
Dataset split: train=7000, val=1500, test=1500

Starting training...
Epoch [1/100] (8.2s) | Train Loss: 1.2341, Acc: 0.6234 | Val Loss: 1.1987, Acc: 0.6421, F1: 0.6389
Epoch [2/100] (7.9s) | Train Loss: 1.0234, Acc: 0.7012 | Val Loss: 0.9876, Acc: 0.7234, F1: 0.7198
...
Epoch [50/100] (8.1s) | Train Loss: 0.3456, Acc: 0.8923 | Val Loss: 0.3789, Acc: 0.8734, F1: 0.8712
Early stopping triggered after 65 epochs

Training completed in 542.3s

================================================================================
Final Test Results
================================================================================
Test Loss: 0.3812
Test Accuracy: 0.8698
Test Precision: 0.8645
Test Recall: 0.8698
Test F1 Score: 0.8671
```

---

## 📁 Output Files

After training, you'll find:

```Markdown
python-ia/
├── models/
│   ├── squid_model.pth       # Final trained model
│   ├── best_model.pth         # Best model during training
│   └── training_metadata.json # Training configuration & metrics
└── training.log               # Complete training log

```

---

## ⚙️ Common Options

| Option | Description | Default | Example |
|--------|-------------|---------|---------|
| `--num-samples` | Number of training samples | 10000 | `--num-samples 50000` |
| `--epochs` | Training epochs | 100 | `--epochs 200` |
| `--batch-size` | Batch size | 64 | `--batch-size 128` |
| `--lr` | Learning rate | 0.001 | `--lr 0.0001` |
| `--hidden-dims` | Hidden layer sizes | 128 64 | `--hidden-dims 256 128 64` |
| `--dropout` | Dropout rate | 0.1 | `--dropout 0.2` |
| `--no-cuda` | Force CPU training | False | `--no-cuda` |
| `--output-dir` | Output directory | ./models | `--output-dir ./my_models` |

---

## 🔍 Monitoring Training

### View Training Log (Real-time)

```powershell
Get-Content training.log -Wait -Tail 20
```

### Check Model Info

```powershell
python -c "from squid_model import SquidModel; m = SquidModel(); print(m.get_architecture_info())"
```

---

## ✅ Verify Installation

Before training, verify everything is installed:

```powershell
# Check Python
python --version

# Check PyTorch
python -c "import torch; print('PyTorch:', torch.__version__); print('CUDA:', torch.cuda.is_available())"

# Check SQUID modules
python -c "from squid_model import SquidModel; print('✓ All modules OK')"
```

---

## 🐛 Troubleshooting

### "ModuleNotFoundError: No module named 'torch'"

**Solution**: Install dependencies

```powershell
pip install -r requirements.txt
```

### "CUDA out of memory"

**Solution**: Use CPU or reduce batch size

```powershell
python train.py --no-cuda
# OR
python train.py --batch-size 32
```

### Training is very slow

**Solutions**:

- Reduce samples: `--num-samples 5000`
- Increase batch size: `--batch-size 128`
- Use GPU if available (remove `--no-cuda`)

### Accuracy is low

**Solutions**:

- Increase training data: `--num-samples 50000`
- Train longer: `--epochs 200`
- Adjust learning rate: `--lr 0.0001`

---

## 📖 Full Documentation

For detailed information, see: [TRAINING_GUIDE.md](../TRAINING_GUIDE.md)

Topics covered:

- Complete environment setup
- Data preparation (synthetic & real)
- Advanced training techniques
- Model evaluation & deployment
- Hyperparameter tuning
- Troubleshooting guide

---

## 🎓 Next Steps

After training:

1. **Review Results**: Check `training.log` and test metrics
2. **Test Model**: Run `python squid_model.py` to test predictions
3. **Deploy Model**: Copy `models/squid_model.pth` to production location
4. **Start Service**: Run `python app.py` to start the AI service
5. **Integrate**: Connect with Java backend via `/decide` endpoint

---

## 📞 Support

- Check troubleshooting section above
- Review full guide: `TRAINING_GUIDE.md`
- Check main README: `../README.md`
- Review training logs: `training.log`

---

**Last Updated**: 2025-01-03
