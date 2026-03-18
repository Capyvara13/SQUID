"""Painel de monitoramento de hardware."""

from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QGroupBox,
    QLabel, QPushButton, QProgressBar
)
from PySide6.QtCore import QTimer

from ..workers import HardwareMonitorWorker


class HardwarePanel(QWidget):
    """Painel para monitoramento de hardware (CPU, RAM, GPU)."""
    
    def __init__(self, parent=None):
        super().__init__(parent)
        self.monitor_worker = None
        self._setup_ui()
        self._init_hardware_info()
    
    def _setup_ui(self):
        """Configura a interface."""
        layout = QVBoxLayout(self)
        layout.setSpacing(15)
        
        # CPU
        cpu_group = QGroupBox("CPU")
        cpu_layout = QVBoxLayout(cpu_group)
        
        self.cpu_bar = QProgressBar()
        self.cpu_bar.setRange(0, 100)
        self.cpu_bar.setFormat("Uso: %p%")
        cpu_layout.addWidget(QLabel("Uso:"))
        cpu_layout.addWidget(self.cpu_bar)
        
        self.cpu_info = QLabel("Núcleos: -")
        cpu_layout.addWidget(self.cpu_info)
        
        self.cpu_freq = QLabel("Frequência: -")
        cpu_layout.addWidget(self.cpu_freq)
        
        layout.addWidget(cpu_group)
        
        # RAM
        ram_group = QGroupBox("Memoria RAM")
        ram_layout = QVBoxLayout(ram_group)
        
        self.ram_bar = QProgressBar()
        self.ram_bar.setRange(0, 100)
        self.ram_bar.setFormat("Uso: %p%")
        ram_layout.addWidget(QLabel("Uso:"))
        ram_layout.addWidget(self.ram_bar)
        
        self.ram_info = QLabel("- / -")
        ram_layout.addWidget(self.ram_info)
        
        layout.addWidget(ram_group)
        
        # GPU
        gpu_group = QGroupBox("🎮 GPU (PyTorch/CUDA)")
        gpu_layout = QVBoxLayout(gpu_group)
        
        self.gpu_name = QLabel("GPU: N/A")
        gpu_layout.addWidget(self.gpu_name)
        
        self.gpu_bar = QProgressBar()
        self.gpu_bar.setRange(0, 100)
        self.gpu_bar.setFormat("Memória: %p%")
        gpu_layout.addWidget(QLabel("Memória GPU:"))
        gpu_layout.addWidget(self.gpu_bar)
        
        self.cuda_info = QLabel("CUDA: -")
        gpu_layout.addWidget(self.cuda_info)
        
        layout.addWidget(gpu_group)
        
        # PyTorch
        pytorch_group = QGroupBox("🔥 PyTorch")
        pytorch_layout = QVBoxLayout(pytorch_group)
        
        self.pytorch_ver = QLabel("Versão: -")
        pytorch_layout.addWidget(self.pytorch_ver)
        
        self.pytorch_cuda = QLabel("CUDA disponível: -")
        pytorch_layout.addWidget(self.pytorch_cuda)
        
        layout.addWidget(pytorch_group)
        
        # Controles
        controls = QHBoxLayout()
        
        self.monitor_btn = QPushButton("Iniciar Monitoramento")
        self.monitor_btn.setCheckable(True)
        self.monitor_btn.clicked.connect(self._toggle_monitoring)
        controls.addWidget(self.monitor_btn)
        
        controls.addStretch()
        layout.addLayout(controls)
        layout.addStretch()
    
    def _init_hardware_info(self):
        """Inicializa informações estáticas."""
        try:
            import psutil
            self.cpu_info.setText(
                f"Núcleos: {psutil.cpu_count(logical=False)} físicos / "
                f"{psutil.cpu_count()} lógicos"
            )
        except ImportError:
            self.cpu_info.setText("psutil não instalado")
        
        try:
            import torch
            self.pytorch_ver.setText(f"Versão: {torch.__version__}")
            self.pytorch_cuda.setText(f"CUDA disponível: {torch.cuda.is_available()}")
            
            if torch.cuda.is_available():
                self.gpu_name.setText(f"GPU: {torch.cuda.get_device_name(0)}")
                self.cuda_info.setText(f"CUDA: {torch.version.cuda}")
        except ImportError:
            self.pytorch_ver.setText("PyTorch não instalado")
    
    def _toggle_monitoring(self, checked):
        """Alterna monitoramento."""
        if checked:
            self.monitor_btn.setText("Parar Monitoramento")
            self._start_monitoring()
        else:
            self.monitor_btn.setText("Iniciar Monitoramento")
            self._stop_monitoring()
    
    def _start_monitoring(self):
        """Inicia worker de monitoramento."""
        self.monitor_worker = HardwareMonitorWorker(interval_ms=2000)
        self.monitor_worker.stats_ready.connect(self._update_stats)
        self.monitor_worker.start()
    
    def _stop_monitoring(self):
        """Para worker."""
        if self.monitor_worker:
            self.monitor_worker.stop()
            self.monitor_worker = None
    
    def _update_stats(self, stats: dict):
        """Atualiza estatísticas.
        
        Args:
            stats: Dicionário com cpu_percent, ram_percent, etc
        """
        cpu = stats.get('cpu_percent', 0)
        self.cpu_bar.setValue(int(cpu))
        
        # Color coding
        if cpu > 80:
            self.cpu_bar.setStyleSheet("QProgressBar::chunk { background-color: #ef4444; }")
        elif cpu > 50:
            self.cpu_bar.setStyleSheet("QProgressBar::chunk { background-color: #f59e0b; }")
        else:
            self.cpu_bar.setStyleSheet("QProgressBar::chunk { background-color: #22c55e; }")
        
        # RAM
        ram = stats.get('ram_percent', 0)
        self.ram_bar.setValue(int(ram))
        
        used = stats.get('ram_used_gb', 0)
        total = stats.get('ram_total_gb', 0)
        self.ram_info.setText(f"{used:.1f} GB / {total:.1f} GB")
        
        if ram > 90:
            self.ram_bar.setStyleSheet("QProgressBar::chunk { background-color: #ef4444; }")
        elif ram > 70:
            self.ram_bar.setStyleSheet("QProgressBar::chunk { background-color: #f59e0b; }")
        else:
            self.ram_bar.setStyleSheet("QProgressBar::chunk { background-color: #22c55e; }")
        
        # GPU
        gpu = stats.get('gpu_percent', 0)
        self.gpu_bar.setValue(int(gpu))
    
    def hideEvent(self, event):
        """Para monitoramento ao esconder."""
        self._stop_monitoring()
        super().hideEvent(event)
