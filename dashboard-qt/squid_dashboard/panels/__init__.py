"""Paineis de UI do dashboard."""

from .service_panel import ServiceControlPanel
from .instance_panel import InstanceManagerPanel
from .ai_panel import AIPanel
from .merkle_panel import MerkleTreePanel
from .hardware_panel import HardwarePanel
from .crypto_panel import CryptoEnginePanel
from .overview_panel import OverviewPanel
from .database_panel import DatabasePanel
from .requirements_panel import RequirementsPanel
from .monitoring_panel import MonitoringPanel

__all__ = [
    'ServiceControlPanel',
    'InstanceManagerPanel', 
    'AIPanel',
    'MerkleTreePanel',
    'HardwarePanel',
    'CryptoEnginePanel',
    'OverviewPanel',
    'DatabasePanel',
    'RequirementsPanel',
    'MonitoringPanel'
]
