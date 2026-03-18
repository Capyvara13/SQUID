"""Painel de monitoramento de banco de dados SQUID."""

from datetime import datetime
from typing import Optional

from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QGroupBox,
    QLabel, QTableWidget, QTableWidgetItem, QPushButton,
    QHeaderView, QProgressBar, QGridLayout, QComboBox
)
from PySide6.QtCore import Qt, QTimer
from PySide6.QtGui import QColor

from .overview_panel import MetricCard


class DatabasePanel(QWidget):
    """Painel de monitoramento do banco de dados.

    Secoes:
        - Database Health: conexoes, latencia, tamanho do banco
        - Merkle Integrity: validacao da arvore, divergencias
        - Audit Monitor: eventos recentes, alertas
    """

    def __init__(self, java_client, parent=None):
        super().__init__(parent)
        self.java_client = java_client
        self.current_instance_id = None
        self._setup_ui()

        # timer de auto-refresh (a cada 10 segundos)
        self._timer = QTimer(self)
        self._timer.timeout.connect(self.refresh_all)
        self._timer.setInterval(10_000)

    # aqui estou configurando a interface do painel

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setSpacing(12)

        # selecao de instancia
        instance_group = QGroupBox("Instancia")
        instance_layout = QHBoxLayout(instance_group)
        
        instance_layout.addWidget(QLabel("Instancia ativa:"))
        self.instance_combo = QComboBox()
        self.instance_combo.currentTextChanged.connect(self._on_instance_changed)
        instance_layout.addWidget(self.instance_combo, 1)
        
        self.refresh_instances_btn = QPushButton("Atualizar Lista")
        self.refresh_instances_btn.clicked.connect(self._refresh_instance_list)
        instance_layout.addWidget(self.refresh_instances_btn)
        
        layout.addWidget(instance_group)

        # toolbar superior
        toolbar = QHBoxLayout()
        self.refresh_btn = QPushButton("Atualizar")
        self.refresh_btn.clicked.connect(self.refresh_all)
        toolbar.addWidget(self.refresh_btn)

        self.auto_refresh_btn = QPushButton("Auto-refresh")
        self.auto_refresh_btn.setCheckable(True)
        self.auto_refresh_btn.toggled.connect(self._toggle_auto_refresh)
        toolbar.addWidget(self.auto_refresh_btn)

        toolbar.addStretch()

        self.status_label = QLabel("Aguardando dados...")
        self.status_label.setStyleSheet("color: #64748b;")
        toolbar.addWidget(self.status_label)

        layout.addLayout(toolbar)

        # 1. Database Health
        health_group = QGroupBox("Database Health")
        health_layout = QGridLayout(health_group)

        self.card_type = MetricCard("Tipo DB", "-", "#38bdf8")
        health_layout.addWidget(self.card_type, 0, 0)

        self.card_connected = MetricCard("Status", "-", "#22c55e")
        health_layout.addWidget(self.card_connected, 0, 1)

        self.card_latency = MetricCard("Latencia", "-", "#f59e0b")
        health_layout.addWidget(self.card_latency, 0, 2)

        self.card_connections = MetricCard("Conexoes Ativas", "-", "#8b5cf6")
        health_layout.addWidget(self.card_connections, 0, 3)

        self.card_size = MetricCard("Tamanho DB", "-", "#06b6d4")
        health_layout.addWidget(self.card_size, 1, 0)

        self.card_ssl = MetricCard("SSL/TLS", "-", "#64748b")
        health_layout.addWidget(self.card_ssl, 1, 1)

        self.card_version = MetricCard("Versao DB", "-", "#64748b")
        health_layout.addWidget(self.card_version, 1, 2)

        self.card_pool = MetricCard("Pool Size", "-", "#64748b")
        health_layout.addWidget(self.card_pool, 1, 3)

        # contadores de tabelas
        self.tables_group = QGroupBox("Tabelas")
        tables_layout = QVBoxLayout(self.tables_group)
        self.table_labels = {}
        for name in ["users", "audit_logs", "merkle_nodes", "probability_models", "decoy_operations"]:
            row = QHBoxLayout()
            lbl = QLabel(name)
            lbl.setMinimumWidth(160)
            lbl.setStyleSheet("font-family: Consolas; color: #94a3b8;")
            row.addWidget(lbl)
            bar = QProgressBar()
            bar.setRange(0, 1000)
            bar.setFormat("%v registros")
            bar.setStyleSheet("QProgressBar::chunk { background-color: #38bdf8; }")
            row.addWidget(bar)
            tables_layout.addLayout(row)
            self.table_labels[name] = bar

        health_layout.addWidget(self.tables_group, 2, 0, 1, 4)
        layout.addWidget(health_group)

        # 2. Merkle Integrity
        merkle_group = QGroupBox("Merkle Integrity")
        merkle_layout = QGridLayout(merkle_group)

        self.card_stored_root = MetricCard("Root Armazenado", "-", "#8b5cf6")
        merkle_layout.addWidget(self.card_stored_root, 0, 0)

        self.card_live_root = MetricCard("Root Live", "-", "#10b981")
        merkle_layout.addWidget(self.card_live_root, 0, 1)

        self.card_roots_match = MetricCard("Integridade", "-", "#64748b")
        merkle_layout.addWidget(self.card_roots_match, 0, 2)

        self.card_tree_ver = MetricCard("Versao Arvore", "-", "#f59e0b")
        merkle_layout.addWidget(self.card_tree_ver, 0, 3)

        self.card_total_nodes = MetricCard("Total Nos", "-", "#06b6d4")
        merkle_layout.addWidget(self.card_total_nodes, 1, 0)

        self.verify_merkle_btn = QPushButton("Verificar Merkle")
        self.verify_merkle_btn.clicked.connect(self._refresh_merkle)
        merkle_layout.addWidget(self.verify_merkle_btn, 1, 3)

        layout.addWidget(merkle_group)

        # 3. Audit Monitor
        audit_group = QGroupBox("Audit Monitor")
        audit_layout = QVBoxLayout(audit_group)

        # resumo da cadeia
        chain_row = QHBoxLayout()
        self.card_chain_total = MetricCard("Total Entradas", "-", "#38bdf8")
        chain_row.addWidget(self.card_chain_total)
        self.card_chain_valid = MetricCard("Validas", "-", "#22c55e")
        chain_row.addWidget(self.card_chain_valid)
        self.card_chain_broken = MetricCard("Quebradas", "-", "#ef4444")
        chain_row.addWidget(self.card_chain_broken)
        self.card_chain_status = MetricCard("Cadeia", "-", "#64748b")
        chain_row.addWidget(self.card_chain_status)
        audit_layout.addLayout(chain_row)

        # botao verificar
        btn_row = QHBoxLayout()
        self.verify_chain_btn = QPushButton("Verificar Cadeia de Auditoria")
        self.verify_chain_btn.clicked.connect(self._refresh_audit_verify)
        btn_row.addWidget(self.verify_chain_btn)
        btn_row.addStretch()
        audit_layout.addLayout(btn_row)

        # tabela de logs recentes
        audit_layout.addWidget(QLabel("<b>Eventos Recentes:</b>"))
        self.audit_table = QTableWidget()
        self.audit_table.setColumnCount(6)
        self.audit_table.setHorizontalHeaderLabels(
            ["ID", "Acao", "Ator", "Timestamp", "Hash", "Merkle Leaf"]
        )
        self.audit_table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.audit_table.setAlternatingRowColors(True)
        self.audit_table.setEditTriggers(QTableWidget.NoEditTriggers)
        self.audit_table.setSelectionBehavior(QTableWidget.SelectRows)
        self.audit_table.setMaximumHeight(250)
        audit_layout.addWidget(self.audit_table)

        layout.addWidget(audit_group)
        layout.addStretch()

    # aqui estou implementando a logica de refresh

    def refresh_all(self):
        # atualiza todos os dados do painel
        self._refresh_health()
        self._refresh_merkle()
        self._refresh_audit_logs()
        self._refresh_audit_verify()
        self.status_label.setText(f"Atualizado: {datetime.now():%H:%M:%S}")

    def _toggle_auto_refresh(self, checked: bool):
        if checked:
            self._timer.start()
            self.auto_refresh_btn.setText("Parar auto-refresh")
        else:
            self._timer.stop()
            self.auto_refresh_btn.setText("Auto-refresh")

    def _refresh_instance_list(self):
        # atualiza a lista de instancias disponiveis
        # aqui estou buscando as instancias do backend
        def fetch():
            return self.java_client.get_instances()
        
        def on_complete(instances):
            self.instance_combo.clear()
            if instances:
                for instance in instances:
                    self.instance_combo.addItem(instance.get("id", "unknown"))
        
        # worker para busca assincrona
        from ..workers import AsyncActionWorker
        worker = AsyncActionWorker(fetch)
        worker.finished.connect(on_complete)
        worker.start()

    def _on_instance_changed(self, instance_id: str):
        # quando o usuario muda a instancia selecionada
        if instance_id:
            self.current_instance_id = instance_id
            self.refresh_all()

    def _refresh_health(self):
        # aqui estou verificando a saude do banco de dados
        data = self.java_client.db_health(self.current_instance_id)
        if not data:
            self.card_connected.set_value("OFFLINE")
            self.card_connected.value_lbl.setStyleSheet(
                "color: #ef4444; font-size: 14pt; font-weight: bold;"
            )
            return

        connected = data.get("connected", False)
        self.card_connected.set_value("ONLINE" if connected else "OFFLINE")
        self.card_connected.value_lbl.setStyleSheet(
            f"color: {'#22c55e' if connected else '#ef4444'}; font-size: 14pt; font-weight: bold;"
        )

        self.card_type.set_value(data.get("db_type", "-").upper())
        self.card_latency.set_value(f"{data.get('latency_ms', '-')} ms")
        self.card_connections.set_value(str(data.get("active_connections", "-")))
        self.card_size.set_value(f"{data.get('db_size_mb', '-')} MB")
        self.card_ssl.set_value("ON" if data.get("ssl_enabled") else "OFF")
        self.card_version.set_value(str(data.get("db_version", "-"))[:20])

        # contadores de tabelas
        counts = data.get("table_counts", {})
        for name, bar in self.table_labels.items():
            count = counts.get(name, 0)
            if count < 0:
                count = 0
            bar.setMaximum(max(count, 1))
            bar.setValue(count)
            bar.setFormat(f"{count} registros")

    def _refresh_merkle(self):
        # aqui estou verificando a integridade da merkle tree
        data = self.java_client.db_merkle_integrity(self.current_instance_id)
        if not data:
            return

        stored = data.get("stored_root", "-") or "-"
        live = data.get("live_root", "-") or "-"
        match = data.get("roots_match", False)

        self.card_stored_root.set_value(stored[:16] + "..." if len(stored) > 16 else stored)
        self.card_live_root.set_value(live[:16] + "..." if len(live) > 16 else live)

        if match:
            self.card_roots_match.set_value("INTEGRO")
            self.card_roots_match.value_lbl.setStyleSheet(
                "color: #22c55e; font-size: 14pt; font-weight: bold;"
            )
        else:
            self.card_roots_match.set_value("DIVERGENCIA")
            self.card_roots_match.value_lbl.setStyleSheet(
                "color: #ef4444; font-size: 14pt; font-weight: bold;"
            )

        self.card_tree_ver.set_value(str(data.get("latest_tree_version", "-")))
        self.card_total_nodes.set_value(str(data.get("total_stored_nodes", "-")))

    def _refresh_audit_logs(self):
        # aqui estou carregando os logs de auditoria
        data = self.java_client.db_audit_logs(self.current_instance_id, limit=20)
        if not data:
            self.audit_table.setRowCount(0)
            return

        self.audit_table.setRowCount(len(data))
        for row_idx, entry in enumerate(data):
            self.audit_table.setItem(row_idx, 0, QTableWidgetItem(str(entry.get("id", ""))))
            self.audit_table.setItem(row_idx, 1, QTableWidgetItem(entry.get("action", "")))
            self.audit_table.setItem(row_idx, 2, QTableWidgetItem(entry.get("actor", "")))
            self.audit_table.setItem(row_idx, 3, QTableWidgetItem(entry.get("timestamp", "")))

            hash_val = entry.get("hash", "")
            hash_item = QTableWidgetItem(hash_val[:16] + "..." if len(hash_val) > 16 else hash_val)
            hash_item.setToolTip(hash_val)
            self.audit_table.setItem(row_idx, 4, hash_item)

            leaf = entry.get("merkle_leaf", "") or ""
            leaf_item = QTableWidgetItem(leaf[:16] + "..." if len(leaf) > 16 else leaf)
            leaf_item.setToolTip(leaf)
            self.audit_table.setItem(row_idx, 5, leaf_item)

    def _refresh_audit_verify(self):
        # aqui estou verificando a cadeia de auditoria
        data = self.java_client.db_audit_verify(self.current_instance_id)
        if not data:
            return

        self.card_chain_total.set_value(str(data.get("total_entries", 0)))
        self.card_chain_valid.set_value(str(data.get("valid_entries", 0)))

        broken = data.get("broken_entries", 0)
        self.card_chain_broken.set_value(str(broken))
        if broken > 0:
            self.card_chain_broken.value_lbl.setStyleSheet(
                "color: #ef4444; font-size: 14pt; font-weight: bold;"
            )
        else:
            self.card_chain_broken.value_lbl.setStyleSheet(
                "color: #22c55e; font-size: 14pt; font-weight: bold;"
            )

        chain_intact = data.get("chain_intact", False)
        if chain_intact:
            self.card_chain_status.set_value("INTEGRA")
            self.card_chain_status.value_lbl.setStyleSheet(
                "color: #22c55e; font-size: 14pt; font-weight: bold;"
            )
        else:
            self.card_chain_status.set_value("COMPROMETIDA")
            self.card_chain_status.value_lbl.setStyleSheet(
                "color: #ef4444; font-size: 14pt; font-weight: bold;"
            )


from datetime import datetime
from typing import Optional

from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QGroupBox,
    QLabel, QTableWidget, QTableWidgetItem, QPushButton,
    QHeaderView, QProgressBar, QGridLayout, QComboBox
)
from PySide6.QtCore import Qt, QTimer
from PySide6.QtGui import QColor

from .overview_panel import MetricCard


class DatabasePanel(QWidget):
    """Painel de monitoramento do banco de dados.

    Seções:
        - Database Health: conexões, latência, tamanho do banco
        - Merkle Integrity: validação da árvore, divergências
        - Audit Monitor: eventos recentes, alertas
    """

    def __init__(self, java_client, parent=None):
        super().__init__(parent)
        self.java_client = java_client
        self._setup_ui()

        # Auto-refresh timer (every 10 seconds)
        self._timer = QTimer(self)
        self._timer.timeout.connect(self.refresh_all)
        self._timer.setInterval(10_000)

    # ──────────────── UI Setup ────────────────

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setSpacing(12)

        # ── Top toolbar ──
        toolbar = QHBoxLayout()
        self.refresh_btn = QPushButton("Atualizar")
        self.refresh_btn.clicked.connect(self.refresh_all)
        toolbar.addWidget(self.refresh_btn)

        self.auto_refresh_btn = QPushButton("Auto-refresh")
        self.auto_refresh_btn.setCheckable(True)
        self.auto_refresh_btn.toggled.connect(self._toggle_auto_refresh)
        toolbar.addWidget(self.auto_refresh_btn)

        toolbar.addStretch()

        self.status_label = QLabel("Aguardando dados...")
        self.status_label.setStyleSheet("color: #64748b;")
        toolbar.addWidget(self.status_label)

        layout.addLayout(toolbar)

        # ── 1. Database Health ──
        health_group = QGroupBox("Database Health")
        health_layout = QGridLayout(health_group)

        self.card_type = MetricCard("Tipo DB", "-", "#38bdf8")
        health_layout.addWidget(self.card_type, 0, 0)

        self.card_connected = MetricCard("Status", "-", "#22c55e")
        health_layout.addWidget(self.card_connected, 0, 1)

        self.card_latency = MetricCard("Latência", "-", "#f59e0b")
        health_layout.addWidget(self.card_latency, 0, 2)

        self.card_connections = MetricCard("Conexões Ativas", "-", "#8b5cf6")
        health_layout.addWidget(self.card_connections, 0, 3)

        self.card_size = MetricCard("Tamanho DB", "-", "#06b6d4")
        health_layout.addWidget(self.card_size, 1, 0)

        self.card_ssl = MetricCard("SSL/TLS", "-", "#64748b")
        health_layout.addWidget(self.card_ssl, 1, 1)

        self.card_version = MetricCard("Versão DB", "-", "#64748b")
        health_layout.addWidget(self.card_version, 1, 2)

        self.card_pool = MetricCard("Pool Size", "-", "#64748b")
        health_layout.addWidget(self.card_pool, 1, 3)

        # Table counts bar
        self.tables_group = QGroupBox("Tabelas")
        tables_layout = QVBoxLayout(self.tables_group)
        self.table_labels = {}
        for name in ["users", "audit_logs", "merkle_nodes", "probability_models", "decoy_operations"]:
            row = QHBoxLayout()
            lbl = QLabel(name)
            lbl.setMinimumWidth(160)
            lbl.setStyleSheet("font-family: Consolas; color: #94a3b8;")
            row.addWidget(lbl)
            bar = QProgressBar()
            bar.setRange(0, 1000)
            bar.setFormat("%v registros")
            bar.setStyleSheet("QProgressBar::chunk { background-color: #38bdf8; }")
            row.addWidget(bar)
            tables_layout.addLayout(row)
            self.table_labels[name] = bar

        health_layout.addWidget(self.tables_group, 2, 0, 1, 4)
        layout.addWidget(health_group)

        # ── 2. Merkle Integrity ──
        merkle_group = QGroupBox("Merkle Integrity")
        merkle_layout = QGridLayout(merkle_group)

        self.card_stored_root = MetricCard("Root Armazenado", "-", "#8b5cf6")
        merkle_layout.addWidget(self.card_stored_root, 0, 0)

        self.card_live_root = MetricCard("Root Live", "-", "#10b981")
        merkle_layout.addWidget(self.card_live_root, 0, 1)

        self.card_roots_match = MetricCard("Integridade", "-", "#64748b")
        merkle_layout.addWidget(self.card_roots_match, 0, 2)

        self.card_tree_ver = MetricCard("Versão Árvore", "-", "#f59e0b")
        merkle_layout.addWidget(self.card_tree_ver, 0, 3)

        self.card_total_nodes = MetricCard("Total Nós", "-", "#06b6d4")
        merkle_layout.addWidget(self.card_total_nodes, 1, 0)

        self.verify_merkle_btn = QPushButton("Verificar Merkle")
        self.verify_merkle_btn.clicked.connect(self._refresh_merkle)
        merkle_layout.addWidget(self.verify_merkle_btn, 1, 3)

        layout.addWidget(merkle_group)

        # ── 3. Audit Monitor ──
        audit_group = QGroupBox("Audit Monitor")
        audit_layout = QVBoxLayout(audit_group)

        # Chain integrity summary
        chain_row = QHBoxLayout()
        self.card_chain_total = MetricCard("Total Entradas", "-", "#38bdf8")
        chain_row.addWidget(self.card_chain_total)
        self.card_chain_valid = MetricCard("Válidas", "-", "#22c55e")
        chain_row.addWidget(self.card_chain_valid)
        self.card_chain_broken = MetricCard("Quebradas", "-", "#ef4444")
        chain_row.addWidget(self.card_chain_broken)
        self.card_chain_status = MetricCard("Cadeia", "-", "#64748b")
        chain_row.addWidget(self.card_chain_status)
        audit_layout.addLayout(chain_row)

        # Verify button
        btn_row = QHBoxLayout()
        self.verify_chain_btn = QPushButton("Verificar Cadeia de Auditoria")
        self.verify_chain_btn.clicked.connect(self._refresh_audit_verify)
        btn_row.addWidget(self.verify_chain_btn)
        btn_row.addStretch()
        audit_layout.addLayout(btn_row)

        # Recent audit logs table
        audit_layout.addWidget(QLabel("<b>Eventos Recentes:</b>"))
        self.audit_table = QTableWidget()
        self.audit_table.setColumnCount(6)
        self.audit_table.setHorizontalHeaderLabels(
            ["ID", "Ação", "Ator", "Timestamp", "Hash", "Merkle Leaf"]
        )
        self.audit_table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.audit_table.setAlternatingRowColors(True)
        self.audit_table.setEditTriggers(QTableWidget.NoEditTriggers)
        self.audit_table.setSelectionBehavior(QTableWidget.SelectRows)
        self.audit_table.setMaximumHeight(250)
        audit_layout.addWidget(self.audit_table)

        layout.addWidget(audit_group)
        layout.addStretch()

    # ──────────────── Refresh Logic ────────────────

    def refresh_all(self):
        """Atualiza todos os dados do painel."""
        self._refresh_health()
        self._refresh_merkle()
        self._refresh_audit_logs()
        self._refresh_audit_verify()
        self.status_label.setText(f"Atualizado: {datetime.now():%H:%M:%S}")

    def _toggle_auto_refresh(self, checked: bool):
        if checked:
            self._timer.start()
            self.auto_refresh_btn.setText("Parar auto-refresh")
        else:
            self._timer.stop()
            self.auto_refresh_btn.setText("Auto-refresh")

    def _refresh_health(self):
        data = self.java_client.db_health()
        if not data:
            self.card_connected.set_value("OFFLINE")
            self.card_connected.value_lbl.setStyleSheet(
                "color: #ef4444; font-size: 14pt; font-weight: bold;"
            )
            return

        connected = data.get("connected", False)
        self.card_connected.set_value("ONLINE" if connected else "OFFLINE")
        self.card_connected.value_lbl.setStyleSheet(
            f"color: {'#22c55e' if connected else '#ef4444'}; font-size: 14pt; font-weight: bold;"
        )

        self.card_type.set_value(data.get("db_type", "-").upper())
        self.card_latency.set_value(f"{data.get('latency_ms', '-')} ms")
        self.card_connections.set_value(str(data.get("active_connections", "-")))
        self.card_size.set_value(f"{data.get('db_size_mb', '-')} MB")
        self.card_ssl.set_value("ON" if data.get("ssl_enabled") else "OFF")
        self.card_version.set_value(str(data.get("db_version", "-"))[:20])

        # Table counts
        counts = data.get("table_counts", {})
        for name, bar in self.table_labels.items():
            count = counts.get(name, 0)
            if count < 0:
                count = 0
            bar.setMaximum(max(count, 1))
            bar.setValue(count)
            bar.setFormat(f"{count} registros")

    def _refresh_merkle(self):
        data = self.java_client.db_merkle_integrity()
        if not data:
            return

        stored = data.get("stored_root", "-") or "-"
        live = data.get("live_root", "-") or "-"
        match = data.get("roots_match", False)

        self.card_stored_root.set_value(stored[:16] + "..." if len(stored) > 16 else stored)
        self.card_live_root.set_value(live[:16] + "..." if len(live) > 16 else live)

        if match:
            self.card_roots_match.set_value("INTEGRO")
            self.card_roots_match.value_lbl.setStyleSheet(
                "color: #22c55e; font-size: 14pt; font-weight: bold;"
            )
        else:
            self.card_roots_match.set_value("DIVERGENCIA")
            self.card_roots_match.value_lbl.setStyleSheet(
                "color: #ef4444; font-size: 14pt; font-weight: bold;"
            )

        self.card_tree_ver.set_value(str(data.get("latest_tree_version", "-")))
        self.card_total_nodes.set_value(str(data.get("total_stored_nodes", "-")))

    def _refresh_audit_logs(self):
        data = self.java_client.db_audit_logs(limit=20)
        if not data:
            self.audit_table.setRowCount(0)
            return

        self.audit_table.setRowCount(len(data))
        for row_idx, entry in enumerate(data):
            self.audit_table.setItem(row_idx, 0, QTableWidgetItem(str(entry.get("id", ""))))
            self.audit_table.setItem(row_idx, 1, QTableWidgetItem(entry.get("action", "")))
            self.audit_table.setItem(row_idx, 2, QTableWidgetItem(entry.get("actor", "")))
            self.audit_table.setItem(row_idx, 3, QTableWidgetItem(entry.get("timestamp", "")))

            hash_val = entry.get("hash", "")
            hash_item = QTableWidgetItem(hash_val[:16] + "..." if len(hash_val) > 16 else hash_val)
            hash_item.setToolTip(hash_val)
            self.audit_table.setItem(row_idx, 4, hash_item)

            leaf = entry.get("merkle_leaf", "") or ""
            leaf_item = QTableWidgetItem(leaf[:16] + "..." if len(leaf) > 16 else leaf)
            leaf_item.setToolTip(leaf)
            self.audit_table.setItem(row_idx, 5, leaf_item)

    def _refresh_audit_verify(self):
        data = self.java_client.db_audit_verify()
        if not data:
            return

        self.card_chain_total.set_value(str(data.get("total_entries", 0)))
        self.card_chain_valid.set_value(str(data.get("valid_entries", 0)))

        broken = data.get("broken_entries", 0)
        self.card_chain_broken.set_value(str(broken))
        if broken > 0:
            self.card_chain_broken.value_lbl.setStyleSheet(
                "color: #ef4444; font-size: 14pt; font-weight: bold;"
            )
        else:
            self.card_chain_broken.value_lbl.setStyleSheet(
                "color: #22c55e; font-size: 14pt; font-weight: bold;"
            )

        chain_intact = data.get("chain_intact", False)
        if chain_intact:
            self.card_chain_status.set_value("INTEGRA")
            self.card_chain_status.value_lbl.setStyleSheet(
                "color: #22c55e; font-size: 14pt; font-weight: bold;"
            )
        else:
            self.card_chain_status.set_value("COMPROMETIDA")
            self.card_chain_status.value_lbl.setStyleSheet(
                "color: #ef4444; font-size: 14pt; font-weight: bold;"
            )
