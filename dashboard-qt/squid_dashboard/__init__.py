"""SQUID Dashboard Package - Sistema de Dashboard Qt para SQUID."""

__version__ = "3.0.0"

from .clients import JavaClient, PythonClient
from .app import SquidDashboard

__all__ = ['JavaClient', 'PythonClient', 'SquidDashboard']
