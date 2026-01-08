#!/usr/bin/env python3
"""Fast training script for SQUID MLP model.

Designed to be quick for development: generates synthetic dataset
and trains for a few epochs. Saves model to `models/fast_model.pth`.
"""
import os
import time
import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim

from squid_model import SquidModel


def generate_synthetic_dataset(n_samples=1024, input_dim=13):
    X = np.random.randn(n_samples, input_dim).astype(np.float32)
    y = np.zeros(n_samples, dtype=np.int64)
    # simple rule-based labels for quick training
    for i in range(n_samples):
        if X[i, 3] > 0.7:
            y[i] = 1  # DECOY
        elif X[i, 2] > 0.6:
            y[i] = 3  # REASSIGN
        elif X[i, 0] > 0.5:
            y[i] = 2  # MUTATE
        else:
            y[i] = 0  # VALID
    return X, y


def train_fast(save_path='models/fast_model.pth', epochs=5, batch_size=64):
    os.makedirs(os.path.dirname(save_path), exist_ok=True)

    model_wrapper = SquidModel(input_dim=13, hidden_dims=[64, 32], output_dim=4, model_type='mlp', deterministic=True)
    # ensure underlying torch module
    model = model_wrapper.model

    device = torch.device('cpu')
    model.to(device)

    X, y = generate_synthetic_dataset(n_samples=1024, input_dim=13)
    X_tensor = torch.tensor(X)
    y_tensor = torch.tensor(y)

    criterion = nn.CrossEntropyLoss()
    optimizer = optim.Adam(model.parameters(), lr=1e-3)

    n_batches = max(1, X.shape[0] // batch_size)
    for epoch in range(epochs):
        perm = np.random.permutation(X.shape[0])
        epoch_loss = 0.0
        for b in range(n_batches):
            idx = perm[b*batch_size:(b+1)*batch_size]
            xb = X_tensor[idx]
            yb = y_tensor[idx]

            optimizer.zero_grad()
            preds = model(xb)
            loss = criterion(preds, yb)
            loss.backward()
            optimizer.step()

            epoch_loss += loss.item()

        print(f"Epoch {epoch+1}/{epochs} loss={epoch_loss / n_batches:.4f}")

    # Save model state
    torch.save({'model_state_dict': model.state_dict(), 'model_config': {'input_dim': model_wrapper.input_dim,'hidden_dims': model_wrapper.hidden_dims,'output_dim': model_wrapper.output_dim}}, save_path)
    print(f"Saved fast model to {save_path}")


if __name__ == '__main__':
    train_fast()
