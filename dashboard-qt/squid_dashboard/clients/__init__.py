"""Clientes API para comunicação com serviços backend."""

from .java_client import JavaClient
from .python_client import PythonClient

__all__ = ['JavaClient', 'PythonClient']
