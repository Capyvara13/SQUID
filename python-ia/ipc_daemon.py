#!/usr/bin/env python3
"""Lightweight IPC daemon for SQUID AI service.

Protocol: JSON per line on stdin; daemon writes JSON per line on stdout.

Supports commands:
 - health: returns {ok: true}
 - decide: payload { params, features, seed_model_hash(optional) } -> returns sr, c, actions, rotation_plan
 - train: quick training stub (fast_train)

Also supports --once: read one JSON blob from stdin, write response, exit.
"""
import sys
import json
import traceback
from squid_formulas import SuperRelationCalculator, CorrelationCalculator, PolicyCalculator
from decision_engine import plan_rotations

# Simple global entropy budget. Each AI decision that performs non-trivial
# randomization consumes a small amount. When exhausted, behaviour becomes
# effectively deterministic (LOCKED-like) until process restart.
ENTROPY_BUDGET = 100.0


def sanitize(value):
    try:
        if value is None or not (isinstance(value, (int, float))):
            return 0.0
        if not (value == value) or value == float('inf') or value == float('-inf'):
            return 0.0
        return float(value)
    except Exception:
        return 0.0


def _compute_ai_explanation(sr: float, c: float, rotation_plan: dict) -> dict:
    """Map SR/C and rotation risk into a high-level decision explanation.

    This is intentionally minimal and does not expose internal weights.
    """
    risk = float(rotation_plan.get("risk", 0.0)) if rotation_plan else 0.0
    rotation_count = int(rotation_plan.get("rotation_count", 0)) if rotation_plan else 0

    # Default decision: HOLD_STATE with moderate confidence.
    decision = "HOLD_STATE"
    confidence = 0.6
    drivers = []

    # Heuristics: low SR -> drift, high C/risk -> entropy/temporal issues.
    if sr < 0.4:
        drivers.append("fingerprint_drift")
    if c > 0.7:
        drivers.append("entropy_spike")
    if risk > 0.6:
        drivers.append("low_temporal_coherence")

    if rotation_count > 0 and risk >= 0.4:
        decision = "MUTATE_TREE"
        confidence = min(1.0, 0.7 + risk * 0.3)
    elif rotation_count > 0:
        decision = "INSERT_DECOY"
        confidence = 0.6 + risk * 0.2

    if not drivers:
        drivers.append("stable_conditions")

    return {
        "decision": decision,
        "confidence": float(max(0.0, min(1.0, confidence))),
        "drivers": drivers,
    }


def handle_decide(payload):
    params = payload.get('params', {})
    features = payload.get('features', [])
    seed = payload.get('seed_model_hash')

    sr_calc = SuperRelationCalculator()
    c_calc = CorrelationCalculator()
    policy = PolicyCalculator()

    try:
        sr = sr_calc.calculate(params)
        c = c_calc.calculate(params)
    except Exception:
        sr = 0.0
        c = 0.0

    sr = sanitize(sr)
    c = sanitize(c)

    global ENTROPY_BUDGET

    # Decide whether we still have entropy budget for stochastic behaviour.
    locked = ENTROPY_BUDGET <= 0.0
    if locked:
        seed_val = 42
    else:
        seed_val = None

    # Generate actions via policy
    actions = policy.generate_actions(len(features), sr, c, seed=seed_val)

    rotation_plan = plan_rotations(len(features), sr, c, seed=seed_val)

    # Consume a small amount of entropy budget when we actually rotate.
    if not locked:
        ENTROPY_BUDGET -= 0.5 + 0.01 * float(rotation_plan.get('rotation_count', 0))

    explanation = _compute_ai_explanation(sr, c, rotation_plan)

    return {
        'sr': sr,
        'c': c,
        'actions': actions,
        'rotation_plan': rotation_plan,
        'decision': explanation['decision'],
        'confidence': explanation['confidence'],
        'drivers': explanation['drivers'],
        'entropy_budget_remaining': max(0.0, float(ENTROPY_BUDGET))
    }


def handle_health(_payload):
    return {'ok': True, 'service': 'python-ia', 'status': 'ready'}


def process_message(msg):
    cmd = msg.get('cmd')
    payload = msg.get('payload', {})
    try:
        if cmd == 'decide':
            return {'ok': True, 'result': handle_decide(payload)}
        elif cmd == 'health':
            return {'ok': True, 'result': handle_health(payload)}
        elif cmd == 'train':
            # quick stub: return success
            return {'ok': True, 'result': {'trained': True}}
        else:
            return {'ok': False, 'error': 'unknown_cmd'}
    except Exception as e:
        return {'ok': False, 'error': str(e), 'trace': traceback.format_exc()}


def run_once():
    data = sys.stdin.read()
    if not data:
        return
    try:
        msg = json.loads(data)
    except Exception:
        # try line-based
        try:
            msg = json.loads(data.strip().splitlines()[0])
        except Exception:
            print(json.dumps({'ok': False, 'error': 'invalid_json'}))
            return

    resp = process_message(msg)
    print(json.dumps(resp, separators=(',', ':')))
    sys.stdout.flush()


def run_daemon():
    while True:
        line = sys.stdin.readline()
        if not line:
            break
        line = line.strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except Exception:
            out = {'ok': False, 'error': 'invalid_json'}
            print(json.dumps(out, separators=(',', ':')))
            sys.stdout.flush()
            continue

        resp = process_message(msg)
        print(json.dumps(resp, separators=(',', ':')))
        sys.stdout.flush()


if __name__ == '__main__':
    once = '--once' in sys.argv
    if once:
        run_once()
    else:
        run_daemon()
