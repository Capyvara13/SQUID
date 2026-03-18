"""Painel de IA (SR/C)."""

import time
from datetime import datetime
from typing import Dict, List

from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QGroupBox,
    QLabel, QTextEdit, QPushButton, QGridLayout
)
from PySide6.QtCore import Qt

from ..models import AIMetrics


class MetricCard(QWidget):
    """Card de métrica simples."""
    
    def __init__(self, title: str, value: str = "-", color: str = "#38bdf8", parent=None):
        super().__init__(parent)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(10, 8, 10, 8)
        layout.setSpacing(4)
        
        self.title_lbl = QLabel(title)
        self.title_lbl.setStyleSheet("color: #94a3b8; font-size: 9pt;")
        layout.addWidget(self.title_lbl)
        
        self.value_lbl = QLabel(value)
        self.value_lbl.setStyleSheet(f"color: {color}; font-size: 16pt; font-weight: bold;")
        layout.addWidget(self.value_lbl)
        
        self.setStyleSheet("background-color: #1e293b; border-radius: 6px;")
    
    def set_value(self, value: str):
        """Atualiza o valor."""
        self.value_lbl.setText(value)


class AIPanel(QWidget):
    """Painel para monitoramento da IA com métricas SR/C."""
    
    def __init__(self, python_client, parent=None):
        super().__init__(parent)
        self.python_client = python_client
        self._setup_ui()
    
    def _setup_ui(self):
        """Configura a interface."""
        layout = QVBoxLayout(self)
        layout.setSpacing(15)
        
        # Cards de métricas SR/C
        metrics_group = QGroupBox("Métricas IA (Super-Relação / Correlação)")
        metrics_layout = QGridLayout(metrics_group)
        
        self.sr_card = MetricCard("SR", "0.0000", "#8b5cf6")
        metrics_layout.addWidget(self.sr_card, 0, 0)
        
        self.c_card = MetricCard("C", "0.0000", "#10b981")
        metrics_layout.addWidget(self.c_card, 0, 1)
        
        self.confidence_card = MetricCard("Confiança", "0%", "#f59e0b")
        metrics_layout.addWidget(self.confidence_card, 0, 2)
        
        self.decision_card = MetricCard("Decisão", "HOLD_STATE", "#38bdf8")
        metrics_layout.addWidget(self.decision_card, 0, 3)
        
        layout.addWidget(metrics_group)
        
        # Informações do modelo
        model_group = QGroupBox("Informações do Modelo PyTorch")
        model_layout = QVBoxLayout(model_group)
        
        self.model_info_lbl = QLabel("Carregando...")
        self.model_info_lbl.setStyleSheet("color: #94a3b8;")
        model_layout.addWidget(self.model_info_lbl)
        
        self.model_hash_lbl = QLabel("Hash: -")
        model_layout.addWidget(self.model_hash_lbl)
        
        layout.addWidget(model_group)
        
        # Log de ações
        actions_group = QGroupBox("Ações e Decisões da IA")
        actions_layout = QVBoxLayout(actions_group)
        
        self.actions_log = QTextEdit()
        self.actions_log.setReadOnly(True)
        self.actions_log.setMaximumHeight(200)
        self.actions_log.setObjectName("LogConsole")
        actions_layout.addWidget(self.actions_log)
        
        layout.addWidget(actions_group)
        
        # Controles
        controls = QHBoxLayout()
        
        self.refresh_btn = QPushButton("Atualizar Metricas")
        self.refresh_btn.clicked.connect(self._refresh_metrics)
        controls.addWidget(self.refresh_btn)
        
        self.test_btn = QPushButton("Testar Decisao")
        self.test_btn.clicked.connect(self._test_decision)
        controls.addWidget(self.test_btn)
        
        controls.addStretch()
        layout.addLayout(controls)
        layout.addStretch()
    
    def _refresh_metrics(self):
        """Atualiza métricas da IA."""
        # Busca informações do modelo
        model_info = self.python_client.model_info()
        if model_info:
            loaded = model_info.get('model_loaded', False)
            path = model_info.get('model_loaded_path', 'N/A')
            hash_val = model_info.get('model_hash', 'N/A')
            params = model_info.get('parameters', 0)
            
            status = "Carregado" if loaded else "Nao carregado"
            self.model_info_lbl.setText(
                f"{status} | Caminho: {path} | Parâmetros: {params:,}"
            )
            self.model_hash_lbl.setText(
                f"Hash: {hash_val[:32]}..." if hash_val else "Hash: -"
            )
    
    def _test_decision(self):
        """Testa uma decisão da IA com features de exemplo."""
        # Features de exemplo
        features = [
            {
                "depth": 3,
                "index": i,
                "index_hash": hash(f"leaf_{i}") % 10000,
                "local_entropy": 7.5 + (i * 0.1),
                "timestamp": int(time.time() * 1000),
                "global_L": 64,
                "global_b": 4,
                "global_m": 3,
                "global_t": 128,
                "last_access_count": i % 5,
                "leaf_hist_score": 0.3 + (i * 0.05)
            }
            for i in range(5)
        ]
        
        params = {"b": 4, "m": 3, "t": 128}
        
        # Chama API
        decision = self.python_client.decide(params, features)
        
        if decision:
            self._update_metrics_from_decision(decision)
        else:
            self.actions_log.append("Erro ao obter decisao da IA")
    
    def _update_metrics_from_decision(self, decision: Dict):
        """Atualiza UI com dados da decisão.
        
        Args:
            decision: Dicionário com sr, c, actions, decision, confidence
        """
        sr = decision.get('sr', 0)
        c = decision.get('c', 0)
        confidence = decision.get('confidence', 0)
        ai_decision = decision.get('decision', 'HOLD_STATE')
        actions = decision.get('actions', [])
        entropy_budget = decision.get('entropy_budget_remaining', 0)
        
        # Atualiza cards
        self.sr_card.set_value(f"{sr:.4f}")
        self.c_card.set_value(f"{c:.4f}")
        self.confidence_card.set_value(f"{confidence*100:.1f}%")
        self.decision_card.set_value(ai_decision)
        
        # Color coding para decisão
        if ai_decision == "HOLD_STATE":
            self.decision_card.value_lbl.setStyleSheet("color: #22c55e; font-size: 16pt; font-weight: bold;")
        elif ai_decision == "MUTATE_TREE":
            self.decision_card.value_lbl.setStyleSheet("color: #f59e0b; font-size: 16pt; font-weight: bold;")
        else:
            self.decision_card.value_lbl.setStyleSheet("color: #ef4444; font-size: 16pt; font-weight: bold;")
        
        # Adiciona ao log
        timestamp = datetime.now().strftime('%H:%M:%S')
        self.actions_log.append(
            f"[{timestamp}] SR={sr:.4f} | C={c:.4f} | "
            f"Confiança={confidence*100:.1f}% | Entropy={entropy_budget:.1f}"
        )
        self.actions_log.append(f"  Decisão: {ai_decision}")
        if actions:
            self.actions_log.append(f"  Ações: {', '.join(str(a) for a in actions[:5])}")
        
        drivers = decision.get('drivers', [])
        if drivers:
            self.actions_log.append(f"  Drivers: {', '.join(drivers)}")
        
        self.actions_log.append("-" * 50)
