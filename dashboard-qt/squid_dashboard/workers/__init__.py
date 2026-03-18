"""Threads de trabalho para telemetria e operações assíncronas."""

import time
from typing import Callable, Any
from PySide6.QtCore import QThread, Signal


class TelemetryWorker(QThread):
    """Worker para polling do backend Java."""
    data_ready = Signal(dict)
    
    def __init__(self, client):
        super().__init__()
        self.client = client
        self.running = True
    
    def run(self):
        while self.running:
            data = {}
            
            # Fetch Health
            try:
                health = self.client.health()
                if health:
                    data['health'] = health
            except:
                pass
            
            # Fetch Global Root
            try:
                groot = self.client.get_global_root()
                if groot:
                    data['global_root'] = groot
            except:
                pass
            
            if data:
                self.data_ready.emit(data)
            
            time.sleep(2)
    
    def stop(self):
        self.running = False
        self.wait()


class PythonTelemetryWorker(QThread):
    """Worker para polling do serviço Python IA."""
    data_ready = Signal(dict)
    
    def __init__(self, client):
        super().__init__()
        self.client = client
        self.running = True
    
    def run(self):
        while self.running:
            data = {}
            
            # Fetch Python IA health/model info
            try:
                model_info = self.client.model_info()
                if model_info:
                    data['model_info'] = model_info
            except:
                pass
            
            # Fetch Merkle status
            try:
                merkle_status = self.client.merkle_status()
                if merkle_status:
                    data['merkle_status'] = merkle_status
            except:
                pass
            
            if data:
                self.data_ready.emit(data)
            
            time.sleep(5)
    
    def stop(self):
        self.running = False
        self.wait()


class AsyncActionWorker(QThread):
    """Worker para executar ações assíncronas."""
    finished = Signal(object)
    error = Signal(str)
    
    def __init__(self, func: Callable, *args, **kwargs):
        super().__init__()
        self.func = func
        self.args = args
        self.kwargs = kwargs
    
    def run(self):
        try:
            result = self.func(*self.args, **self.kwargs)
            self.finished.emit(result)
        except Exception as e:
            self.error.emit(str(e))


class HardwareMonitorWorker(QThread):
    """Worker para monitoramento de hardware em tempo real."""
    stats_ready = Signal(dict)
    
    def __init__(self, interval_ms: int = 2000):
        super().__init__()
        self.interval_ms = interval_ms
        self.running = True
    
    def run(self):
        while self.running:
            stats = {}
            
            try:
                import psutil
                
                # CPU
                stats['cpu_percent'] = psutil.cpu_percent(interval=0.5)
                freq = psutil.cpu_freq()
                if freq:
                    stats['cpu_freq'] = freq.current
                
                # RAM
                ram = psutil.virtual_memory()
                stats['ram_percent'] = ram.percent
                stats['ram_used_gb'] = ram.used / (1024**3)
                stats['ram_total_gb'] = ram.total / (1024**3)
            except ImportError:
                pass
            
            # GPU
            try:
                import torch
                if torch.cuda.is_available():
                    allocated = torch.cuda.memory_allocated(0)
                    total = torch.cuda.get_device_properties(0).total_memory
                    stats['gpu_percent'] = (allocated / total) * 100
                    stats['gpu_memory_gb'] = allocated / (1024**3)
            except:
                pass
            
            self.stats_ready.emit(stats)
            time.sleep(self.interval_ms / 1000)
    
    def stop(self):
        self.running = False
        self.wait()
