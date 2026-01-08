#!/usr/bin/env python3
"""
SQUID Model - PyTorch implementation of the AI decision model
Supports both deterministic XGBoost and interpretable MLP approaches
"""

import os
import json
import hashlib
import pickle
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from typing import List, Dict, Any, Tuple
import xgboost as xgb
from sklearn.ensemble import GradientBoostingClassifier
import logging

from squid_formulas import PolicyCalculator

logger = logging.getLogger(__name__)


class SquidMLP(nn.Module):
    """
    Lightweight MLP for SQUID decision making
    Architecture: Input -> Hidden[128] -> Hidden[64] -> Output[4]
    """
    
    def __init__(self, input_dim: int = 11, hidden_dims: List[int] = [128, 64], 
                 output_dim: int = 4, dropout_rate: float = 0.1):
        super(SquidMLP, self).__init__()
        
        self.input_dim = input_dim
        self.hidden_dims = hidden_dims
        self.output_dim = output_dim
        
        # Build layers
        layers = []
        prev_dim = input_dim
        
        for hidden_dim in hidden_dims:
            layers.extend([
                nn.Linear(prev_dim, hidden_dim),
                nn.ReLU(),
                nn.Dropout(dropout_rate)
            ])
            prev_dim = hidden_dim
        
        # Output layer
        layers.append(nn.Linear(prev_dim, output_dim))
        
        self.network = nn.Sequential(*layers)
        
        # Initialize weights deterministically if needed
        self._init_weights()
    
    def _init_weights(self):
        """Initialize weights with Xavier uniform"""
        for module in self.modules():
            if isinstance(module, nn.Linear):
                nn.init.xavier_uniform_(module.weight)
                if module.bias is not None:
                    nn.init.zeros_(module.bias)
    
    def forward(self, x):
        """Forward pass through network"""
        logits = self.network(x)
        return F.softmax(logits, dim=-1)


class SquidModel:
    """
    Main SQUID model class supporting multiple backends
    """
    
    def __init__(self, input_dim: int = 11, hidden_dims: List[int] = [128, 64], 
                 output_dim: int = 4, model_type: str = "mlp", deterministic: bool = True):
        self.input_dim = input_dim
        self.hidden_dims = hidden_dims
        self.output_dim = output_dim
        self.model_type = model_type
        self.deterministic = deterministic
        
        # Action mapping
        self.action_map = {0: "VALID", 1: "DECOY", 2: "MUTATE", 3: "REASSIGN"}
        
        # Policy calculator for fallback
        self.policy_calc = PolicyCalculator()
        
        # Initialize model
        self.model = None
        self.model_hash = None
        self._init_model()
    
    def _init_model(self):
        """Initialize the underlying model"""
        if self.deterministic:
            torch.manual_seed(42)
            np.random.seed(42)
        
        if self.model_type == "mlp":
            self.model = SquidMLP(
                input_dim=self.input_dim,
                hidden_dims=self.hidden_dims,
                output_dim=self.output_dim
            )
        elif self.model_type == "xgboost":
            self.model = xgb.XGBClassifier(
                n_estimators=100,
                max_depth=6,
                learning_rate=0.1,
                random_state=42 if self.deterministic else None,
                objective='multi:softprob',
                num_class=self.output_dim
            )
        elif self.model_type == "gbdt":
            self.model = GradientBoostingClassifier(
                n_estimators=100,
                max_depth=6,
                learning_rate=0.1,
                random_state=42 if self.deterministic else None
            )
        else:
            raise ValueError(f"Unsupported model type: {self.model_type}")
        
        # Calculate model hash
        self._update_model_hash()
    
    def _update_model_hash(self):
        """Update model hash based on parameters"""
        if self.model_type == "mlp":
            # Hash based on model state dict
            model_state = str(self.model.state_dict())
            self.model_hash = hashlib.sha256(model_state.encode()).hexdigest()[:16]
        else:
            # Hash based on model parameters
            model_params = {
                'type': self.model_type,
                'deterministic': self.deterministic,
                'input_dim': self.input_dim,
                'output_dim': self.output_dim
            }
            param_str = json.dumps(model_params, sort_keys=True)
            self.model_hash = hashlib.sha256(param_str.encode()).hexdigest()[:16]
    
    def predict_actions(self, features: List[Dict[str, Any]], sr: float, c: float) -> List[str]:
        """
        Predict actions for given features using the model
        Falls back to deterministic policy if model prediction fails
        """
        try:
            if len(features) == 0:
                return []
            
            # Convert features to model input
            feature_array = self._features_to_array(features)
            
            # Get model predictions
            if self.model_type == "mlp":
                predictions = self._predict_mlp(feature_array, sr, c)
            else:
                predictions = self._predict_tree(feature_array, sr, c)
            
            # Convert predictions to actions
            actions = [self.action_map[pred] for pred in predictions]
            
            # Apply policy constraints
            actions = self._apply_policy_constraints(actions, sr, c)
            
            return actions
            
        except Exception as e:
            logger.warning(f"Model prediction failed: {e}, falling back to policy")
            return self._fallback_policy(features, sr, c)
    
    def _features_to_array(self, features: List[Dict[str, Any]]) -> np.ndarray:
        """Convert feature dictionaries to numpy array"""
        feature_names = [
            'depth', 'index', 'index_hash', 'local_entropy', 'timestamp',
            'global_L', 'global_b', 'global_m', 'global_t',
            'last_access_count', 'leaf_hist_score'
        ]
        
        feature_matrix = []
        for feature_dict in features:
            feature_row = []
            for name in feature_names:
                value = feature_dict.get(name, 0.0)
                # Normalize certain features
                if name == 'timestamp':
                    value = (value - 1640995200000) / 86400000  # Days since epoch
                elif name == 'index_hash':
                    value = value / 1000.0
                elif name == 'local_entropy':
                    value = value / 8.0  # Normalize to [0, 1] range
                
                feature_row.append(float(value))
            
            feature_matrix.append(feature_row)
        
        return np.array(feature_matrix, dtype=np.float32)
    
    def _predict_mlp(self, features: np.ndarray, sr: float, c: float) -> List[int]:
        """Predict using MLP model"""
        self.model.eval()
        
        with torch.no_grad():
            # Convert to tensor
            input_tensor = torch.tensor(features, dtype=torch.float32)
            
            # Add global context (SR, C)
            batch_size = input_tensor.shape[0]
            global_context = torch.tensor([[sr, c]], dtype=torch.float32).repeat(batch_size, 1)
            
            # Concatenate features with global context
            input_with_context = torch.cat([input_tensor, global_context], dim=1)
            
            # Handle dimension mismatch
            if input_with_context.shape[1] != self.input_dim:
                # Pad or truncate to match expected input dimension
                if input_with_context.shape[1] < self.input_dim:
                    padding = torch.zeros(batch_size, self.input_dim - input_with_context.shape[1])
                    input_with_context = torch.cat([input_with_context, padding], dim=1)
                else:
                    input_with_context = input_with_context[:, :self.input_dim]
            
            # Get predictions
            probabilities = self.model(input_with_context)
            predictions = torch.argmax(probabilities, dim=1)
            
            return predictions.numpy().tolist()
    
    def _predict_tree(self, features: np.ndarray, sr: float, c: float) -> List[int]:
        """Predict using tree-based model (XGBoost/GBDT)"""
        # Add global context to features
        batch_size = features.shape[0]
        global_context = np.array([[sr, c]] * batch_size)
        features_with_context = np.concatenate([features, global_context], axis=1)
        
        # Ensure model is trained with dummy data if not already trained
        if not hasattr(self.model, 'classes_'):
            self._train_dummy_model(features_with_context.shape[1])
        
        try:
            if self.model_type == "xgboost":
                probabilities = self.model.predict_proba(features_with_context)
                predictions = np.argmax(probabilities, axis=1)
            else:  # GBDT
                predictions = self.model.predict(features_with_context)
            
            return predictions.tolist()
        except Exception as e:
            logger.warning(f"Tree prediction failed: {e}")
            # Fallback to simple heuristic
            return [0] * batch_size  # All VALID
    
    def _train_dummy_model(self, feature_dim: int):
        """Train model with dummy data for inference"""
        logger.info("Training dummy model for inference...")
        
        # Generate dummy training data
        n_samples = 1000
        X_dummy = np.random.random((n_samples, feature_dim))
        
        # Generate dummy labels based on simple rules
        y_dummy = []
        for i in range(n_samples):
            if X_dummy[i, 0] > 0.8:  # High depth -> more likely DECOY
                label = 1
            elif X_dummy[i, 1] > 0.9:  # High index -> REASSIGN
                label = 3
            elif X_dummy[i, 2] > 0.7:  # High entropy -> MUTATE
                label = 2
            else:
                label = 0  # VALID
            y_dummy.append(label)
        
        # Train the model
        self.model.fit(X_dummy, y_dummy)
        logger.info("Dummy model training completed")
    
    def _apply_policy_constraints(self, actions: List[str], sr: float, c: float) -> List[str]:
        """Apply policy constraints to model predictions with intelligent blending"""
        total_actions = len(actions)
        if total_actions == 0:
            return actions
        
        # Calculate expected decoy rate from policy
        expected_decoy_rate = self.policy_calc.calculate_decoy_rate(sr, c)
        
        # Count current decoy rate in predictions
        decoy_count = actions.count("DECOY")
        current_decoy_rate = decoy_count / total_actions if total_actions > 0 else 0
        
        # Only apply constraint if deviation is too large (> 20% deviation from expected)
        rate_deviation = abs(current_decoy_rate - expected_decoy_rate) / (expected_decoy_rate + 0.01)
        
        if rate_deviation > 0.2:  # More lenient threshold
            # Get policy actions for blending
            policy_actions = self.policy_calc.generate_actions(total_actions, sr, c, seed=42)
            
            # Blend model predictions with policy (80% model, 20% policy)
            # This maintains model's decisions while correcting extreme deviations
            blended_actions = []
            policy_indices = [i for i, a in enumerate(policy_actions) if a == "DECOY"]
            model_indices = [i for i, a in enumerate(actions) if a == "DECOY"]
            
            # Calculate how many policy decoys to use
            num_policy_decoys = int(total_actions * expected_decoy_rate * 0.2)
            
            # Start with model actions
            blended_actions = actions.copy()
            
            # Replace some low-confidence predictions with policy recommendations
            for i in range(min(num_policy_decoys, len(policy_indices))):
                idx = policy_indices[i]
                if idx < len(blended_actions):
                    blended_actions[idx] = policy_actions[idx]
            
            return blended_actions
        
        return actions
    
    def _fallback_policy(self, features: List[Dict[str, Any]], sr: float, c: float) -> List[str]:
        """Fallback to deterministic policy"""
        return self.policy_calc.generate_actions(len(features), sr, c, seed=42)
    
    def save_model(self, path: str):
        """Save model to file"""
        os.makedirs(os.path.dirname(path), exist_ok=True)
        
        if self.model_type == "mlp":
            torch.save({
                'model_state_dict': self.model.state_dict(),
                'model_config': {
                    'input_dim': self.input_dim,
                    'hidden_dims': self.hidden_dims,
                    'output_dim': self.output_dim,
                    'model_type': self.model_type
                }
            }, path)
        else:
            with open(path, 'wb') as f:
                pickle.dump({
                    'model': self.model,
                    'model_config': {
                        'input_dim': self.input_dim,
                        'output_dim': self.output_dim,
                        'model_type': self.model_type
                    }
                }, f)
        
        logger.info(f"Model saved to {path}")
    
    def load_model(self, path: str):
        """Load model from file"""
        if self.model_type == "mlp":
            checkpoint = torch.load(path, map_location='cpu')
            config = checkpoint['model_config']
            
            # Recreate model with saved config
            self.model = SquidMLP(
                input_dim=config['input_dim'],
                hidden_dims=config.get('hidden_dims', [128, 64]),
                output_dim=config['output_dim']
            )
            self.model.load_state_dict(checkpoint['model_state_dict'])
            # Ensure SquidModel metadata matches the loaded model config
            self.input_dim = int(config.get('input_dim', self.input_dim))
            self.hidden_dims = config.get('hidden_dims', self.hidden_dims)
            self.output_dim = int(config.get('output_dim', self.output_dim))
        else:
            with open(path, 'rb') as f:
                data = pickle.load(f)
                self.model = data['model']
                # Update metadata for non-MLP models if provided
                model_config = data.get('model_config', {})
                if model_config:
                    self.input_dim = int(model_config.get('input_dim', self.input_dim))
                    self.output_dim = int(model_config.get('output_dim', self.output_dim))
        
        self._update_model_hash()
        logger.info(f"Model loaded from {path}")
    
    def get_model_hash(self) -> str:
        """Get current model hash"""
        return self.model_hash
    
    def get_parameter_count(self) -> int:
        """Get number of model parameters"""
        if self.model_type == "mlp":
            return sum(p.numel() for p in self.model.parameters())
        else:
            return getattr(self.model, 'n_estimators', 100)
    
    def get_architecture_info(self) -> Dict[str, Any]:
        """Get model architecture information"""
        return {
            'type': self.model_type,
            'input_dim': self.input_dim,
            'output_dim': self.output_dim,
            'hidden_dims': self.hidden_dims if self.model_type == "mlp" else None,
            'deterministic': self.deterministic
        }


# Utility function for model training (for future use)
def train_squid_model(training_data: List[Dict], model_type: str = "mlp") -> SquidModel:
    """
    Train SQUID model on simulation data
    This would be used in a full training pipeline
    """
    logger.info(f"Training {model_type} model on {len(training_data)} samples")
    
    # Initialize model
    model = SquidModel(model_type=model_type, deterministic=True)
    
    # In a real implementation, this would:
    # 1. Convert training_data to features and labels
    # 2. Split into train/validation sets
    # 3. Train the model with proper loss function
    # 4. Validate and tune hyperparameters
    # 5. Return trained model
    
    logger.info("Model training completed (placeholder)")
    return model


if __name__ == "__main__":
    # Test model functionality
    print("Testing SQUID Model...")
    
    # Create test model
    model = SquidModel(model_type="mlp", deterministic=True)
    
    # Test features
    test_features = [
        {
            "depth": 3, "index": 0, "index_hash": 0, "local_entropy": 7.5,
            "timestamp": 1640995200000, "global_L": 64, "global_b": 4,
            "global_m": 3, "global_t": 128, "last_access_count": 0,
            "leaf_hist_score": 0.5
        },
        {
            "depth": 3, "index": 1, "index_hash": 1, "local_entropy": 6.8,
            "timestamp": 1640995200000, "global_L": 64, "global_b": 4,
            "global_m": 3, "global_t": 128, "last_access_count": 1,
            "leaf_hist_score": 0.3
        }
    ]
    
    # Test prediction
    actions = model.predict_actions(test_features, sr=2.5, c=15.0)
    print(f"Predicted actions: {actions}")
    print(f"Model hash: {model.get_model_hash()}")
    print(f"Parameters: {model.get_parameter_count()}")
    
    print("Model tests completed.")
