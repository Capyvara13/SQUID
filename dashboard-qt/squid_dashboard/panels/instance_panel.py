"""Painel de gerenciamento de instâncias SQUID."""

import json
import time
import random
from datetime import datetime
from typing import Dict, List, Optional

from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QPushButton, QTableWidget, QTableWidgetItem,
    QHeaderView, QMenu, QMessageBox, QInputDialog, QProgressDialog,
    QFileDialog, QDialog
)
from PySide6.QtCore import Qt, QTimer
from PySide6.QtGui import QColor, QAction

from ..dialogs import CreateInstanceDialog
from ..workers import AsyncActionWorker

# Text-based file extensions (everything else is treated as binary)
_TEXT_EXTENSIONS = {'txt', 'json', 'xml', 'csv', 'html', 'htm', 'md', 'yaml', 'yml', 'log', 'ini', 'cfg', 'toml'}


class InstanceManagerPanel(QWidget):
    """Painel para gerenciamento completo de instâncias SQUID."""
    
    def __init__(self, java_client, parent=None):
        super().__init__(parent)
        self.java_client = java_client
        self.parent_window = parent
        self._active_workers: list = []  # prevent GC of running workers
        self._setup_ui()
        self._load_initial_data()
    
    def _setup_ui(self):
        """Configura a interface do painel."""
        layout = QVBoxLayout(self)
        layout.setSpacing(12)
        
        # Controles superiores
        controls = QHBoxLayout()
        
        self.create_btn = QPushButton("+ Criar Instância")
        self.create_btn.setObjectName("StartBtn")
        self.create_btn.setToolTip("Criar nova instância com configuração B, M, T")
        self.create_btn.clicked.connect(self._create_instance)
        controls.addWidget(self.create_btn)
        
        controls.addStretch()
        
        self.refresh_btn = QPushButton("Atualizar")
        self.refresh_btn.setToolTip("Atualizar lista de instâncias")
        self.refresh_btn.clicked.connect(self._refresh_list)
        controls.addWidget(self.refresh_btn)
        
        layout.addLayout(controls)
        
        # Tabela de instâncias
        self.table = QTableWidget()
        self.table.setColumnCount(7)
        self.table.setHorizontalHeaderLabels([
            "Nome", "Status", "Leafs", "Config (B/M/T)", "Merkle Root", "Criado em", "Ações"
        ])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.Interactive)
        self.table.horizontalHeader().setStretchLastSection(True)
        self.table.setSelectionBehavior(QTableWidget.SelectRows)
        self.table.setSelectionMode(QTableWidget.SingleSelection)
        self.table.setAlternatingRowColors(True)
        layout.addWidget(self.table)
        
        # Barra de status
        self.status_label = QLabel("Pronto")
        layout.addWidget(self.status_label)
    
    def _load_initial_data(self):
        """Carrega dados iniciais após inicialização."""
        QTimer.singleShot(500, self._refresh_list)
    
    def _create_instance(self):
        """Abre diálogo para criar nova instância."""
        dialog = CreateInstanceDialog(self)
        if dialog.exec() == QDialog.Accepted:
            data = dialog.get_instance_data()
            self._process_instance_creation(data)
    
    def _process_instance_creation(self, data: Dict):
        """Processa a criação de instância com feedback ao usuário.
        
        Args:
            data: Dicionário com name, B, M, T, data
        """
        progress = QProgressDialog(
            "Criando instância SQUID...",
            None, 0, 0, self
        )
        progress.setWindowTitle("SQUID")
        progress.setWindowModality(Qt.WindowModal)
        progress.show()
        
        def create():
            """Função executada em thread separada."""
            # Prepara configuração
            config = {
                'B': data['B'],
                'M': data['M'],
                'T': data['T'],
                'data': data['data']
            }
            return self.java_client.create_instance(data['name'], config)
        
        def on_complete(result):
            """Callback quando a criação termina."""
            progress.close()
            
            if result:
                self._log_action(
                    f"Instância criada: {data['name']} "
                    f"(B={data['B']}, M={data['M']}, T={data['T']})"
                )
                
                QMessageBox.information(
                    self,
                    "Sucesso",
                    f"Instância '{data['name']}' criada com sucesso!\n\n"
                    f"Configuração:\n"
                    f"  • B (Branching): {data['B']}\n"
                    f"  • M (Depth): {data['M']}\n"
                    f"  • T (Leaves): {data['T']}\n\n"
                    f"Próximos passos:\n"
                    f"  1. Acesse 'Visualizar Leafs' para ver a árvore\n"
                    f"  2. Use 'Criar Endpoint HTTP' para expor via API\n"
                    f"  3. 'Re-criptografar' para renovar a segurança"
                )
                self._refresh_list()
            else:
                QMessageBox.warning(
                    self,
                    "Erro",
                    "Falha ao criar instância.\n\n"
                    "Verifique:\n"
                    "  • Serviço HTTP API está online\n"
                    "  • Conexão com backend Java (porta 8080)"
                )
        
        worker = AsyncActionWorker(create)
        worker.finished.connect(on_complete)
        worker.error.connect(lambda e: self._handle_error("criação", e))
        self._keep_worker(worker)
        worker.start()
    
    def _refresh_list(self):
        """Atualiza a lista de instâncias da API."""
        self.status_label.setText("Atualizando...")
        
        def fetch():
            return self.java_client.list_instances()
        
        def on_complete(data):
            self.status_label.setText("Pronto")
            if data is None:
                # API indisponível, mostra dados de exemplo
                self._show_sample_data()
            else:
                self._populate_table(data)
        
        worker = AsyncActionWorker(fetch)
        worker.finished.connect(on_complete)
        worker.error.connect(lambda e: self._show_sample_data())
        self._keep_worker(worker)
        worker.start()
    
    def _show_sample_data(self):
        """Mostra dados de exemplo quando API está indisponível."""
        sample_data = [
            {
                'id': 'demo-1',
                'name': 'instancia_demo',
                'status': 'ACTIVE',
                'leaf_count': 256,
                'merkle_root': 'a1b2c3d4e5f6789...',
                'created_at': datetime.now().isoformat()
            },
            {
                'id': 'demo-2',
                'name': 'teste_criptografia',
                'status': 'PAUSED',
                'leaf_count': 512,
                'merkle_root': 'b2c3d4e5f6a7b8c...',
                'created_at': datetime.now().isoformat()
            }
        ]
        self._populate_table(sample_data)
        self.status_label.setText("Modo demonstração (API indisponível)")
    
    def _populate_table(self, data: List[Dict]):
        """Preenche a tabela com dados das instâncias.
        
        Args:
            data: Lista de dicionários com dados das instâncias
        """
        self.table.setRowCount(0)
        
        for row, inst in enumerate(data):
            self.table.insertRow(row)
            
            # Nome (store inst_id as Qt.UserRole data for lookup)
            name_item = QTableWidgetItem(inst.get('name', 'N/A'))
            name_item.setData(Qt.UserRole, inst.get('id', ''))
            self.table.setItem(row, 0, name_item)
            
            # Status com cor
            status_item = QTableWidgetItem(inst.get('status', 'UNKNOWN'))
            status = inst.get('status', '')
            if status == 'ACTIVE':
                status_item.setForeground(QColor(34, 197, 94))  # Green
            elif status == 'PAUSED':
                status_item.setForeground(QColor(245, 158, 11))  # Yellow
            elif status in ('DESTROYED', 'FINALIZED'):
                status_item.setForeground(QColor(239, 68, 68))  # Red
            self.table.setItem(row, 1, status_item)
            
            # Leafs
            leaf_count = inst.get('leaf_count', 
                                inst.get('ephemeralKeysCount', 0))
            self.table.setItem(row, 2, QTableWidgetItem(str(leaf_count)))
            
            # Config (B/M/T)
            b = inst.get('config_B', '?')
            m = inst.get('config_M', '?')
            t = inst.get('config_T', '?')
            config_text = f"B={b}  M={m}  T={t}"
            config_item = QTableWidgetItem(config_text)
            config_item.setForeground(QColor(148, 163, 184))
            self.table.setItem(row, 3, config_item)
            
            # Merkle Root (truncado)
            root = inst.get('merkle_root', 'N/A')
            if len(root) > 20:
                root = root[:20] + "..."
            self.table.setItem(row, 4, QTableWidgetItem(root))
            
            # Criado em
            created = inst.get('created_at', 'N/A')
            if isinstance(created, str) and 'T' in created:
                created = created.split('T')[0]
            self.table.setItem(row, 5, QTableWidgetItem(str(created)))
            
            # Botão de ações
            actions_widget = QWidget()
            actions_layout = QHBoxLayout(actions_widget)
            actions_layout.setContentsMargins(4, 2, 4, 2)
            
            actions_btn = QPushButton("Ações ▼")
            actions_btn.setObjectName("ActionBtn")
            actions_btn.setMaximumWidth(90)
            actions_btn.setMenu(self._create_actions_menu(inst))
            
            actions_layout.addWidget(actions_btn)
            actions_layout.addStretch()
            
            self.table.setCellWidget(row, 6, actions_widget)
    
    def _create_actions_menu(self, inst: Dict) -> QMenu:
        """Cria menu de contexto para ações da instância.
        
        Args:
            inst: Dicionário com dados da instância
            
        Returns:
            QMenu com ações disponíveis
        """
        menu = QMenu(self)
        inst_id = inst.get('id', '')
        inst_name = inst.get('name', '')
        
        # Visualização
        view_leaves = QAction("Visualizar Leafs", self)
        view_leaves.triggered.connect(
            lambda: self._view_leaves(inst_id, inst_name)
        )
        menu.addAction(view_leaves)
        
        view_details = QAction("Ver Detalhes", self)
        view_details.triggered.connect(
            lambda: self._view_details(inst)
        )
        menu.addAction(view_details)
        
        menu.addSeparator()
        
        # Operações de API
        create_endpoint = QAction("Criar Endpoint HTTP", self)
        create_endpoint.triggered.connect(
            lambda: self._create_endpoint(inst_id, inst_name)
        )
        menu.addAction(create_endpoint)
        
        send_db = QAction("Enviar para Banco", self)
        send_db.triggered.connect(
            lambda: self._send_to_database(inst_id, inst_name)
        )
        menu.addAction(send_db)
        
        menu.addSeparator()
        
        # Operações criptográficas
        reencrypt = QAction("Re-criptografar", self)
        reencrypt.triggered.connect(
            lambda: self._reencrypt_instance(inst_id, inst_name)
        )
        menu.addAction(reencrypt)
        
        decrypt = QAction("Descriptografar", self)
        decrypt.triggered.connect(
            lambda: self._decrypt_instance(inst_id, inst_name)
        )
        menu.addAction(decrypt)
        
        remove_leaves = QAction("Remover Leafs", self)
        remove_leaves.triggered.connect(
            lambda: self._remove_leaves(inst_id, inst_name)
        )
        menu.addAction(remove_leaves)
        
        menu.addSeparator()
        
        # Ação crítica
        delete_action = QAction("Excluir Instancia", self)
        delete_action.setObjectName("CriticalAction")
        delete_action.triggered.connect(
            lambda: self._delete_instance(inst_id, inst_name)
        )
        menu.addAction(delete_action)
        
        return menu
    
    # === AÇÕES DE INSTÂNCIA ===
    
    def _view_leaves(self, inst_id: str, inst_name: str):
        """Visualiza as folhas de uma instância buscando dados reais da API."""
        self._log_action(f"Visualizando leafs: {inst_name}")
        
        from ..dialogs import LeafViewerDialog
        
        # Fetch instance data for header info
        instances = self.java_client.list_instances()
        inst_data = None
        if instances:
            for inst in instances:
                if inst.get('id') == inst_id:
                    inst_data = inst
                    break
        
        dialog = LeafViewerDialog(inst_id, inst_name, self.java_client, inst_data, self)
        dialog.exec()
    
    def _view_details(self, inst: Dict):
        """Mostra detalhes completos da instância com histórico."""
        inst_id = inst.get('id', '')
        
        # Fetch history from backend
        history_data = self.java_client.get_instance_history(inst_id)
        
        # Build formatted detail text
        lines = [
            f"ID: {inst.get('id', 'N/A')}",
            f"Nome: {inst.get('name', 'N/A')}",
            f"Status: {inst.get('status', 'N/A')}",
            f"Folhas: {inst.get('leaf_count', 'N/A')}",
            f"Merkle Root: {inst.get('merkle_root', 'N/A')}",
            f"Config: B={inst.get('config_B', '?')} M={inst.get('config_M', '?')} T={inst.get('config_T', '?')}",
            f"Criado em: {inst.get('created_at', 'N/A')}",
            "",
            "=== HISTÓRICO ===",
        ]
        
        if history_data and isinstance(history_data.get('history'), list):
            for entry in history_data['history']:
                ts = entry.get('timestamp', '?')
                action = entry.get('action', '?')
                detail = entry.get('details', '')
                lines.append(f"[{ts}] {action}: {detail}")
        else:
            lines.append("(sem histórico disponível)")
        
        details_text = "\n".join(lines)
        
        msg = QMessageBox(self)
        msg.setWindowTitle("Detalhes da Instância")
        msg.setText(f"Instância: {inst.get('name', 'N/A')} ({inst.get('status', 'N/A')})")
        msg.setDetailedText(details_text)
        msg.setStandardButtons(QMessageBox.Ok)
        msg.exec()
    
    def _create_endpoint(self, inst_id: str, inst_name: str):
        """Mostra como acessar a instância via HTTP API."""
        # The instance is already accessible via the REST API once the
        # HTTP API service is running; there is no separate "create" step.
        url = f"http://localhost:8080/api/v1/instances"
        
        QMessageBox.information(
            self,
            "Acesso HTTP",
            f"Instância: {inst_name} (ID: {inst_id})\n\n"
            f"A instância já é acessível via API REST quando o serviço\n"
            f"HTTP API está online (porta 8080).\n\n"
            f"Endpoints disponiveis:\n"
            f"  • GET {url}  ->  Listar instancias\n"
            f"  • POST {url}/{inst_id}/reencrypt  ->  Re-criptografar\n"
            f"  • POST {url}/{inst_id}/decrypt  ->  Descriptografar\n"
            f"  • POST {url}/{inst_id}/remove-leaves  ->  Remover folhas\n"
            f"  • POST {url}/{inst_id}/export  ->  Exportar para banco"
        )
        self._log_action(f"Endpoint HTTP consultado: {inst_name}")
    
    def _send_to_database(self, inst_id: str, inst_name: str):
        """Envia instância para banco de dados."""
        db_options = ["MySQL", "PostgreSQL"]
        choice, ok = QInputDialog.getItem(
            self,
            "Enviar para Banco",
            f"Instância: {inst_name}\nEscolha o banco de dados:",
            db_options, 0, False
        )
        if not ok:
            return
        db_type = choice.lower()
        
        progress = QProgressDialog(
            f"Enviando para {db_type.upper()}...",
            None, 0, 0, self
        )
        progress.show()
        
        def send():
            return self.java_client.send_to_database(inst_id, db_type)
        
        def on_complete(result):
            progress.close()
            if result:
                self._log_action(f"Instância {inst_name} enviada para {db_type}")
                QMessageBox.information(
                    self, "Sucesso",
                    f"Instância exportada com sucesso!"
                )
            else:
                QMessageBox.warning(
                    self, "Erro",
                    "Falha ao exportar instância."
                )
        
        worker = AsyncActionWorker(send)
        worker.finished.connect(on_complete)
        worker.start()
    
    def _reencrypt_instance(self, inst_id: str, inst_name: str):
        """Re-criptografa uma instância."""
        reply = QMessageBox.question(
            self,
            "Re-criptografar",
            f"Instância: {inst_name}\n\n"
            "Isso irá:\n"
            "  1. Descriptografar dados atuais\n"
            "  2. Gerar nova seed aleatória\n"
            "  3. Criar nova árvore Merkle\n"
            "  4. Nova assinatura Dilithium\n\n"
            "Continuar?",
            QMessageBox.Yes | QMessageBox.No,
            QMessageBox.No
        )
        
        if reply != QMessageBox.Yes:
            return
        
        progress = QProgressDialog("Re-criptografando...", None, 0, 0, self)
        progress.show()
        
        def reencrypt():
            return self.java_client.reencrypt_instance(inst_id)
        
        def on_complete(result):
            progress.close()
            if result:
                new_root = result.get('new_merkle_root', 'N/A')[:16]
                self._log_action(
                    f"Re-criptografada: {inst_name} - Novo root: {new_root}..."
                )
                QMessageBox.information(
                    self, "Sucesso",
                    f"Instância re-criptografada!\n\n"
                    f"Novo Merkle Root: {new_root}..."
                )
                self._refresh_list()
            else:
                QMessageBox.warning(self, "Erro", "Falha na re-criptografia.")
        
        worker = AsyncActionWorker(reencrypt)
        worker.finished.connect(on_complete)
        worker.start()
    
    def _decrypt_instance(self, inst_id: str, inst_name: str):
        """Descriptografa instância para arquivo."""
        ext, ok = QInputDialog.getText(
            self,
            "Descriptografar",
            "Extensão do arquivo (txt, pdf, png, json, xml):",
            text="txt"
        )
        
        if not ok or not ext:
            return
        
        # Remove ponto inicial se presente
        ext = ext.lstrip('.')
        
        # Diálogo para escolher onde salvar
        filename = f"{inst_name}_decrypted.{ext}"
        filepath, _ = QFileDialog.getSaveFileName(
            self,
            "Salvar arquivo descriptografado",
            filename,
            f"Arquivos (*.{ext})"
        )
        
        if not filepath:
            return
        
        progress = QProgressDialog("Descriptografando...", None, 0, 0, self)
        progress.show()
        
        def decrypt():
            return self.java_client.decrypt_instance(inst_id, ext)
        
        def on_complete(result):
            progress.close()
            if result:
                # Salvar arquivo
                try:
                    content = result.get('content', '')
                    if ext.lower() in _TEXT_EXTENSIONS:
                        with open(filepath, 'w', encoding='utf-8') as f:
                            f.write(content)
                    else:
                        import base64
                        with open(filepath, 'wb') as f:
                            f.write(base64.b64decode(content) if content else b'')
                    
                    self._log_action(f"Descriptografado: {inst_name} -> {filepath}")
                    QMessageBox.information(
                        self, "Sucesso",
                        f"Arquivo salvo:\n{filepath}"
                    )
                except Exception as e:
                    QMessageBox.critical(self, "Erro", f"Falha ao salvar: {e}")
            else:
                QMessageBox.warning(self, "Erro", "Falha na descriptografia.")
        
        worker = AsyncActionWorker(decrypt)
        worker.finished.connect(on_complete)
        worker.start()
    
    def _remove_leaves(self, inst_id: str, inst_name: str):
        """Remove folhas da instância."""
        # Determine actual leaf count from table data
        leaf_count = self._get_instance_leaf_count(inst_id)
        if leaf_count <= 0:
            QMessageBox.warning(self, "Aviso", "Instância não possui folhas.")
            return
        
        count, ok = QInputDialog.getInt(
            self,
            "Remover Leafs",
            f"Número de folhas para remover de {inst_name} (total: {leaf_count}):",
            1, 1, min(100, leaf_count), 1
        )
        
        if not ok:
            return
        
        indices = random.sample(range(leaf_count), min(count, leaf_count))
        
        reply = QMessageBox.question(
            self,
            "Confirmar",
            f"Remover {count} folha(s) da instância {inst_name}?\n\n"
            f"Índices selecionados: {indices[:5]}...\n\n"
            "Isso irá recalcular a árvore Merkle.",
            QMessageBox.Yes | QMessageBox.No
        )
        
        if reply != QMessageBox.Yes:
            return
        
        progress = QProgressDialog("Removendo folhas...", None, 0, 0, self)
        progress.show()
        
        def remove():
            return self.java_client.remove_leaves(inst_id, indices)
        
        def on_complete(result):
            progress.close()
            if result:
                new_root = result.get('new_merkle_root', 'N/A')[:16]
                self._log_action(
                    f"Removidas {count} folhas de {inst_name}"
                )
                QMessageBox.information(
                    self, "Sucesso",
                    f"{count} folha(s) removidas!\n\n"
                    f"Novo Merkle Root: {new_root}..."
                )
                self._refresh_list()
            else:
                QMessageBox.warning(self, "Erro", "Falha ao remover folhas.")
        
        worker = AsyncActionWorker(remove)
        worker.finished.connect(on_complete)
        worker.start()
    
    def _delete_instance(self, inst_id: str, inst_name: str):
        """Exclui uma instância permanentemente."""
        reply = QMessageBox.warning(
            self,
            "EXCLUIR INSTANCIA",
            f"Instancia: {inst_name}\n\n"
            "ATENCAO: Esta acao e IRREVERSIVEL!\n\n"
            "Será removido:\n"
            "  • Seed criptográfica\n"
            "  • Todas as folhas\n"
            "  • Árvore Merkle completa\n"
            "  • Histórico de operações\n"
            "  • Logs da IA\n\n"
            "Deseja continuar?",
            QMessageBox.Yes | QMessageBox.No,
            QMessageBox.No
        )
        
        if reply != QMessageBox.Yes:
            return
        
        # Segunda confirmação
        confirm, ok = QInputDialog.getText(
            self,
            "Confirmação",
            f"Digite '{inst_name}' para confirmar a exclusão:"
        )
        
        if not ok or confirm != inst_name:
            QMessageBox.information(self, "Cancelado", "Exclusão cancelada.")
            return
        
        progress = QProgressDialog("Excluindo instância...", None, 0, 0, self)
        progress.show()
        
        def delete():
            return self.java_client.cancel_instance(inst_id)
        
        def on_complete(result):
            progress.close()
            if result:
                self._log_action(f"Instância EXCLUÍDA: {inst_name}")
                QMessageBox.information(
                    self, "Excluído",
                    f"Instância '{inst_name}' foi excluída permanentemente."
                )
                self._refresh_list()
            else:
                QMessageBox.warning(self, "Erro", "Falha ao excluir instância.")
        
        worker = AsyncActionWorker(delete)
        worker.finished.connect(on_complete)
        worker.start()
    
    def _log_action(self, message: str):
        """Registra uma ação no log.
        
        Args:
            message: Mensagem a ser logada
        """
        timestamp = datetime.now().strftime('%H:%M:%S')
        log_entry = f"[{timestamp}] [INSTANCE] {message}"
        
        # Envia para o log principal se disponível
        if self.parent_window and hasattr(self.parent_window, 'add_log'):
            self.parent_window.add_log(log_entry)
        
        print(log_entry)  # Fallback
    
    def _get_instance_leaf_count(self, inst_id: str) -> int:
        """Obtém contagem de folhas de uma instância a partir da tabela."""
        for row in range(self.table.rowCount()):
            name_item = self.table.item(row, 0)
            leaf_item = self.table.item(row, 2)
            if name_item and leaf_item:
                stored_id = name_item.data(Qt.UserRole)
                if stored_id == inst_id:
                    try:
                        return int(leaf_item.text())
                    except (ValueError, TypeError):
                        pass
        return 256  # fallback
    
    def _keep_worker(self, worker: AsyncActionWorker):
        """Armazena referência ao worker para evitar GC e remove ao finalizar."""
        self._active_workers.append(worker)
        worker.finished.connect(lambda _: self._discard_worker(worker))
        worker.error.connect(lambda _: self._discard_worker(worker))
    
    def _discard_worker(self, worker: AsyncActionWorker):
        """Remove referência ao worker."""
        try:
            self._active_workers.remove(worker)
        except ValueError:
            pass
    
    def _handle_error(self, operation: str, error: str):
        """Trata erros de operações.
        
        Args:
            operation: Nome da operação
            error: Mensagem de erro
        """
        QMessageBox.critical(
            self,
            f"Erro na {operation}",
            f"Falha durante {operation}:\n\n{error}"
        )
