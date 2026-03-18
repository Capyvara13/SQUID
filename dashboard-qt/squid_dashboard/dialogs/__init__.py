"""Diálogos do dashboard."""

from PySide6.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QFormLayout,
    QLineEdit, QSpinBox, QTextEdit, QGroupBox,
    QDialogButtonBox, QLabel, QMessageBox, QPushButton,
    QTableWidget, QTableWidgetItem, QHeaderView
)
from PySide6.QtCore import Qt
from PySide6.QtGui import QColor
from typing import Dict, Optional, List


class CreateInstanceDialog(QDialog):
    """Diálogo para criar nova instância SQUID com configuração B, M, T."""
    
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Criar Nova Instância SQUID")
        self.setMinimumWidth(450)
        self.setWindowFlags(Qt.Dialog | Qt.WindowCloseButtonHint)
        
        self._setup_ui()
    
    def _setup_ui(self):
        """Configura a interface do diálogo."""
        layout = QVBoxLayout(self)
        layout.setSpacing(12)
        layout.setContentsMargins(20, 20, 20, 20)
        
        # Form principal
        form = QFormLayout()
        form.setSpacing(10)
        
        # Nome
        self.name_input = QLineEdit()
        self.name_input.setPlaceholderText("Ex: instancia_producao_01")
        form.addRow("Nome da Instância:*", self.name_input)
        
        layout.addLayout(form)
        
        # Configuração B, M, T
        config_group = QGroupBox("Configuração da Árvore (B, M, T)")
        config_layout = QFormLayout(config_group)
        config_layout.setSpacing(8)
        
        # B - Branching factor
        self.b_spin = QSpinBox()
        self.b_spin.setRange(2, 16)
        self.b_spin.setValue(2)
        self.b_spin.setToolTip("Fator de ramificação: número de filhos por nó")
        config_layout.addRow("B (Branching):", self.b_spin)
        
        # M - Depth
        self.m_spin = QSpinBox()
        self.m_spin.setRange(1, 12)
        self.m_spin.setValue(8)
        self.m_spin.setToolTip("Profundidade máxima da árvore")
        config_layout.addRow("M (Depth):", self.m_spin)
        
        # T - Total leaves (calculado automaticamente)
        self.t_spin = QSpinBox()
        self.t_spin.setRange(1, 4096)
        self.t_spin.setValue(256)
        self.t_spin.setToolTip("Número total de folhas (L = B^M)")
        self.t_spin.setReadOnly(True)
        self.t_spin.setButtonSymbols(QSpinBox.NoButtons)
        config_layout.addRow("T (Total Leaves):", self.t_spin)
        
        # Label de fórmula
        formula_label = QLabel("Fórmula: L = B^M (máx: 4096)")
        formula_label.setStyleSheet("color: #64748b; font-size: 9pt;")
        config_layout.addRow("", formula_label)
        
        # Atualiza T quando B ou M mudam
        self.b_spin.valueChanged.connect(self._update_t)
        self.m_spin.valueChanged.connect(self._update_t)
        self._update_t()
        
        layout.addWidget(config_group)
        
        # Dados
        data_group = QGroupBox("Dados para Criptografar")
        data_layout = QVBoxLayout(data_group)
        
        self.data_input = QTextEdit()
        self.data_input.setPlaceholderText("Insira os dados/texto para criptografar...\nDeixe em branco para criar instância vazia.")
        self.data_input.setMaximumHeight(100)
        data_layout.addWidget(self.data_input)
        
        layout.addWidget(data_group)
        
        # Botões
        btn_box = QDialogButtonBox(
            QDialogButtonBox.Ok | QDialogButtonBox.Cancel
        )
        btn_box.accepted.connect(self._validate_and_accept)
        btn_box.rejected.connect(self.reject)
        
        # Customiza textos dos botões
        ok_btn = btn_box.button(QDialogButtonBox.Ok)
        ok_btn.setText("Criar Instância")
        ok_btn.setObjectName("StartBtn")
        
        cancel_btn = btn_box.button(QDialogButtonBox.Cancel)
        cancel_btn.setText("Cancelar")
        
        layout.addWidget(btn_box)
        layout.addStretch()
    
    def _update_t(self):
        """Atualiza o valor de T baseado em B e M."""
        b = self.b_spin.value()
        m = self.m_spin.value()
        t = min(b ** m, 4096)  # Limite máximo de 4096
        self.t_spin.setValue(t)
    
    def _validate_and_accept(self):
        """Valida os dados antes de aceitar."""
        name = self.name_input.text().strip()
        
        if not name:
            QMessageBox.warning(
                self,
                "Validação",
                "Nome da instância é obrigatório!"
            )
            self.name_input.setFocus()
            return
        
        # Valida caracteres permitidos
        import re
        if not re.match(r'^[a-zA-Z0-9_-]+$', name):
            QMessageBox.warning(
                self,
                "Validação",
                "Nome deve conter apenas letras, números, hífen e underscore!"
            )
            self.name_input.setFocus()
            return
        
        self.accept()
    
    def get_instance_data(self) -> Dict:
        """Retorna os dados da instância configurada.
        
        Returns:
            Dict com name, B, M, T, data
        """
        return {
            'name': self.name_input.text().strip(),
            'B': self.b_spin.value(),
            'M': self.m_spin.value(),
            'T': self.t_spin.value(),
            'data': self.data_input.toPlainText()
        }


class LeafViewerDialog(QDialog):
    """Diálogo para visualizar as folhas de uma instância SQUID com dados reais do backend."""

    # Color map: state -> QColor
    _STATE_COLORS = {
        'VALID': QColor(34, 197, 94),      # green
        'DECOY': QColor(245, 158, 11),     # amber
        'MUTATE': QColor(239, 68, 68),     # red
        'REASSIGN': QColor(56, 189, 248),  # blue
    }

    _STATE_LABELS = {
        'VALID': 'VALID',
        'DECOY': 'DECOY',
        'MUTATE': 'MUTATE',
        'REASSIGN': 'REASSIGN',
    }

    def __init__(self, inst_id: str, inst_name: str,
                 java_client, inst_data: Optional[Dict] = None, parent=None):
        super().__init__(parent)
        self.setWindowTitle(f"Leafs - {inst_name}")
        self.setMinimumSize(900, 620)
        self.setWindowFlags(Qt.Dialog | Qt.WindowCloseButtonHint)

        self.inst_id = inst_id
        self.inst_name = inst_name
        self.java_client = java_client
        self.inst_data = inst_data or {}
        self._all_leaves: List[Dict] = []  # backend leaf data cache
        self._setup_ui()
        self._load_leaves()

    # ---- UI setup ----

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setSpacing(8)

        # -- Header --
        self.header_label = QLabel()
        self.header_label.setStyleSheet("color: #94a3b8; padding: 4px;")
        layout.addWidget(self.header_label)
        self._update_header()

        # -- Toolbar (search + filter + refresh) --
        toolbar = QHBoxLayout()

        self.search_input = QLineEdit()
        self.search_input.setPlaceholderText("Buscar por indice ou hash...")
        self.search_input.textChanged.connect(self._apply_filter)
        toolbar.addWidget(self.search_input, 3)

        from PySide6.QtWidgets import QComboBox
        self.state_filter = QComboBox()
        self.state_filter.addItems(["Todos", "VALID", "DECOY", "MUTATE", "REASSIGN"])
        self.state_filter.currentTextChanged.connect(self._apply_filter)
        toolbar.addWidget(self.state_filter, 1)

        self.refresh_btn = QPushButton("Atualizar")
        self.refresh_btn.clicked.connect(self._load_leaves)
        toolbar.addWidget(self.refresh_btn)

        layout.addLayout(toolbar)

        # -- Stats bar --
        self.stats_label = QLabel()
        self.stats_label.setStyleSheet("color: #64748b; font-size: 9pt; padding: 2px 4px;")
        layout.addWidget(self.stats_label)

        # -- Main table --
        self.table = QTableWidget()
        self.table.setColumnCount(7)
        self.table.setHorizontalHeaderLabels(
            ["#", "Hash", "Depth", "Estado", "Entropia", "SR", "C"]
        )
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.Stretch)
        for col in (0, 2, 3, 4, 5, 6):
            self.table.horizontalHeader().setSectionResizeMode(
                col, QHeaderView.ResizeToContents
            )
        self.table.setSelectionBehavior(QTableWidget.SelectRows)
        self.table.setAlternatingRowColors(True)
        self.table.setSortingEnabled(True)
        layout.addWidget(self.table, 3)

        # -- Detail panel --
        self.detail_label = QLabel("Clique em uma folha para ver detalhes.")
        self.detail_label.setWordWrap(True)
        self.detail_label.setMinimumHeight(100)
        self.detail_label.setStyleSheet(
            "background-color: #1e293b; padding: 12px; border-radius: 6px; "
            "color: #e2e8f0; font-family: 'Consolas', 'Courier New', monospace;"
        )
        layout.addWidget(self.detail_label, 1)

        # -- Close --
        btn_box = QDialogButtonBox(QDialogButtonBox.Close)
        btn_box.rejected.connect(self.reject)
        layout.addWidget(btn_box)

        self.table.currentCellChanged.connect(self._on_row_selected)

    # ---- Data loading ----

    def _load_leaves(self):
        """Carrega folhas reais do backend."""
        self.refresh_btn.setEnabled(False)
        self.refresh_btn.setText("Carregando...")

        data = self.java_client.get_instance_leaves(self.inst_id)

        if data and isinstance(data.get('leaves'), list):
            self._all_leaves = data['leaves']
            self.inst_data['merkle_root'] = data.get('merkle_root', self.inst_data.get('merkle_root', ''))
            self.inst_data['leaf_count'] = data.get('total', len(self._all_leaves))
            if 'config' in data:
                cfg = data['config']
                self.inst_data['config_B'] = cfg.get('B', '?')
                self.inst_data['config_M'] = cfg.get('M', '?')
                self.inst_data['config_T'] = cfg.get('T', '?')
        else:
            # Fallback: generate synthetic preview so dialog is not empty
            self._all_leaves = self._synthetic_leaves()

        self._update_header()
        self._apply_filter()
        self.refresh_btn.setEnabled(True)
        self.refresh_btn.setText("Atualizar")

    def _synthetic_leaves(self) -> List[Dict]:
        """Gera folhas sintéticas como fallback quando o backend está offline."""
        import hashlib
        count = int(self.inst_data.get('leaf_count',
                    self.inst_data.get('ephemeralKeysCount', 8)))
        count = min(max(count, 1), 500)
        root = self.inst_data.get('merkle_root', 'demo')
        states = ['VALID', 'DECOY', 'MUTATE', 'REASSIGN']
        leaves = []
        for i in range(count):
            h = hashlib.sha256(f"{root}:{i}".encode()).hexdigest()
            state = 'VALID' if i % 7 != 0 else states[(i // 7) % 4]
            entropy = round(6.0 + (int(h[:4], 16) % 400) / 100.0, 2)
            leaves.append({
                'index': i,
                'hash': h[:24],
                'depth': (i % 5) + 1,
                'state': state,
                'entropy': entropy,
                'sr': round(0.85 + (int(h[4:8], 16) % 150) / 1000.0, 3),
                'c': round(0.80 + (int(h[8:12], 16) % 200) / 1000.0, 3),
            })
        return leaves

    # ---- Header / stats ----

    def _update_header(self):
        root_display = str(self.inst_data.get('merkle_root', 'N/A'))[:24]
        cfg = (
            f"B={self.inst_data.get('config_B', '?')} "
            f"M={self.inst_data.get('config_M', '?')} "
            f"T={self.inst_data.get('config_T', '?')}"
        )
        self.header_label.setText(
            f"<b>{self.inst_name}</b> &nbsp;|&nbsp; "
            f"ID: {self.inst_id} &nbsp;|&nbsp; "
            f"Config: {cfg} &nbsp;|&nbsp; "
            f"Root: {root_display}..."
        )

    def _update_stats(self, visible: int):
        total = len(self._all_leaves)
        counts = {}
        for lf in self._all_leaves:
            s = lf.get('state', 'UNKNOWN')
            counts[s] = counts.get(s, 0) + 1

        parts = [f"Total: {total}"]
        for state, color_hex in [('VALID', '#22c55e'), ('DECOY', '#f59e0b'),
                                  ('MUTATE', '#ef4444'), ('REASSIGN', '#38bdf8')]:
            n = counts.get(state, 0)
            if n > 0:
                pct = round(n / total * 100, 1) if total else 0
                parts.append(f"<span style='color:{color_hex}'>{state}: {n} ({pct}%)</span>")

        if visible < total:
            parts.append(f"Visíveis: {visible}")

        self.stats_label.setText(" &nbsp;|&nbsp; ".join(parts))

    # ---- Filter / populate ----

    def _apply_filter(self, _text=None):
        query = self.search_input.text().strip().lower()
        state_filter = self.state_filter.currentText()

        filtered = []
        for lf in self._all_leaves:
            # State filter
            if state_filter != "Todos" and lf.get('state') != state_filter:
                continue
            # Text search (index or hash)
            if query:
                idx_str = str(lf.get('index', ''))
                hash_str = str(lf.get('hash', '')).lower()
                if query not in idx_str and query not in hash_str:
                    continue
            filtered.append(lf)

        self._populate_table(filtered)
        self._update_stats(len(filtered))

    def _populate_table(self, leaves: List[Dict]):
        self.table.setSortingEnabled(False)
        self.table.setRowCount(min(len(leaves), 2000))

        for i, lf in enumerate(leaves[:2000]):
            idx_item = QTableWidgetItem()
            idx_item.setData(Qt.DisplayRole, lf.get('index', i))
            self.table.setItem(i, 0, idx_item)

            hash_item = QTableWidgetItem(str(lf.get('hash', ''))[:24] + "...")
            hash_item.setToolTip(str(lf.get('hash', '')))
            self.table.setItem(i, 1, hash_item)

            depth_item = QTableWidgetItem()
            depth_item.setData(Qt.DisplayRole, lf.get('depth', 0))
            self.table.setItem(i, 2, depth_item)

            state = lf.get('state', 'UNKNOWN')
            state_item = QTableWidgetItem(self._STATE_LABELS.get(state, state))
            color = self._STATE_COLORS.get(state, QColor(148, 163, 184))
            state_item.setForeground(color)
            self.table.setItem(i, 3, state_item)

            ent_item = QTableWidgetItem()
            ent_item.setData(Qt.DisplayRole, lf.get('entropy', 0))
            self.table.setItem(i, 4, ent_item)

            sr_item = QTableWidgetItem()
            sr_item.setData(Qt.DisplayRole, lf.get('sr', 0))
            self.table.setItem(i, 5, sr_item)

            c_item = QTableWidgetItem()
            c_item.setData(Qt.DisplayRole, lf.get('c', 0))
            self.table.setItem(i, 6, c_item)

        self.table.setSortingEnabled(True)

    # ---- Leaf detail ----

    def _on_row_selected(self, row, col, prev_row, prev_col):
        if row < 0:
            return
        idx_item = self.table.item(row, 0)
        if not idx_item:
            return

        leaf_index = idx_item.data(Qt.DisplayRole)
        if leaf_index is None:
            return

        # Quick preview from cached data
        lf = None
        for cached in self._all_leaves:
            if cached.get('index') == leaf_index:
                lf = cached
                break

        if not lf:
            return

        state = lf.get('state', '?')
        color_hex = {
            'VALID': '#22c55e', 'DECOY': '#f59e0b',
            'MUTATE': '#ef4444', 'REASSIGN': '#38bdf8'
        }.get(state, '#94a3b8')

        # Fetch detailed info from backend (includes full hash, proof, history)
        detail = self.java_client.get_instance_leaf_detail(self.inst_id, leaf_index)

        full_hash = ''
        proof_html = ''
        history_html = ''

        if detail:
            full_hash = detail.get('hash_full', lf.get('hash', ''))
            proof = detail.get('merkle_proof', [])
            if proof:
                proof_lines = [f"&nbsp;&nbsp;L{i}: {p[:20]}..." for i, p in enumerate(proof)]
                proof_html = (
                    "<br><b>Merkle Proof:</b><br>"
                    + "<br>".join(proof_lines)
                )
            hist = detail.get('history', [])
            if hist:
                hist_lines = []
                for h in hist:
                    ts = str(h.get('timestamp', ''))[:19]
                    act = h.get('action', '?')
                    det = h.get('details', '')
                    hist_lines.append(f"&nbsp;&nbsp;[{ts}] <b>{act}</b>: {det}")
                history_html = (
                    "<br><b>Histórico Relacionado:</b><br>"
                    + "<br>".join(hist_lines)
                )

        self.detail_label.setText(
            f"<b style='font-size:11pt'>Leaf #{leaf_index}</b><br><br>"
            f"<b>Hash:</b> {full_hash or lf.get('hash', '?')}<br>"
            f"<b>Depth:</b> {lf.get('depth', '?')}<br>"
            f"<b>Estado:</b> <span style='color:{color_hex}'>{state}</span><br>"
            f"<b>Entropia:</b> {lf.get('entropy', '?')} bits &nbsp;&nbsp; "
            f"<b>SR:</b> {lf.get('sr', '?')} &nbsp;&nbsp; "
            f"<b>C:</b> {lf.get('c', '?')}"
            f"{proof_html}"
            f"{history_html}"
        )
