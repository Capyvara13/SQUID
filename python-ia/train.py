#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
SQUID+ Training Pipeline (Optimized Version)
-------------------------------------------
Treinamento refinado com:
- Normalização completa de features
- Rede neural mais profunda
- AdamW + OneCycleLR
- Label smoothing
- Gradient clipping
- Early stopping aprimorado
- Logs mais claros com progresso e métricas

Compatível com GPU AMD (DirectML/ROCm)
"""

import os
import time
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, TensorDataset, random_split
from sklearn.metrics import accuracy_score, f1_score
import numpy as np
from tqdm import tqdm

# ============================================================
# CONFIGURAÇÃO DO DISPOSITIVO (GPU AMD via DirectML ou ROCm)
# ============================================================
def get_device():
    try:
        if torch.backends.mps.is_available():
            return torch.device("mps")  # macOS
        elif torch.cuda.is_available():
            return torch.device("cuda")
        elif torch.backends.directml.is_available():  # GPU AMD via DirectML
            import torch_directml
            return torch_directml.device()
        elif torch.version.hip:  # ROCm (Linux)
            return torch.device("cuda")
        else:
            return torch.device("cpu")
    except Exception:
        return torch.device("cpu")

device = get_device()
print(f"[INFO] Usando dispositivo: {device}")

# ============================================================
# GERAÇÃO DE DADOS (SINTÉTICOS)
# ============================================================
def generate_synthetic_data(samples=10000, features=12, classes=3, seed=42):
    np.random.seed(seed)
    X = np.random.randn(samples, features)
    # Rótulos com base em uma combinação não-linear
    y = (np.tanh(X[:, 0] + 0.5 * X[:, 3] - X[:, 5]) > 0).astype(int)
    y = np.where(y == 0, np.random.randint(0, classes, size=samples), y % classes)
    return X, y

# ============================================================
# MODELO NEURAL APRIMORADO
# ============================================================
class SquidMLP(nn.Module):
    def __init__(self, input_dim, hidden_dims=[512, 256, 128], output_dim=3, dropout=0.25):
        super().__init__()
        layers = []
        dims = [input_dim] + hidden_dims
        for i in range(len(dims) - 1):
            layers += [
                nn.Linear(dims[i], dims[i + 1]),
                nn.BatchNorm1d(dims[i + 1]),
                nn.ReLU(),
                nn.Dropout(dropout)
            ]
        layers.append(nn.Linear(hidden_dims[-1], output_dim))
        self.net = nn.Sequential(*layers)

    def forward(self, x):
        return self.net(x)

# ============================================================
# TREINAMENTO PRINCIPAL
# ============================================================
def train_model(X, y, epochs=100, batch_size=32, patience=25, lr=3e-4):
    # Normalização completa
    X = (X - np.mean(X, axis=0)) / (np.std(X, axis=0) + 1e-8)
    
    X_tensor = torch.tensor(X, dtype=torch.float32)
    y_tensor = torch.tensor(y, dtype=torch.long)
    dataset = TensorDataset(X_tensor, y_tensor)

    train_size = int(0.8 * len(dataset))
    val_size = len(dataset) - train_size
    train_ds, val_ds = random_split(dataset, [train_size, val_size])
    train_loader = DataLoader(train_ds, batch_size=batch_size, shuffle=True)
    val_loader = DataLoader(val_ds, batch_size=batch_size)

    model = SquidMLP(input_dim=X.shape[1], output_dim=len(np.unique(y))).to(device)
    criterion = nn.CrossEntropyLoss(label_smoothing=0.1)
    optimizer = optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)
    scheduler = optim.lr_scheduler.OneCycleLR(
        optimizer, max_lr=lr, epochs=epochs, steps_per_epoch=len(train_loader)
    )

    best_val_loss = float("inf")
    patience_counter = 0

    for epoch in range(epochs):
        model.train()
        train_loss = 0
        for xb, yb in tqdm(train_loader, desc=f"Epoch {epoch+1}/{epochs}", leave=False):
            xb, yb = xb.to(device), yb.to(device)
            optimizer.zero_grad()
            preds = model(xb)
            loss = criterion(preds, yb)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            scheduler.step()
            train_loss += loss.item()

        # Validação
        model.eval()
        val_loss, y_true, y_pred = 0, [], []
        with torch.no_grad():
            for xb, yb in val_loader:
                xb, yb = xb.to(device), yb.to(device)
                preds = model(xb)
                loss = criterion(preds, yb)
                val_loss += loss.item()
                y_true.extend(yb.cpu().numpy())
                y_pred.extend(preds.argmax(1).cpu().numpy())

        val_acc = accuracy_score(y_true, y_pred)
        val_f1 = f1_score(y_true, y_pred, average="weighted")
        print(f"[Epoch {epoch+1:03d}] train_loss={train_loss/len(train_loader):.4f} | "
              f"val_loss={val_loss/len(val_loader):.4f} | acc={val_acc:.3f} | f1={val_f1:.3f}")

        # Early stopping
        if val_loss < best_val_loss:
            best_val_loss = val_loss
            patience_counter = 0
            torch.save(model.state_dict(), "squid_best_model.pt")
        else:
            patience_counter += 1
            if patience_counter >= patience:
                print("[EARLY STOP] Treinamento encerrado antecipadamente.")
                break

    print("[INFO] Treinamento concluído!")
    model.load_state_dict(torch.load("squid_best_model.pt"))
    return model

# ============================================================
# EXECUÇÃO PRINCIPAL
# ============================================================
if __name__ == "__main__":
    start = time.time()
    X, y = generate_synthetic_data(samples=10000)
    model = train_model(X, y, epochs=150, batch_size=32, patience=25, lr=3e-4)
    print(f"[INFO] Tempo total: {time.time() - start:.2f}s")