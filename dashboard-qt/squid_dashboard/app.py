"""Classe principal da aplicação SQUID Dashboard."""

import subprocess
import os
import signal
from datetime import datetime
from typing import Optional

from PySide6.QtWidgets import (
    QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QTabWidget, QSplitter, QTextEdit, QLabel, QMessageBox
)
from PySide6.QtCore import Qt, QProcess, Signal, QObject, Slot

from .clients import JavaClient, PythonClient
from .models import ServiceInfo, ServiceStatus
from .panels import (
    OverviewPanel, CryptoEnginePanel, InstanceManagerPanel,
    AIPanel, MerkleTreePanel, HardwarePanel, ServiceControlPanel,
    DatabasePanel, RequirementsPanel, MonitoringPanel
)
from .workers import TelemetryWorker, PythonTelemetryWorker

# resolve os diretorios base relativo a este arquivo pra funcionar independente do CWD
_DASHBOARD_DIR = os.path.dirname(os.path.abspath(__file__))
_PROJECT_ROOT = os.path.abspath(os.path.join(_DASHBOARD_DIR, '..', '..'))


class ServiceManager(QObject):
    # gerenciador de servicos do squid
    
    status_changed = Signal(str, ServiceStatus)
    log_message = Signal(str, str)
    
    def __init__(self, parent=None):
        super().__init__(parent)
        self.services = {}
        self._setup_services()
    
    def _setup_services(self):
        # configura os servicos padrao
        self.services['http_api'] = ServiceInfo(
            name='HTTP API',
            status=ServiceStatus.OFFLINE,
            port=8080
        )
        self.services['pytorch_ia'] = ServiceInfo(
            name='PyTorch IA',
            status=ServiceStatus.OFFLINE,
            port=5000
        )
        self.services['ipc_daemon'] = ServiceInfo(
            name='IPC Daemon',
            status=ServiceStatus.OFFLINE,
            port=0
        )
    
    def start_service(self, name: str):
        # inicia um servico pelo nome
        if name not in self.services:
            return
        
        service = self.services[name]
        service.status = ServiceStatus.STARTING
        self.status_changed.emit(name, service.status)
        self.log_message.emit(name, "Iniciando servico...")
        
        process = QProcess(self)
        service.process = process
        
        jar_path = os.path.join(_PROJECT_ROOT, 'java-backend', 'target', 'squid-core-1.0.0.jar')
        py_app = os.path.join(_PROJECT_ROOT, 'python-ia', 'app.py')
        py_ipc = os.path.join(_PROJECT_ROOT, 'python-ia', 'ipc_daemon.py')
        
        if name == 'http_api':
            process.setProgram('java')
            process.setArguments(['-jar', jar_path])
        elif name == 'pytorch_ia':
            process.setProgram('python')
            process.setArguments([py_app])
        elif name == 'ipc_daemon':
            process.setProgram('python')
            process.setArguments([py_ipc])
        
        # usa signals pra transicoes de estado assincronas em vez de checar sincronamente
        process.started.connect(lambda: self._on_process_started(name))
        process.errorOccurred.connect(lambda err: self._on_process_error(name, err))
        process.finished.connect(lambda: self._on_process_finished(name))
        process.start()
    
    def stop_service(self, name: str):
        # para um servico
        if name not in self.services:
            return
        
        service = self.services[name]
        service.status = ServiceStatus.STOPPING
        self.status_changed.emit(name, service.status)
        
        if service.process and service.process.state() == QProcess.Running:
            service.process.terminate()
            service.process.waitForFinished(5000)
        
        service.status = ServiceStatus.OFFLINE
        service.process = None
        self.status_changed.emit(name, service.status)
        self.log_message.emit(name, "Serviço parado")
    
    def _on_process_started(self, name: str):
        # chamado quando o processo inicia com sucesso
        service = self.services.get(name)
        if service:
            service.status = ServiceStatus.ONLINE
            service.pid = service.process.processId() if service.process else 0
            self.status_changed.emit(name, service.status)
            self.log_message.emit(name, f"Servico iniciado (PID {service.pid})")
    
    def _on_process_error(self, name: str, error):
        # chamado quando o processo falha ao iniciar
        service = self.services.get(name)
        if service and service.status != ServiceStatus.OFFLINE:
            service.status = ServiceStatus.ERROR
            self.status_changed.emit(name, service.status)
            self.log_message.emit(name, f"Erro ao iniciar: {error}")
    
    def _on_process_finished(self, name: str):
        # chamado quando o processo termina
        service = self.services.get(name)
        if service:
            service.status = ServiceStatus.OFFLINE
            service.process = None
            self.status_changed.emit(name, service.status)
            self.log_message.emit(name, "Processo encerrado")
    
    def get_service_status(self, name: str) -> ServiceStatus:
        # retorna o status de um servico
        return self.services.get(name, ServiceInfo('', ServiceStatus.OFFLINE)).status
    
    def get_all_statuses(self) -> dict:
        # retorna status de todos os servicos
        return {name: svc.status for name, svc in self.services.items()}


class SquidDashboard(QMainWindow):
    # janela principal do squid dashboard
    
    def __init__(self):
        super().__init__()
        self.setWindowTitle("SQUID Dashboard v3.0 - Sistema Completo")
        self.resize(1400, 900)
        
        # clientes da api
        self.java_client = JavaClient()
        self.python_client = PythonClient()
        
        # gerenciador de servicos
        self.service_manager = ServiceManager(self)
        self.service_manager.log_message.connect(self._on_service_log)
        
        self._setup_ui()
        self._setup_workers()
    
    def _setup_ui(self):
        # monta a interface principal
        central = QWidget()
        self.setCentralWidget(central)
        layout = QVBoxLayout(central)
        layout.setSpacing(10)
        layout.setContentsMargins(10, 10, 10, 10)
        
        # painel de controle de servicos
        self.service_panel = ServiceControlPanel(self.service_manager, self)
        
        # splitter principal
        splitter = QSplitter(Qt.Horizontal)
        
        # abas da esquerda
        self.tabs = QTabWidget()
        
        self.overview_panel = OverviewPanel(self)
        self.tabs.addTab(self.overview_panel, "Visao Geral")
        
        self.crypto_panel = CryptoEnginePanel(self.java_client, self)
        self.tabs.addTab(self.crypto_panel, "Criptografia")
        
        self.instance_panel = InstanceManagerPanel(self.java_client, self)
        self.tabs.addTab(self.instance_panel, "Instancias")
        
        self.ai_panel = AIPanel(self.python_client, self)
        self.tabs.addTab(self.ai_panel, "IA (SR/C)")
        
        self.merkle_panel = MerkleTreePanel(self.python_client, self)
        self.tabs.addTab(self.merkle_panel, "Merkle Tree")
        
        self.hardware_panel = HardwarePanel(self)
        self.tabs.addTab(self.hardware_panel, "Hardware")
        
        self.database_panel = DatabasePanel(self.java_client, self)
        self.tabs.addTab(self.database_panel, "Banco de Dados")
        
        self.requirements_panel = RequirementsPanel(self.java_client, self)
        self.tabs.addTab(self.requirements_panel, "Requisitos")
        
        self.monitoring_panel = MonitoringPanel(self.java_client, self.python_client, self)
        self.tabs.addTab(self.monitoring_panel, "Monitoramento")
        
        self.tabs.addTab(self.service_panel, "Servicos")
        
        splitter.addWidget(self.tabs)
        
        # painel direito (logs)
        right_panel = QSplitter(Qt.Vertical)
        
        # stream de pensamento da ia
        ai_stream = QWidget()
        ai_layout = QVBoxLayout(ai_stream)
        ai_layout.addWidget(QLabel("<b>AI Thought Stream</b>"))
        self.ai_stream = QTextEdit()
        self.ai_stream.setReadOnly(True)
        self.ai_stream.setStyleSheet(
            "background-color: #020617; color: #4ade80; font-family: Consolas;"
        )
        ai_layout.addWidget(self.ai_stream)
        right_panel.addWidget(ai_stream)
        
        # log do sistema
        log_widget = QWidget()
        log_layout = QVBoxLayout(log_widget)
        log_layout.addWidget(QLabel("<b>System Audit Log</b>"))
        self.audit_log = QTextEdit()
        self.audit_log.setReadOnly(True)
        self.audit_log.setObjectName("LogConsole")
        log_layout.addWidget(self.audit_log)
        right_panel.addWidget(log_widget)
        
        right_panel.setSizes([300, 400])
        splitter.addWidget(right_panel)
        splitter.setSizes([1000, 400])
        
        layout.addWidget(splitter, 1)
        
        # Status bar
        self.statusBar().showMessage("SQUID Dashboard v3.0 - Pronto")
    
    def _setup_workers(self):
        # configura workers de telemetria (nao inicia automaticamente)
        # workers sao iniciados/parados quando os servicos correspondentes
        # transitam para ONLINE/OFFLINE via _on_service_status_changed
        self.java_worker = TelemetryWorker(self.java_client)
        self.java_worker.data_ready.connect(self._on_java_telemetry)
        
        self.python_worker = PythonTelemetryWorker(self.python_client)
        self.python_worker.data_ready.connect(self._on_python_telemetry)
        
        # reage a mudancas de status dos servicos pra iniciar/parar workers
        self.service_manager.status_changed.connect(self._on_service_status_changed)
    
    @Slot(str, ServiceStatus)
    def _on_service_status_changed(self, service_name: str, status: ServiceStatus):
        # inicia/para workers de telemetria conforme status dos servicos
        if service_name == 'http_api':
            if status == ServiceStatus.ONLINE and not self.java_worker.isRunning():
                self.java_worker.start()
            elif status in (ServiceStatus.OFFLINE, ServiceStatus.ERROR) and self.java_worker.isRunning():
                self.java_worker.stop()
        elif service_name == 'pytorch_ia':
            if status == ServiceStatus.ONLINE and not self.python_worker.isRunning():
                self.python_worker.start()
            elif status in (ServiceStatus.OFFLINE, ServiceStatus.ERROR) and self.python_worker.isRunning():
                self.python_worker.stop()
    
    def _on_java_telemetry(self, data: dict):
        # processa telemetria vinda do java
        self.overview_panel.update_from_telemetry(data)
        
        if 'global_root' in data:
            gr = data['global_root']
            if 'aiDecisionHash' in gr:
                self._add_ai_thought(gr['aiDecisionHash'][:16], 75.0)
    
    def _on_python_telemetry(self, data: dict):
        # processa telemetria vinda do python
        if 'merkle_status' in data:
            status = data['merkle_status']
            total = status.get('totalNodes', 0)
            self.statusBar().showMessage(
                f"Python IA | Merkle Nodes: {total} | {datetime.now():%H:%M:%S}",
                3000
            )
    
    def _on_service_log(self, service_name: str, message: str):
        # processa logs de servicos
        timestamp = datetime.now().strftime('%H:%M:%S')
        entry = f"[{timestamp}] [{service_name.upper()}] {message}"
        self.add_log(entry)
    
    def _add_ai_thought(self, hash_fragment: str, entropy: float):
        # adiciona um pensamento da ia no stream
        ts = datetime.now().strftime('%H:%M:%S')
        status = 'STABLE' if entropy > 20 else 'MUTATION_NEEDED'
        
        # evita duplicatas
        text = self.ai_stream.toPlainText()
        if hash_fragment in text and str(round(entropy, 1)) in text:
            return
        
        self.ai_stream.append(f"[{ts}] HASH: {hash_fragment}...")
        self.ai_stream.append(f"        ENTROPY: {entropy:.2f} | STATUS: {status}")
        self.ai_stream.append("-" * 40)
    
    def add_log(self, message: str):
        # adiciona entrada ao log de auditoria
        self.audit_log.append(message)
        # rola pro final
        scrollbar = self.audit_log.verticalScrollBar()
        scrollbar.setValue(scrollbar.maximum())
    
    def closeEvent(self, event):
        # trata o fechamento da janela
        # para os workers
        if hasattr(self, 'java_worker'):
            self.java_worker.stop()
        if hasattr(self, 'python_worker'):
            self.python_worker.stop()
        
        # para os servicos
        for name in list(self.service_manager.services.keys()):
            self.service_manager.stop_service(name)
        
        event.accept()
