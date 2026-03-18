"""Modelos de dados para o dashboard."""

from dataclasses import dataclass, field
from datetime import datetime
from typing import Dict, Any, List, Optional
from enum import Enum


class ServiceStatus(Enum):
    """Status de um serviço."""
    ONLINE = "ONLINE"
    OFFLINE = "OFFLINE"
    ERROR = "ERRO"
    STARTING = "INICIANDO"
    STOPPING = "PARANDO"


class InstanceStatus(Enum):
    """Status de uma instância SQUID."""
    ACTIVE = "ACTIVE"
    PAUSED = "PAUSED"
    ENCRYPTING = "ENCRYPTING"
    DECRYPTING = "DECRYPTING"
    MUTATING = "MUTATING"
    DESTROYED = "DESTROYED"


class LeafState(Enum):
    """Estado de uma folha na árvore Merkle."""
    VALID = "VALID"
    DECOY = "DECOY"
    MUTATE = "MUTATE"
    REASSIGN = "REASSIGN"


@dataclass
class LeafNode:
    """Representa uma folha na árvore Merkle."""
    id: str
    hash: str
    depth: int
    entropy: float
    sr_local: float
    c_local: float
    state: LeafState
    history: List[Dict] = field(default_factory=list)
    created_at: datetime = field(default_factory=datetime.now)
    
    def to_dict(self) -> Dict[str, Any]:
        """Converte para dicionário."""
        return {
            'id': self.id,
            'hash': self.hash,
            'depth': self.depth,
            'entropy': self.entropy,
            'sr_local': self.sr_local,
            'c_local': self.c_local,
            'state': self.state.value,
            'history': self.history,
            'created_at': self.created_at.isoformat()
        }


@dataclass
class SquidInstance:
    """Representa uma instância SQUID completa."""
    id: str
    name: str
    created_at: datetime
    status: InstanceStatus
    B: int  # Branching factor
    M: int  # Depth
    T: int  # Total leaves
    seed_root: str
    merkle_root: str
    leafs: List[LeafNode] = field(default_factory=list)
    history: List[Dict] = field(default_factory=list)
    ai_logs: List[Dict] = field(default_factory=list)
    data_original: str = ""
    endpoint_active: bool = False
    endpoint_port: int = 0
    
    @property
    def leaf_count(self) -> int:
        """Retorna número de folhas."""
        return len(self.leafs)
    
    def to_dict(self) -> Dict[str, Any]:
        """Converte para dicionário."""
        return {
            'id': self.id,
            'name': self.name,
            'created_at': self.created_at.isoformat(),
            'status': self.status.value,
            'B': self.B,
            'M': self.M,
            'T': self.T,
            'seed_root': self.seed_root,
            'merkle_root': self.merkle_root,
            'leaf_count': self.leaf_count,
            'history_count': len(self.history),
            'endpoint_active': self.endpoint_active
        }


@dataclass
class ServiceInfo:
    """Informações sobre um serviço gerenciado."""
    name: str
    status: ServiceStatus
    process: Any = None
    error_message: str = ""
    start_time: Optional[datetime] = None
    pid: int = 0
    port: int = 0
    
    @property
    def is_running(self) -> bool:
        """Verifica se o serviço está rodando."""
        return self.status == ServiceStatus.ONLINE and self.process is not None


@dataclass
class AIMetrics:
    """Métricas da IA (SR/C)."""
    sr: float = 0.0
    c: float = 0.0
    confidence: float = 0.0
    decision: str = "HOLD_STATE"
    actions: List[str] = field(default_factory=list)
    timestamp: datetime = field(default_factory=datetime.now)
    
    def is_valid(self) -> bool:
        """Verifica se as métricas são válidas."""
        return all([
            isinstance(self.sr, (int, float)),
            isinstance(self.c, (int, float)),
            0 <= self.confidence <= 1
        ])


@dataclass
class HardwareStats:
    """Estatísticas de hardware."""
    cpu_percent: float = 0.0
    cpu_freq: float = 0.0
    ram_percent: float = 0.0
    ram_used_gb: float = 0.0
    ram_total_gb: float = 0.0
    gpu_percent: float = 0.0
    gpu_memory_gb: float = 0.0
    timestamp: datetime = field(default_factory=datetime.now)
