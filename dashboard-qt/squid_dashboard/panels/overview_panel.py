"""Painel de visao geral e integridade global."""

from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QGroupBox,
    QLabel, QLineEdit, QProgressBar, QGridLayout, QComboBox
)

from ..models import ServiceStatus


class MetricCard(QWidget):
    """Card de metrica."""
    
    def __init__(self, title: str, value: str = "-", color: str = "#38bdf8", parent=None):
        super().__init__(parent)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 8, 12, 8)
        layout.setSpacing(4)
        
        self.title_lbl = QLabel(title)
        self.title_lbl.setStyleSheet("color: #64748b; font-size: 9pt;")
        layout.addWidget(self.title_lbl)
        
        self.value_lbl = QLabel(value)
        self.value_lbl.setStyleSheet(f"color: {color}; font-size: 14pt; font-weight: bold;")
        layout.addWidget(self.value_lbl)
        
        self.setStyleSheet("background-color: #1e293b; border-radius: 6px;")
    
    def set_value(self, value: str):
        self.value_lbl.setText(value)


class OverviewPanel(QWidget):
    """Painel de visao geral do sistema."""
    
    def __init__(self, parent=None):
        super().__init__(parent)
        self.current_instance_id = None
        self._setup_ui()
    
    def _setup_ui(self):
        # aqui estou configurando a interface do painel
        layout = QVBoxLayout(self)
        layout.setSpacing(15)
        
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
        
        # Integridade Global
        integrity_group = QGroupBox("Integridade Global (Merkle-of-Merkles)")
        integrity_layout = QVBoxLayout(integrity_group)
        
        # Root hash
        self.root_display = QLineEdit()
        self.root_display.setReadOnly(True)
        self.root_display.setText("Aguardando dados...")
        integrity_layout.addWidget(QLabel("<b>Hash Root Global:</b>"))
        integrity_layout.addWidget(self.root_display)
        
        # Componentes
        comps_layout = QHBoxLayout()
        
        self.dynamic_card = MetricCard("Arvore Dinamica", "Aguardando...", "#8b5cf6")
        comps_layout.addWidget(self.dynamic_card)
        
        self.iterative_card = MetricCard("Seed Iterativo", "Aguardando...", "#10b981")
        comps_layout.addWidget(self.iterative_card)
        
        self.ops_card = MetricCard("Operacoes", "Aguardando...", "#f59e0b")
        comps_layout.addWidget(self.ops_card)
        
        self.ai_card = MetricCard("Hash IA", "Aguardando...", "#ef4444")
        comps_layout.addWidget(self.ai_card)
        
        integrity_layout.addLayout(comps_layout)
        
        # Entropia
        self.entropy_bar = QProgressBar()
        self.entropy_bar.setRange(0, 100)
        self.entropy_bar.setFormat("Orcamento de Entropia: %p%")
        integrity_layout.addWidget(self.entropy_bar)
        
        layout.addWidget(integrity_group)
        
        # Status dos servicos
        status_group = QGroupBox("Status dos Componentes")
        status_layout = QGridLayout(status_group)
        
        self.pqc_card = MetricCard("PQC (Kyber+Dilithium)", "Verificando...", "#64748b")
        status_layout.addWidget(self.pqc_card, 0, 0)
        
        self.fp_card = MetricCard("Modo Fingerprint", "Verificando...", "#64748b")
        status_layout.addWidget(self.fp_card, 0, 1)
        
        self.merkle_card = MetricCard("Estado Merkle", "Verificando...", "#64748b")
        status_layout.addWidget(self.merkle_card, 0, 2)
        
        layout.addWidget(status_group)
        
        # Informacoes
        info_group = QGroupBox("Informacoes")
        info_layout = QVBoxLayout(info_group)
        
        info_text = QLabel(
            "<b>SQUID Dashboard v3.0</b><br>"
            "Sistema de Criptografia Pos-Quantica com IA<br><br>"
            "<b>Componentes:</b><br>"
            "• Java Backend (porta 8080) - API REST e criptografia Kyber/Dilithium<br>"
            "• Python IA (porta 5000) - Modelo PyTorch e decisoes SR/C<br>"
            "• IPC Daemon - Comunicacao entre processos<br><br>"
            "<b>Funcionalidades:</b><br>"
            "• Instancias SQUID com arvores Merkle configuraveis<br>"
            "• Criptografia hibrida pos-quantica<br>"
            "• IA para rotacao e protecao de dados<br>"
            "• Monitoramento de hardware em tempo real"
        )
        info_text.setWordWrap(True)
        info_layout.addWidget(info_text)
        
        layout.addWidget(info_group)
        layout.addStretch()
    
    def _refresh_instance_list(self):
        # aqui estou atualizando a lista de instancias disponiveis
        from ..clients import JavaClient
        client = JavaClient()
        
        def on_complete(instances):
            self.instance_combo.clear()
            if instances:
                for instance in instances:
                    self.instance_combo.addItem(instance.get("id", "unknown"))
        
        # worker para busca assincrona
        from ..workers import AsyncActionWorker
        def fetch():
            return client.get_instances()
        
        worker = AsyncActionWorker(fetch)
        worker.finished.connect(on_complete)
        worker.start()
    
    def _on_instance_changed(self, instance_id: str):
        # quando o usuario muda a instancia selecionada
        if instance_id:
            self.current_instance_id = instance_id
            # atualiza dados da instancia
    
    def update_from_telemetry(self, data: dict):
        # aqui estou atualizando o display com dados de telemetria
        # dados vem no formato: health, global_root
        
        # Global root
        if "global_root" in data:
            groot = data["global_root"]
            
            if "globalRoot" in groot:
                self.root_display.setText(groot["globalRoot"])
            
            if "dynamicRoot" in groot:
                val = groot["dynamicRoot"]
                self.dynamic_card.set_value(val[:16] + "..." if len(val) > 16 else val)
            
            if "iterativeRoots" in groot:
                count = len(groot["iterativeRoots"]) if groot["iterativeRoots"] else 0
                self.iterative_card.set_value(f"{count} niveis")
            
            if "operationRoots" in groot:
                count = len(groot["operationRoots"]) if groot["operationRoots"] else 0
                self.ops_card.set_value(f"{count} ops")
            
            if "aiDecisionHash" in groot:
                val = groot["aiDecisionHash"]
                self.ai_card.set_value(val[:16] + "..." if val else "N/A")
            
            if "entropyBudget" in groot:
                val = float(groot["entropyBudget"])
                self.entropy_bar.setValue(int(val))
                
                if val < 20:
                    self.entropy_bar.setStyleSheet(
                        "QProgressBar::chunk { background-color: #ef4444; }"
                    )
                else:
                    self.entropy_bar.setStyleSheet(
                        "QProgressBar::chunk { background-color: #38bdf8; }"
                    )
        
        # Health status
        if "health" in data:
            health = data["health"]
            
            crypto_status = health.get("cryptography_status", "OFFLINE")
            self.pqc_card.set_value(crypto_status)
            
            if crypto_status == "ONLINE":
                self.pqc_card.value_lbl.setStyleSheet("color: #22c55e; font-size: 14pt; font-weight: bold;")
            else:
                self.pqc_card.value_lbl.setStyleSheet("color: #ef4444; font-size: 14pt; font-weight: bold;")
            
            self.fp_card.set_value(health.get("fingerprint_mode", "-"))
            self.merkle_card.set_value(health.get("merkle_state", "-"))


from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QGroupBox,
    QLabel, QLineEdit, QProgressBar, QGridLayout
)

from ..models import ServiceStatus


class MetricCard(QWidget):
    """Card de métrica."""
    
    def __init__(self, title: str, value: str = "-", color: str = "#38bdf8", parent=None):
        super().__init__(parent)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 8, 12, 8)
        layout.setSpacing(4)
        
        self.title_lbl = QLabel(title)
        self.title_lbl.setStyleSheet("color: #64748b; font-size: 9pt;")
        layout.addWidget(self.title_lbl)
        
        self.value_lbl = QLabel(value)
        self.value_lbl.setStyleSheet(f"color: {color}; font-size: 14pt; font-weight: bold;")
        layout.addWidget(self.value_lbl)
        
        self.setStyleSheet("background-color: #1e293b; border-radius: 6px;")
    
    def set_value(self, value: str):
        self.value_lbl.setText(value)


class OverviewPanel(QWidget):
    """Painel de visão geral do sistema."""
    
    def __init__(self, parent=None):
        super().__init__(parent)
        self._setup_ui()
    
    def _setup_ui(self):
        """Configura a interface."""
        layout = QVBoxLayout(self)
        layout.setSpacing(15)
        
        # Integridade Global
        integrity_group = QGroupBox("Integridade Global (Merkle-of-Merkles)")
        integrity_layout = QVBoxLayout(integrity_group)
        
        # Root hash
        self.root_display = QLineEdit()
        self.root_display.setReadOnly(True)
        self.root_display.setText("Aguardando dados...")
        integrity_layout.addWidget(QLabel("<b>Hash Root Global:</b>"))
        integrity_layout.addWidget(self.root_display)
        
        # Componentes
        comps_layout = QHBoxLayout()
        
        self.dynamic_card = MetricCard("Árvore Dinâmica", "Aguardando...", "#8b5cf6")
        comps_layout.addWidget(self.dynamic_card)
        
        self.iterative_card = MetricCard("Seed Iterativo", "Aguardando...", "#10b981")
        comps_layout.addWidget(self.iterative_card)
        
        self.ops_card = MetricCard("Operações", "Aguardando...", "#f59e0b")
        comps_layout.addWidget(self.ops_card)
        
        self.ai_card = MetricCard("Hash IA", "Aguardando...", "#ef4444")
        comps_layout.addWidget(self.ai_card)
        
        integrity_layout.addLayout(comps_layout)
        
        # Entropia
        self.entropy_bar = QProgressBar()
        self.entropy_bar.setRange(0, 100)
        self.entropy_bar.setFormat("Orçamento de Entropia: %p%")
        integrity_layout.addWidget(self.entropy_bar)
        
        layout.addWidget(integrity_group)
        
        # Status dos serviços
        status_group = QGroupBox("Status dos Componentes")
        status_layout = QGridLayout(status_group)
        
        self.pqc_card = MetricCard("PQC (Kyber+Dilithium)", "Verificando...", "#64748b")
        status_layout.addWidget(self.pqc_card, 0, 0)
        
        self.fp_card = MetricCard("Modo Fingerprint", "Verificando...", "#64748b")
        status_layout.addWidget(self.fp_card, 0, 1)
        
        self.merkle_card = MetricCard("Estado Merkle", "Verificando...", "#64748b")
        status_layout.addWidget(self.merkle_card, 0, 2)
        
        layout.addWidget(status_group)
        
        # Informações
        info_group = QGroupBox("Informacoes")
        info_layout = QVBoxLayout(info_group)
        
        info_text = QLabel(
            "<b>SQUID Dashboard v3.0</b><br>"
            "Sistema de Criptografia Pós-Quântica com IA<br><br>"
            "<b>Componentes:</b><br>"
            "• Java Backend (porta 8080) - API REST e criptografia Kyber/Dilithium<br>"
            "• Python IA (porta 5000) - Modelo PyTorch e decisões SR/C<br>"
            "• IPC Daemon - Comunicação entre processos<br><br>"
            "<b>Funcionalidades:</b><br>"
            "• Instâncias SQUID com árvores Merkle configuráveis<br>"
            "• Criptografia híbrida pós-quântica<br>"
            "• IA para rotação e proteção de dados<br>"
            "• Monitoramento de hardware em tempo real"
        )
        info_text.setWordWrap(True)
        info_layout.addWidget(info_text)
        
        layout.addWidget(info_group)
        layout.addStretch()
    
    def update_from_telemetry(self, data: dict):
        """Atualiza display com dados de telemetria.
        
        Args:
            data: Dicionário com health, global_root
        """
        # Global root
        if 'global_root' in data:
            groot = data['global_root']
            
            if 'globalRoot' in groot:
                self.root_display.setText(groot['globalRoot'])
            
            if 'dynamicRoot' in groot:
                val = groot['dynamicRoot']
                self.dynamic_card.set_value(val[:16] + "..." if len(val) > 16 else val)
            
            if 'iterativeRoots' in groot:
                count = len(groot['iterativeRoots']) if groot['iterativeRoots'] else 0
                self.iterative_card.set_value(f"{count} níveis")
            
            if 'operationRoots' in groot:
                count = len(groot['operationRoots']) if groot['operationRoots'] else 0
                self.ops_card.set_value(f"{count} ops")
            
            if 'aiDecisionHash' in groot:
                val = groot['aiDecisionHash']
                self.ai_card.set_value(val[:16] + "..." if val else "N/A")
            
            if 'entropyBudget' in groot:
                val = float(groot['entropyBudget'])
                self.entropy_bar.setValue(int(val))
                
                if val < 20:
                    self.entropy_bar.setStyleSheet(
                        "QProgressBar::chunk { background-color: #ef4444; }"
                    )
                else:
                    self.entropy_bar.setStyleSheet(
                        "QProgressBar::chunk { background-color: #38bdf8; }"
                    )
        
        # Health status
        if 'health' in data:
            health = data['health']
            
            crypto_status = health.get('cryptography_status', 'OFFLINE')
            self.pqc_card.set_value(crypto_status)
            
            if crypto_status == 'ONLINE':
                self.pqc_card.value_lbl.setStyleSheet("color: #22c55e; font-size: 14pt; font-weight: bold;")
            else:
                self.pqc_card.value_lbl.setStyleSheet("color: #ef4444; font-size: 14pt; font-weight: bold;")
            
            self.fp_card.set_value(health.get('fingerprint_mode', '-'))
            self.merkle_card.set_value(health.get('merkle_state', '-'))
