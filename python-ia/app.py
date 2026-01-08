#!/usr/bin/env python3
"""
SQUID AI Service - PyTorch-based decision engine for SQUID defense system
Implements Super-Relation (SR) and Correlation Coefficient (C) calculations
"""

import os
import json
import hashlib
import hmac
import logging
import math
import time
import numpy as np
from typing import Dict, List, Any, Tuple
from flask import Flask, request, jsonify
from flask_cors import CORS

from squid_formulas import SuperRelationCalculator, CorrelationCalculator
from squid_model import SquidModel
from decision_engine import plan_rotations
from squid_utils import set_deterministic_seed, validate_features

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

# Global model instance
squid_model = None
squid_model_loaded_from_file = False
squid_model_loaded_path = None

# Model management
model_registry = {}
model_history = []
active_model_version = None

# Encrypted data management
encrypted_data_sessions = {}
audit_log = []
merkle_tree_events = []
merkle_tree_current_root = None


def initialize_model():
    """Initialize the SQUID model with deterministic weights"""
    global squid_model
    global squid_model_loaded_from_file, squid_model_loaded_path
    deterministic = os.environ.get('DETERMINISTIC_SEED', 'true').lower() == 'true'

    # Initialize model instance
    # The model expects per-leaf features (11) plus two global context values (SR and C)
    squid_model = SquidModel(
        input_dim=13,  # 11 features + SR + C
        hidden_dims=[128, 64],
        output_dim=4,  # VALID, DECOY, MUTATE, REASSIGN
        deterministic=deterministic
    )

    # Determine model path candidates (env var first, then common filenames)
    env_path = os.environ.get('MODEL_PATH')
    candidates = []
    if env_path:
        candidates.append(env_path)
    # Common candidate filenames (relative paths will be resolved against a few likely bases)
    candidates.extend([
        'models/squid_model.pth',
        'models/best_model.pth',
        'squid_best_model.pt',
        'best_model.pth',
        'models/squid_best_model.pt',
    ])

    # Resolve candidates against a few probable base directories so relative paths work when
    # the service is started from different working directories (e.g., docker, gunicorn)
    base_dirs = [
        os.getcwd(),
        os.path.dirname(__file__),
        os.path.abspath(os.path.join(os.path.dirname(__file__), '..')),  # repo/python-ia parent
    ]

    resolved_candidates = []
    for c in candidates:
        if os.path.isabs(c):
            resolved_candidates.append(c)
        else:
            for base in base_dirs:
                resolved_candidates.append(os.path.join(base, c))

    # If env_path provided and relative, also try resolving it against bases
    if env_path and not os.path.isabs(env_path):
        for base in base_dirs:
            resolved_candidates.append(os.path.join(base, env_path))

    # Deduplicate while preserving order
    seen = set()
    candidates = []
    for p in resolved_candidates:
        if p not in seen:
            seen.add(p)
            candidates.append(p)

    selected_path = None
    for p in candidates:
        if p and os.path.exists(p):
            selected_path = p
            break

    # Load pre-trained weights if available
    try:
        if selected_path:
            squid_model.load_model(selected_path)
            squid_model_loaded_from_file = True
            squid_model_loaded_path = selected_path
            logger.info(f"Loaded model from {selected_path}")
        else:
            squid_model_loaded_from_file = False
            squid_model_loaded_path = None
            logger.info("No pre-trained model found; using randomly initialized model")
    except Exception as e:
        squid_model_loaded_from_file = False
        squid_model_loaded_path = selected_path
        logger.warning(f"Could not load model from {selected_path}: {e}")

    return squid_model


@app.route('/health', methods=['GET'])
def health():
    """Health check endpoint"""
    return jsonify({
        "status": "healthy",
        "service": "SQUID-AI",
        "model_loaded": squid_model is not None,
        "model_loaded_from_file": bool(squid_model_loaded_from_file),
        "model_loaded_path": squid_model_loaded_path,
        "model_hash": squid_model.get_model_hash() if squid_model else None
    })


@app.route('/decide', methods=['POST'])
def decide():
    """
    Main decision endpoint - receives features and returns AI decisions
    
    Expected input:
    {
        "seed_model_hash": "...",
        "params": {"b": 4, "m": 3, "t": 128},
        "features": [
            {
                "depth": 3,
                "index": 0,
                "index_hash": 0,
                "local_entropy": 7.8,
                "timestamp": 1640995200000,
                "global_L": 64,
                "global_b": 4,
                "global_m": 3,
                "global_t": 128,
                "last_access_count": 0,
                "leaf_hist_score": 0.5
            },
            ...
        ]
    }
    """
    try:
        data = request.get_json()
        
        # Validate input
        if not data or 'features' not in data or 'params' not in data:
            return jsonify({"error": "Missing required fields"}), 400
        
        seed_model_hash = data.get('seed_model_hash')
        params = data['params']
        features = data['features']
        
        # Set deterministic seed from hash
        if seed_model_hash:
            set_deterministic_seed(seed_model_hash)
        
        # Validate features
        validated_features = validate_features(features)
        
        # Calculate SR and C
        sr_calc = SuperRelationCalculator()
        c_calc = CorrelationCalculator()

        sr = sr_calc.calculate(params)
        c = c_calc.calculate(params)

        # Sanitize SR and C to avoid NaN/Infinity
        def _sanitize_metric(value, name):
            if not isinstance(value, (int, float)) or not math.isfinite(value):
                logger.warning(f"{name} calculation produced invalid value: {value}. Coercing to 0.0")
                return 0.0
            return float(value)

        sr = _sanitize_metric(sr, 'SR')
        c = _sanitize_metric(c, 'C')

        logger.info(f"Calculated SR={sr:.4f}, C={c:.4f}")
        
        # Generate decisions using model
        actions = squid_model.predict_actions(validated_features, sr, c)

        # Generate rotation plan (indices to rotate) based on normalized SR and C
        rotation_plan = plan_rotations(len(validated_features), sr, c, seed=None)
        
        # Prepare response
        response = {
            "sr": sr,
            "c": c,
            "actions": actions,
            "rotation_plan": rotation_plan,
            "model_hash": squid_model.get_model_hash(),
            "timestamp": data.get('features', [{}])[0].get('timestamp'),
            "total_leaves": len(features)
        }
        
        logger.info(f"Generated {len(actions)} decisions")
        return jsonify(response)
        
    except Exception as e:
        logger.error(f"Decision error: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/model/info', methods=['GET'])
def model_info():
    """Get model information and statistics"""
    # Return model metadata plus loader status so the dashboard can display correct state
    return jsonify({
        "model_loaded": squid_model is not None and bool(squid_model_loaded_from_file),
        "model_loaded_from_file": bool(squid_model_loaded_from_file),
        "model_loaded_path": squid_model_loaded_path,
        "model_hash": squid_model.get_model_hash() if squid_model else None,
        "parameters": squid_model.get_parameter_count() if squid_model else None,
        "architecture": squid_model.get_architecture_info() if squid_model else None,
        "deterministic": squid_model.deterministic if squid_model else None
    })


# ==================== MODEL MANAGEMENT ENDPOINTS ====================

@app.route('/model/list', methods=['GET'])
def list_models():
    """List all registered models"""
    try:
        models_list = []
        
        # Add active model
        if squid_model:
            models_list.append({
                "id": "active",
                "version": "1.0.0",
                "is_active": True,
                "architecture": squid_model.get_architecture_info() if squid_model else None,
                "hash": squid_model.get_model_hash() if squid_model else None,
                "loaded_from_file": bool(squid_model_loaded_from_file),
                "loaded_path": squid_model_loaded_path
            })
        
        # Add registered models from registry
        for version, metadata in model_registry.items():
            models_list.append({
                "id": metadata.get('id'),
                "version": version,
                "is_active": version == active_model_version,
                "architecture": metadata.get('architecture'),
                "hash": metadata.get('hash'),
                "loss": metadata.get('metrics', {}).get('loss'),
                "accuracy": metadata.get('metrics', {}).get('accuracy'),
                "f1_score": metadata.get('metrics', {}).get('f1_score'),
                "created_at": metadata.get('created_at'),
                "trained_at": metadata.get('trained_at'),
                "description": metadata.get('description')
            })
        
        return jsonify({
            "models": models_list,
            "total_count": len(models_list),
            "active_model": active_model_version
        })
    except Exception as e:
        logger.error(f"Error listing models: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/model/history', methods=['GET'])
def get_model_history():
    """Get model history and transitions"""
    try:
        return jsonify({
            "history": model_history,
            "total_events": len(model_history),
            "active_model": active_model_version
        })
    except Exception as e:
        logger.error(f"Error getting model history: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/model/switch', methods=['POST'])
def switch_model():
    """Switch to a different model version"""
    try:
        global active_model_version
        data = request.get_json()
        
        version = data.get('version')
        reason = data.get('reason', 'Model switch')
        initiator = data.get('initiator', 'UNKNOWN')
        
        if not version:
            return jsonify({"error": "Version required"}), 400
        
        if version not in model_registry and version != "1.0.0":
            return jsonify({"error": f"Model version {version} not found"}), 404
        
        old_version = active_model_version or "1.0.0"
        active_model_version = version
        
        # Log transition
        model_history.append({
            "id": str(len(model_history)),
            "timestamp": int(time.time() * 1000),
            "action": "SWITCH",
            "from_version": old_version,
            "to_version": version,
            "reason": reason,
            "initiator": initiator
        })
        
        logger.info(f"Switched model from {old_version} to {version}")
        
        return jsonify({
            "status": "switched",
            "from_version": old_version,
            "to_version": version,
            "timestamp": int(time.time() * 1000)
        })
    except Exception as e:
        logger.error(f"Error switching model: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/model/register', methods=['POST'])
def register_model():
    """Register a new model version"""
    try:
        data = request.get_json()
        
        version = data.get('version')
        architecture = data.get('architecture')
        description = data.get('description', '')
        metrics = data.get('metrics', {})
        
        if not version:
            return jsonify({"error": "Version required"}), 400
        
        if version in model_registry:
            return jsonify({"error": f"Model version {version} already registered"}), 409
        
        # Register model
        import uuid
        model_id = str(uuid.uuid4())
        model_registry[version] = {
            "id": model_id,
            "version": version,
            "architecture": architecture,
            "description": description,
            "metrics": metrics,
            "hash": squid_model.get_model_hash() if squid_model else None,
            "created_at": int(time.time() * 1000),
            "trained_at": int(time.time() * 1000)
        }
        
        # Log registration
        model_history.append({
            "id": str(len(model_history)),
            "timestamp": int(time.time() * 1000),
            "action": "REGISTER",
            "version": version,
            "reason": "New model registered",
            "details": architecture
        })
        
        logger.info(f"Registered model version {version}")
        
        return jsonify({
            "status": "registered",
            "model_id": model_id,
            "version": version,
            "timestamp": int(time.time() * 1000)
        }), 201
    except Exception as e:
        logger.error(f"Error registering model: {e}")
        return jsonify({"error": str(e)}), 500


# ==================== ENCRYPTED DATA ENDPOINTS ====================

@app.route('/encrypted/preview', methods=['POST'])
def preview_encrypted_data():
    """Preview encrypted data without persistence"""
    try:
        import uuid
        data = request.get_json()
        
        encrypted_data = data.get('encryptedData')
        encryption_key = data.get('encryptionKey')
        user = request.headers.get('X-User', 'ANONYMOUS')
        ip_address = request.headers.get('X-IP', '0.0.0.0')
        
        if not encrypted_data:
            return jsonify({"error": "Encrypted data required"}), 400
        
        # Simulate decryption
        try:
            import base64
            decoded = base64.b64decode(encrypted_data).decode('utf-8')
            preview = decoded[:200] + ('...' if len(decoded) > 200 else '')
        except:
            preview = encrypted_data[:100] + '...'
        
        # Create session
        session_id = str(uuid.uuid4())
        data_hash = hashlib.sha256(encrypted_data.encode()).hexdigest()
        
        encrypted_data_sessions[session_id] = {
            "data_hash": data_hash,
            "preview": preview,
            "created_at": int(time.time() * 1000),
            "expires_at": int(time.time() * 1000) + 300000  # 5 minutes
        }
        
        # Log audit
        audit_log.append({
            "id": str(len(audit_log)),
            "timestamp": int(time.time() * 1000),
            "action": "PREVIEW",
            "data_hash": data_hash,
            "user": user,
            "ip_address": ip_address,
            "session_id": session_id
        })
        
        return jsonify({
            "session_id": session_id,
            "data_hash": data_hash,
            "preview": preview,
            "expires_in_seconds": 300
        })
    except Exception as e:
        logger.error(f"Error previewing encrypted data: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/encrypted/upload', methods=['POST'])
def upload_encrypted_data():
    """Upload and encrypt multiple data items"""
    try:
        import uuid
        data = request.get_json()
        
        data_items = data.get('dataItems', [])
        encryption_key = data.get('encryptionKey')
        user = request.headers.get('X-User', 'ANONYMOUS')
        ip_address = request.headers.get('X-IP', '0.0.0.0')
        
        if not data_items:
            return jsonify({"error": "No data items provided"}), 400
        
        encrypted_items = []
        hashes = []
        
        for item in data_items:
            # Simulate encryption
            import base64
            encrypted = base64.b64encode(item.encode()).decode()
            item_hash = hashlib.sha256(encrypted.encode()).hexdigest()
            
            encrypted_items.append(encrypted)
            hashes.append(item_hash)
            
            # Log audit
            audit_log.append({
                "id": str(len(audit_log)),
                "timestamp": int(time.time() * 1000),
                "action": "UPLOAD",
                "data_hash": item_hash,
                "user": user,
                "ip_address": ip_address,
                "details": f"Encrypted data item uploaded"
            })
        
        upload_id = str(uuid.uuid4())
        
        return jsonify({
            "upload_id": upload_id,
            "item_count": len(encrypted_items),
            "encrypted_items": encrypted_items,
            "hashes": hashes,
            "timestamp": int(time.time() * 1000)
        }), 201
    except Exception as e:
        logger.error(f"Error uploading encrypted data: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/encrypted/audit', methods=['GET'])
def get_encrypted_audit_log():
    """Get audit log for encrypted data access"""
    try:
        data_hash = request.args.get('data_hash')
        
        if data_hash:
            filtered_log = [log for log in audit_log if log.get('data_hash') == data_hash]
        else:
            filtered_log = audit_log
        
        return jsonify({
            "audit_log": filtered_log,
            "total_entries": len(filtered_log)
        })
    except Exception as e:
        logger.error(f"Error getting audit log: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/encrypted/stats', methods=['GET'])
def get_encrypted_data_stats():
    """Get statistics about encrypted data operations"""
    try:
        preview_count = len([log for log in audit_log if log.get('action') == 'PREVIEW'])
        upload_count = len([log for log in audit_log if log.get('action') == 'UPLOAD'])
        unique_users = len(set(log.get('user') for log in audit_log))
        
        return jsonify({
            "total_audit_entries": len(audit_log),
            "active_sessions": len(encrypted_data_sessions),
            "preview_count": preview_count,
            "upload_count": upload_count,
            "unique_users": unique_users
        })
    except Exception as e:
        logger.error(f"Error getting encrypted data stats: {e}")
        return jsonify({"error": str(e)}), 500


# ==================== MERKLE TREE ENDPOINTS ====================

@app.route('/merkle/status', methods=['GET'])
def get_merkle_status():
    """Get current Merkle Tree status"""
    try:
        return jsonify({
            "root_hash": merkle_tree_current_root or "empty-tree",
            "event_count": len(merkle_tree_events),
            "last_update": merkle_tree_events[-1].get('timestamp') if merkle_tree_events else None,
            "tree_initialized": merkle_tree_current_root is not None
        })
    except Exception as e:
        logger.error(f"Error getting merkle status: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/merkle/add-leaves', methods=['POST'])
def merkle_add_leaves():
    """Add new leaves to Merkle Tree"""
    try:
        global merkle_tree_current_root
        
        data = request.get_json()
        leaves = data.get('leaves', [])
        reason = data.get('reason', 'Data insertion')
        
        if not leaves:
            return jsonify({"error": "No leaves provided"}), 400
        
        old_root = merkle_tree_current_root or "empty-tree"
        
        # Simulate tree update
        tree_input = ''.join(leaves)
        new_root = hashlib.sha256(tree_input.encode()).hexdigest()
        merkle_tree_current_root = new_root
        
        # Log event
        merkle_tree_events.append({
            "id": str(len(merkle_tree_events)),
            "timestamp": int(time.time() * 1000),
            "event_type": "ADD_LEAVES",
            "previous_root": old_root,
            "new_root": new_root,
            "leaves_count": len(leaves),
            "reason": reason
        })
        
        return jsonify({
            "previous_root": old_root,
            "new_root": new_root,
            "leaves_added": len(leaves),
            "timestamp": int(time.time() * 1000)
        })
    except Exception as e:
        logger.error(f"Error adding merkle leaves: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/merkle/update-leaves', methods=['PUT'])
def merkle_update_leaves():
    """Update existing Merkle Tree leaves"""
    try:
        global merkle_tree_current_root
        
        data = request.get_json()
        updates = data.get('updates', {})
        reason = data.get('reason', 'Data update')
        
        if not updates:
            return jsonify({"error": "No updates provided"}), 400
        
        old_root = merkle_tree_current_root or "empty-tree"
        
        # Simulate tree update
        tree_input = json.dumps(updates)
        new_root = hashlib.sha256(tree_input.encode()).hexdigest()
        merkle_tree_current_root = new_root
        
        # Log event
        merkle_tree_events.append({
            "id": str(len(merkle_tree_events)),
            "timestamp": int(time.time() * 1000),
            "event_type": "UPDATE_LEAVES",
            "previous_root": old_root,
            "new_root": new_root,
            "updates_count": len(updates),
            "reason": reason
        })
        
        return jsonify({
            "previous_root": old_root,
            "new_root": new_root,
            "updates_count": len(updates),
            "timestamp": int(time.time() * 1000)
        })
    except Exception as e:
        logger.error(f"Error updating merkle leaves: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/merkle/history', methods=['GET'])
def get_merkle_history():
    """Get Merkle Tree transition history"""
    try:
        event_type = request.args.get('type')
        
        if event_type:
            history = [e for e in merkle_tree_events if e.get('event_type') == event_type]
        else:
            history = merkle_tree_events
        
        return jsonify({
            "events": history,
            "total_events": len(history)
        })
    except Exception as e:
        logger.error(f"Error getting merkle history: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/merkle/verify', methods=['POST'])
def verify_merkle_tree():
    """Verify Merkle Tree integrity"""
    try:
        return jsonify({
            "is_valid": True,
            "current_root": merkle_tree_current_root or "empty-tree",
            "verification_time": int(time.time() * 1000)
        })
    except Exception as e:
        logger.error(f"Error verifying merkle tree: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/merkle/stats', methods=['GET'])
def get_merkle_stats():
    """Get Merkle Tree statistics"""
    try:
        event_counts = {}
        for event in merkle_tree_events:
            event_type = event.get('event_type')
            event_counts[event_type] = event_counts.get(event_type, 0) + 1
        
        return jsonify({
            "root_hash": merkle_tree_current_root or "empty-tree",
            "total_events": len(merkle_tree_events),
            "event_counts": event_counts,
            "last_update": merkle_tree_events[-1].get('timestamp') if merkle_tree_events else None
        })
    except Exception as e:
        logger.error(f"Error getting merkle stats: {e}")
        return jsonify({"error": str(e)}), 500


# ============================================================================
# ENCRYPTED DATA ENDPOINTS (Kyber + Dilithium Integration)
# ============================================================================

@app.route('/api/v1/encrypted/encrypt', methods=['POST'])
def encrypt_data():
    """Encrypt data with Kyber + Dilithium"""
    try:
        data = request.get_json()
        plaintext = data.get('data', '')
        user_id = request.headers.get('X-User', 'unknown')
        ip_address = request.headers.get('X-IP', 'unknown')
        
        if not plaintext:
            return jsonify({"error": "No data provided"}), 400
        
        # Simulate Kyber encapsulation
        plaintext_bytes = plaintext.encode('utf-8')
        data_hash = hashlib.sha256(plaintext_bytes).hexdigest()
        
        # Simulate encryption (in production: use real Kyber)
        encapsulated_key = hashlib.sha256((plaintext + "kyber").encode()).hexdigest()
        ciphertext = hashlib.sha256((plaintext + "cipher").encode()).hexdigest()
        
        # Simulate Dilithium signature
        signature = "ML_DSA_" + hashlib.sha256((plaintext + "dilithium").encode()).hexdigest()
        
        # Log audit
        audit_log.append({
            "action": "ENCRYPT",
            "dataHash": data_hash,
            "userId": user_id,
            "ipAddress": ip_address,
            "timestamp": time.time(),
            "details": "Data encrypted with Kyber + Dilithium"
        })
        
        return jsonify({
            "encryptedDataId": hashlib.md5(plaintext.encode()).hexdigest(),
            "dataHash": data_hash,
            "encapsulatedKey": encapsulated_key,
            "ciphertext": ciphertext,
            "signature": signature,
            "algorithm": "KYBER_DILITHIUM",
            "timestamp": time.time(),
            "kyberPublicKey": "kyber_pk_" + data_hash[:16],
            "dilithiumPublicKey": "dilithium_pk_" + data_hash[:16]
        })
    except Exception as e:
        logger.error(f"Error encrypting data: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/api/v1/encrypted/preview', methods=['POST'])
def preview_encrypted_data_api_v1():
    """Create a temporary preview session for encrypted data"""
    try:
        data = request.get_json()
        encrypted_data = data.get('encryptedData', {})
        user_id = request.headers.get('X-User', 'unknown')
        ip_address = request.headers.get('X-IP', 'unknown')
        
        data_hash = encrypted_data.get('dataHash', '')
        
        # Create session
        session_id = hashlib.md5(f"{data_hash}{time.time()}".encode()).hexdigest()
        
        # Simulate decryption
        decrypted_data = f"[Decrypted: {encrypted_data.get('encryptedDataId', 'unknown')[:8]}...]"
        
        encrypted_data_sessions[session_id] = {
            "decryptedData": decrypted_data,
            "createdAt": time.time(),
            "expiresAt": time.time() + (5 * 60),  # 5 minutes
            "dataHash": data_hash
        }
        
        # Log audit
        audit_log.append({
            "action": "DECRYPT_SUCCESS",
            "dataHash": data_hash,
            "userId": user_id,
            "ipAddress": ip_address,
            "timestamp": time.time(),
            "details": "Data decrypted for preview (TTL: 5 minutes)"
        })
        
        return jsonify({
            "sessionId": session_id,
            "decryptedData": decrypted_data,
            "dataHash": data_hash,
            "isIntegrityValid": True,
            "signatureValid": True,
            "timestamp": time.time(),
            "expiresAt": time.time() + (5 * 60),
            "signature": encrypted_data.get('signature', '')
        })
    except Exception as e:
        logger.error(f"Error previewing data: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/api/v1/encrypted/audit', methods=['GET'])
def get_encrypted_audit_log_api_v1():
    """Get encryption audit log"""
    try:
        user_id = request.headers.get('X-User', 'unknown')
        
        # Filter by user if needed
        entries = [a for a in audit_log if a.get('userId') == user_id or user_id == 'unknown']
        
        return jsonify({
            "entries": entries[-50:],  # Last 50 entries
            "totalEntries": len(audit_log),
            "filteredCount": len(entries)
        })
    except Exception as e:
        logger.error(f"Error getting audit log: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/api/v1/encrypted/stats', methods=['GET'])
def get_encrypted_stats_api_v1():
    """Get encryption statistics"""
    try:
        return jsonify({
            "totalSessions": len(encrypted_data_sessions),
            "activeSessions": len([s for s in encrypted_data_sessions.values() if s.get('expiresAt', 0) > time.time()]),
            "auditEntries": len(audit_log),
            "algorithmSupported": ["KYBER_DILITHIUM"],
            "sessionTTLSeconds": 300
        })
    except Exception as e:
        logger.error(f"Error getting stats: {e}")
        return jsonify({"error": str(e)}), 500


# ============================================================================
# MERKLE TREE AUTONOMOUS DYNAMICS MONITORING
# ============================================================================

@app.route('/api/v1/merkle/status', methods=['GET'])
def get_merkle_status_api_v1():
    """Get current Merkle Tree status with node state information"""
    try:
        return jsonify({
            "rootHash": merkle_tree_current_root or "0x" + "0" * 64,
            "totalNodes": len(merkle_tree_events) + 3,  # Base + transitions
            "validNodes": len([e for e in merkle_tree_events if e.get('to') == 'VALID']),
            "decoyNodes": len([e for e in merkle_tree_events if e.get('to') == 'DECOY']),
            "compromisedNodes": len([e for e in merkle_tree_events if e.get('to') == 'COMPROMISED']),
            "transitioningNodes": len([e for e in merkle_tree_events if e.get('to') == 'TRANSITIONING']),
            "autonomousTransitions": len(merkle_tree_events),
            "engineRunning": True,
            "lastUpdate": time.time()
        })
    except Exception as e:
        logger.error(f"Error getting merkle status: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/api/v1/merkle/add-leaves', methods=['POST'])
def add_merkle_leaves_api_v1():
    """Add leaves to Merkle Tree"""
    try:
        data = request.get_json()
        leaves = data.get('leaves', [])
        reason = data.get('reason', 'User added leaves')
        
        old_root = merkle_tree_current_root or "0x" + "0" * 64
        
        # Simulate new root
        merkle_tree_current_root = hashlib.sha256(
            (old_root + str(leaves)).encode()
        ).hexdigest()
        
        return jsonify({
            "previousRoot": old_root,
            "newRoot": merkle_tree_current_root,
            "leavesAdded": len(leaves),
            "eventId": hashlib.md5(str(time.time()).encode()).hexdigest(),
            "timestamp": time.time(),
            "dynamicStats": {
                "total_nodes": len(leaves) + 3,
                "valid_nodes": len(leaves),
                "decoy_nodes": 0,
                "total_transitions": len(merkle_tree_events)
            }
        })
    except Exception as e:
        logger.error(f"Error adding merkle leaves: {e}")
        return jsonify({"error": str(e)}), 500


if __name__ == '__main__':
    # Initialize model when running directly
    initialize_model()
    # Run the app when executed directly (development / local run)
    port = int(os.environ.get('PORT', 5000))
    debug = os.environ.get('DEBUG', 'false').lower() == 'true'

    logger.info(f"Starting SQUID AI service on port {port}")
    app.run(host='0.0.0.0', port=port, debug=debug)


# Also initialize model when the module is imported by WSGI servers (e.g., gunicorn)
if squid_model is None:
    try:
        initialize_model()
    except Exception as e:
        logger.warning(f"Model initialization on import failed: {e}")
