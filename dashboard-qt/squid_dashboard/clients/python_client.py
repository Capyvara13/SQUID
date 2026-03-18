"""Cliente para Python IA Service."""

import json
import urllib.request
from typing import Dict, Any, List, Optional


class PythonClient:
    """Cliente para comunicacao com o servico Python IA (porta 5000)."""
    
    def __init__(self, base_url: str = "http://localhost:5000"):
        self.base_url = base_url
    
    def _req(self, method: str, path: str, body: Optional[Dict] = None) -> Optional[Dict]:
        """Faz uma requisicao HTTP para o servico Python."""
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
            with urllib.request.urlopen(req, timeout=15) as resp:
                return json.loads(resp.read().decode('utf-8'))
        except Exception:
            return None
    
    def health(self) -> Optional[Dict]:
        """Verifica saude do servico Python IA."""
        return self._req('GET', '/health')
    
    def get_instances(self) -> Optional[list]:
        """Obtem lista de instancias disponiveis."""
        return self._req('GET', '/api/v1/instances')
    
    def decide(self, params: Dict, features: List[Dict], seed_hash: str = None) -> Optional[Dict]:
        """Obtem decisao da IA para features fornecidas.
        
        Returns:
            Dict com 'sr', 'c', 'actions', 'decision', 'confidence'
        """
        body = {
            'params': params,
            'features': features
        }
        if seed_hash:
            body['seed_model_hash'] = seed_hash
        return self._req('POST', '/decide', body)
    
    def model_info(self) -> Optional[Dict]:
        """Obtem informacoes do modelo PyTorch."""
        return self._req('GET', '/model/info')
    
    def model_list(self) -> Optional[Dict]:
        """Lista modelos registrados."""
        return self._req('GET', '/model/list')
    
    def switch_model(self, version: str, reason: str = "Dashboard switch") -> Optional[Dict]:
        """Troca para outro modelo."""
        return self._req('POST', '/model/switch', {
            'version': version,
            'reason': reason,
            'initiator': 'dashboard'
        })
    
    def register_model(self, version: str, architecture: Dict, 
                       description: str = "", metrics: Dict = None) -> Optional[Dict]:
        """Registra um novo modelo."""
        return self._req('POST', '/model/register', {
            'version': version,
            'architecture': architecture,
            'description': description,
            'metrics': metrics or {}
        })
    
    # metodos da Merkle Tree com suporte a instancias
    def merkle_status(self, instance_id: str = None) -> Optional[Dict]:
        """Obtem status da arvore Merkle."""
        path = '/api/v1/merkle/status'
        if instance_id:
            path += f'?instanceId={instance_id}'
        return self._req('GET', path)
    
    def merkle_structure(self, instance_id: str = None) -> Optional[Dict]:
        """Obtem estrutura hierarquica da arvore."""
        path = f'/api/merkle/{instance_id}/structure' if instance_id else '/api/merkle/structure'
        return self._req('GET', path)
    
    def merkle_add_leaves(self, instance_id: str, leaves: List[str], reason: str = "User added") -> Optional[Dict]:
        """Adiciona folhas a arvore Merkle."""
        return self._req('POST', f'/api/merkle/{instance_id}/add-leaves', {
            'leaves': leaves,
            'reason': reason
        })
    
    def merkle_update_leaves(self, updates: Dict, reason: str = "User update") -> Optional[Dict]:
        """Atualiza folhas existentes na arvore Merkle."""
        return self._req('PUT', '/api/v1/merkle/update-leaves', {
            'updates': updates,
            'reason': reason
        })
    
    def merkle_verify(self, instance_id: str = None) -> Optional[Dict]:
        """Verifica integridade da arvore Merkle."""
        path = '/api/v1/merkle/verify'
        if instance_id:
            path = f'/api/merkle/{instance_id}/verify'
        return self._req('POST', path)
    
    # metodos de analise de impacto
    def analyze_impact(self, instance_id: str, leaf_id: str, impact_type: str = "modification") -> Optional[Dict]:
        """Analisa impacto de alteracao de uma leaf."""
        return self._req('POST', f'/api/merkle/{instance_id}/impact/{impact_type}', {
            'leafId': leaf_id
        })
    
    def cascade_remove(self, instance_id: str, leaf_id: str) -> Optional[Dict]:
        """Executa remocao em cascata de uma leaf."""
        return self._req('POST', f'/api/merkle/{instance_id}/cascade-remove', {
            'leafId': leaf_id
        })
    
    def joint_update(self, instance_id: str, leaf_id: str, new_data: str) -> Optional[Dict]:
        """Executa atualizacao conjunta de uma leaf."""
        return self._req('POST', f'/api/merkle/{instance_id}/joint-update', {
            'leafId': leaf_id,
            'newData': new_data
        })
    
    # metodos de versionamento
    def get_merkle_versions(self, instance_id: str) -> Optional[Dict]:
        """Obtem todas as versoes da arvore."""
        return self._req('GET', f'/api/merkle/{instance_id}/versions')
    
    def get_merkle_version(self, instance_id: str, version_id: int) -> Optional[Dict]:
        """Obtem uma versao especifica."""
        return self._req('GET', f'/api/merkle/{instance_id}/versions/{version_id}')
    
    def compare_versions(self, instance_id: str, version1: int, version2: int) -> Optional[Dict]:
        """Compara duas versoes da arvore."""
        return self._req('GET', f'/api/merkle/{instance_id}/compare?version1={version1}&version2={version2}')
    
    # metodos de lock
    def get_lock_status(self, instance_id: str) -> Optional[Dict]:
        """Obtem status de lock da instancia."""
        return self._req('GET', f'/api/merkle/{instance_id}/lock-status')
    
    # metodos de otimizacao
    def get_optimization_status(self) -> Optional[Dict]:
        """Obtem status do servico de otimizacao."""
        return self._req('GET', '/api/merkle/optimization/status')
    
    def toggle_optimization(self, enabled: bool) -> Optional[Dict]:
        """Ativa ou desativa otimizacao."""
        return self._req('POST', '/api/merkle/optimization/toggle', {
            'enabled': enabled
        })
    
    def encrypt_kyber(self, data: str, user: str = "dashboard") -> Optional[Dict]:
        """Criptografa dados usando Kyber + Dilithium."""
        req = urllib.request.Request(
            f"{self.base_url}/api/v1/encrypted/encrypt",
            data=json.dumps({'data': data}).encode('utf-8'),
            headers={
                'Content-Type': 'application/json',
                'X-User': user
            },
            method='POST'
        )
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                return json.loads(resp.read().decode('utf-8'))
        except:
            return None
    
    def preview_encrypted(self, encrypted_data: str, encryption_key: str = None,
                          user: str = "dashboard", ip: str = "127.0.0.1") -> Optional[Dict]:
        """Cria preview de dados criptografados."""
        req = urllib.request.Request(
            f"{self.base_url}/api/v1/encrypted/preview",
            data=json.dumps({
                'encryptedData': encrypted_data,
                'encryptionKey': encryption_key
            }).encode('utf-8'),
            headers={
                'Content-Type': 'application/json',
                'X-User': user,
                'X-IP': ip
            },
            method='POST'
        )
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                return json.loads(resp.read().decode('utf-8'))
        except:
            return None
    
    def get_audit_log(self, data_hash: str = None) -> Optional[Dict]:
        """Obtem log de auditoria."""
        if data_hash:
            return self._req('GET', f'/api/v1/encrypted/audit?data_hash={data_hash}')
        return self._req('GET', '/api/v1/encrypted/audit')
    
    def get_encrypted_stats(self) -> Optional[Dict]:
        """Obtem estatisticas de dados criptografados."""
        return self._req('GET', '/api/v1/encrypted/stats')
