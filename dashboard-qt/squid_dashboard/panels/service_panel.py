"""Painel de controle de serviços."""

from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QGridLayout,
    QLabel, QPushButton, QGroupBox, QMessageBox
)
from PySide6.QtCore import Qt

from ..models import ServiceStatus


class ServiceControlPanel(QWidget):
    """Painel de controle dos serviços SQUID (HTTP API, PyTorch IA, IPC Daemon)."""
    
    def __init__(self, service_manager, parent=None):
        super().__init__(parent)
        self.service_manager = service_manager
        self._setup_ui()
        self._connect_signals()
    
    def _setup_ui(self):
        """Configura a interface do painel."""
        layout = QVBoxLayout(self)
        layout.setSpacing(15)
        
        # Grupo principal
        group = QGroupBox("Controle de Serviços")
        grid = QGridLayout(group)
        grid.setSpacing(10)
        
        # HTTP API Service
        self._create_service_row(
            grid, 0, "HTTP API",
            "Serviço REST na porta 8080",
            'http_api'
        )
        
        # PyTorch IA Service
        self._create_service_row(
            grid, 1, "PyTorch IA",
            "Serviço de IA na porta 5000",
            'pytorch_ia'
        )
        
        # IPC Daemon Service
        self._create_service_row(
            grid, 2, "IPC Daemon",
            "Comunicação Java ↔ Python",
            'ipc_daemon'
        )
        
        layout.addWidget(group)
        layout.addStretch()
    
    def _create_service_row(self, grid, row, name, tooltip, service_key):
        """Cria uma linha de controle de serviço.
        
        Args:
            grid: GridLayout para adicionar widgets
            row: Número da linha
            name: Nome do serviço
            tooltip: Dica de ferramenta
            service_key: Chave do serviço no service_manager
        """
        # Label do serviço
        label = QLabel(f"<b>{name}</b>")
        label.setToolTip(tooltip)
        grid.addWidget(label, row, 0)
        
        # Status
        status_label = QLabel("OFFLINE")
        status_label.setObjectName("StatusOffline")
        setattr(self, f"{service_key}_status", status_label)
        grid.addWidget(status_label, row, 1)
        
        # Botão Iniciar
        start_btn = QPushButton("Iniciar")
        start_btn.setObjectName("StartBtn")
        start_btn.setToolTip(f"Iniciar {name}")
        start_btn.clicked.connect(lambda: self._start_service(service_key))
        setattr(self, f"{service_key}_start_btn", start_btn)
        grid.addWidget(start_btn, row, 2)
        
        # Botão Parar
        stop_btn = QPushButton("Parar")
        stop_btn.setObjectName("StopBtn")
        stop_btn.setToolTip(f"Parar {name}")
        stop_btn.clicked.connect(lambda: self._stop_service(service_key))
        stop_btn.setEnabled(False)
        setattr(self, f"{service_key}_stop_btn", stop_btn)
        grid.addWidget(stop_btn, row, 3)
    
    def _connect_signals(self):
        """Conecta sinais do service_manager."""
        self.service_manager.status_changed.connect(self._update_status)
    
    def _start_service(self, service_name: str):
        """Inicia um serviço.
        
        Args:
            service_name: Nome do serviço
        """
        status = self.service_manager.get_service_status(service_name)
        if status in [ServiceStatus.OFFLINE, ServiceStatus.ERROR]:
            self.service_manager.start_service(service_name)
    
    def _stop_service(self, service_name: str):
        """Para um serviço.
        
        Args:
            service_name: Nome do serviço
        """
        self.service_manager.stop_service(service_name)
    
    def _update_status(self, service_name: str, status: ServiceStatus):
        """Atualiza a UI quando o status de um serviço muda.
        
        Args:
            service_name: Nome do serviço
            status: Novo status
        """
        status_label = getattr(self, f"{service_name}_status", None)
        start_btn = getattr(self, f"{service_name}_start_btn", None)
        stop_btn = getattr(self, f"{service_name}_stop_btn", None)
        
        if not all([status_label, start_btn, stop_btn]):
            return
        
        # Atualiza texto e estilo do status
        status_label.setText(status.value)
        status_label.setObjectName("")
        
        if status == ServiceStatus.ONLINE:
            status_label.setObjectName("StatusOnline")
            start_btn.setEnabled(False)
            stop_btn.setEnabled(True)
        elif status == ServiceStatus.OFFLINE:
            status_label.setObjectName("StatusOffline")
            start_btn.setEnabled(True)
            stop_btn.setEnabled(False)
        elif status == ServiceStatus.ERROR:
            status_label.setObjectName("StatusError")
            start_btn.setEnabled(True)
            stop_btn.setEnabled(False)
        elif status in [ServiceStatus.STARTING, ServiceStatus.STOPPING]:
            status_label.setObjectName("StatusStarting")
            start_btn.setEnabled(False)
            stop_btn.setEnabled(False)
        
        # Força atualização do estilo
        status_label.style().unpolish(status_label)
        status_label.style().polish(status_label)
