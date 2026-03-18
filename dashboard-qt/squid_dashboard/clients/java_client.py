"""Cliente para Java Backend (HTTP API)."""

import json
import urllib.request
from typing import Dict, Any, Optional


class JavaClient:
    """Cliente para comunicação com o backend Java (porta 8080)."""
    
    def __init__(self, base_url: str = "http://localhost:8080"):
        self.base_url = base_url
    
    def _req(self, method: str, path: str, body: Optional[Dict] = None) -> Optional[Dict]:
        """Faz uma requisição HTTP para o backend Java."""
        url = f"{self.base_url}{path}"
        try:
            data = None
            if body is not None:
                data = json.dumps(body).encode('utf-8')
            
            headers = {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            }
            
            req = urllib.request.Request(url, data=data, headers=headers, method=method)
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.loads(resp.read().decode('utf-8'))
        except Exception:
            return None
    
    def health(self) -> Optional[Dict]:
        """Verifica saúde do sistema."""
        return self._req('GET', '/api/v1/system/health')
    
    def get_global_root(self) -> Optional[Dict]:
        """Obtém o root global da árvore Merkle."""
        return self._req('GET', '/api/v1/global/root')
    
    def list_instances(self) -> Optional[list]:
        """Lista todas as instâncias SQUID."""
        return self._req('GET', '/api/v1/instances')
    
    def create_instance(self, name: str, config: Optional[Dict] = None) -> Optional[Dict]:
        """Cria uma nova instância SQUID.
        
        Args:
            name: Nome da instância
            config: Configuração opcional com B, M, T
        """
        body = {'name': name}
        if config:
            body['config'] = config
        return self._req('POST', '/api/v1/instances', body)
    
    def cancel_instance(self, inst_id: str) -> Optional[Dict]:
        """Cancela/destroi uma instância."""
        return self._req('POST', f'/api/v1/instances/{inst_id}/cancel')
    
    def encrypt(self, data: str, instance_id: str = "") -> Optional[Dict]:
        """Criptografa dados usando Kyber + Dilithium."""
        body = {'data': data}
        if instance_id:
            body['instance_id'] = instance_id
        return self._req('POST', '/api/v1/crypto/encrypt', body)
    
    def decrypt(self, ciphertext: str, metadata: dict, preview_only: bool = True) -> Optional[Dict]:
        """Descriptografa dados."""
        return self._req('POST', '/api/v1/crypto/decrypt', {
            'ciphertext': ciphertext,
            'metadata': metadata,
            'preview_only': preview_only
        })
    
    def run_iterative_seed(self, seed: str, depth: int) -> Optional[Dict]:
        """Executa o motor de seed iterativo."""
        return self._req('POST', '/api/v1/crypto/iterative-seed-run', {
            'initialSeed': seed,
            'depth': depth
        })
    
    def get_operations(self) -> Optional[list]:
        """Obtém operações criptográficas recentes."""
        return self._req('GET', '/api/v1/crypto/operations')
    
    def remove_leaves(self, inst_id: str, leaf_indices: list) -> Optional[Dict]:
        """Remove folhas de uma instância."""
        return self._req('POST', f'/api/v1/instances/{inst_id}/remove-leaves', {
            'leaf_indices': leaf_indices
        })
    
    def reencrypt_instance(self, inst_id: str) -> Optional[Dict]:
        """Re-criptografa uma instância."""
        return self._req('POST', f'/api/v1/instances/{inst_id}/reencrypt')
    
    def decrypt_instance(self, inst_id: str, file_extension: str) -> Optional[Dict]:
        """Descriptografa uma instância para arquivo."""
        return self._req('POST', f'/api/v1/instances/{inst_id}/decrypt', {
            'file_extension': file_extension
        })
    
    def send_to_database(self, inst_id: str, db_type: str) -> Optional[Dict]:
        """Envia instância para banco de dados (MySQL/PostgreSQL)."""
        return self._req('POST', f'/api/v1/instances/{inst_id}/export', {
            'target': db_type
        })

    # ── Leaf & history endpoints ──

    def get_instance_leaves(self, inst_id: str) -> Optional[Dict]:
        """Obtém todas as folhas de uma instância com metadados computados."""
        return self._req('GET', f'/api/v1/instances/{inst_id}/leaves')

    def get_instance_leaf_detail(self, inst_id: str, index: int) -> Optional[Dict]:
        """Obtém detalhes completos de uma folha específica."""
        return self._req('GET', f'/api/v1/instances/{inst_id}/leaves/{index}')

    def get_instance_history(self, inst_id: str) -> Optional[Dict]:
        """Obtém histórico completo de uma instância."""
        return self._req('GET', f'/api/v1/instances/{inst_id}/history')

    # ── Database observability endpoints ──

    def get_instances(self) -> Optional[list]:
        """Obtem lista de instancias disponiveis."""
        return self._req('GET', '/api/v1/instances')

    def db_health(self, instance_id: str = None) -> Optional[Dict]:
        """Obtem saude do banco de dados para uma instancia especifica."""
        path = '/api/v1/database/health'
        if instance_id:
            path += f'?instanceId={instance_id}'
        return self._req('GET', path)

    def db_merkle_integrity(self, instance_id: str = None) -> Optional[Dict]:
        """Verifica integridade Merkle no banco para uma instancia."""
        path = '/api/v1/database/merkle-integrity'
        if instance_id:
            path += f'?instanceId={instance_id}'
        return self._req('GET', path)

    def db_audit_logs(self, instance_id: str = None, limit: int = 50) -> Optional[list]:
        """Obtem logs de auditoria para uma instancia."""
        path = f'/api/v1/database/audit-logs?limit={limit}'
        if instance_id:
            path += f'&instanceId={instance_id}'
        return self._req('GET', path)

    def db_audit_verify(self, instance_id: str = None) -> Optional[Dict]:
        """Verifica cadeia de auditoria para uma instancia."""
        path = '/api/v1/database/audit-verify'
        if instance_id:
            path += f'?instanceId={instance_id}'
        return self._req('GET', path)

    # metodos para verificacao de requisitos
    def check_requirements(self) -> Optional[Dict]:
        """Verifica todos os requisitos do sistema."""
        return self._req('GET', '/api/requirements/check')

    def can_system_start(self) -> Optional[Dict]:
        """Verifica se o sistema pode iniciar."""
        return self._req('GET', '/api/requirements/can-start')

    def get_requirements_status(self) -> Optional[Dict]:
        """Obtem status de todos os requisitos."""
        return self._req('GET', '/api/requirements/status')

    def db_test_connection(self, url: str, user: str, password: str) -> Optional[Dict]:
        """Testa conexao com um banco de dados."""
        return self._req('POST', '/api/v1/database/test-connection', {
            'url': url,
            'user': user,
            'password': password
        })
