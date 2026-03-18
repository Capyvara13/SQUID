"""Painel de verificacao de requisitos de servicos."""

from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QGroupBox,
    QPushButton, QTableWidget, QTableWidgetItem, QLabel,
    QHeaderView, QMessageBox, QCheckBox, QTextEdit
)
from PySide6.QtCore import Qt, QTimer
from PySide6.QtGui import QColor

from ..workers import AsyncActionWorker


class RequirementsPanel(QWidget):
    """Painel para verificacao de requisitos de servicos.
    
    Exibe uma tabela com:
    - Servico | Status | Requisitos | Problemas Detectados
    """
    
    def __init__(self, java_client, parent=None):
        super().__init__(parent)
        self.java_client = java_client
        self._active_workers = []
        self._setup_ui()
        self._load_initial_data()
    
    def _setup_ui(self):
        # aqui estou configurando a interface do painel
        layout = QVBoxLayout(self)
        layout.setSpacing(12)
        
        # grupo de controles
        controls_group = QGroupBox("Controles de Verificacao")
        controls_layout = QHBoxLayout(controls_group)
        
        self.check_btn = QPushButton("Verificar Todos")
        self.check_btn.clicked.connect(self._check_all_requirements)
        controls_layout.addWidget(self.check_btn)
        
        self.can_start_btn = QPushButton("Verificar se Pode Iniciar")
        self.can_start_btn.clicked.connect(self._check_can_start)
        controls_layout.addWidget(self.can_start_btn)
        
        self.auto_check_cb = QCheckBox("Verificacao automatica")
        self.auto_check_cb.setChecked(False)
        self.auto_check_cb.stateChanged.connect(self._toggle_auto_check)
        controls_layout.addWidget(self.auto_check_cb)
        
        controls_layout.addStretch()
        
        self.status_lbl = QLabel("Clique em 'Verificar Todos' para iniciar")
        controls_layout.addWidget(self.status_lbl)
        
        layout.addWidget(controls_group)
        
        # resumo do sistema
        summary_group = QGroupBox("Resumo do Sistema")
        summary_layout = QHBoxLayout(summary_group)
        
        self.total_services_lbl = QLabel("Total: -")
        summary_layout.addWidget(self.total_services_lbl)
        
        self.passed_lbl = QLabel("OK: -")
        self.passed_lbl.setStyleSheet("color: #22c55e; font-weight: bold;")
        summary_layout.addWidget(self.passed_lbl)
        
        self.failed_lbl = QLabel("Falhas: -")
        self.failed_lbl.setStyleSheet("color: #ef4444; font-weight: bold;")
        summary_layout.addWidget(self.failed_lbl)
        
        self.critical_lbl = QLabel("Criticos: -")
        self.critical_lbl.setStyleSheet("color: #f59e0b; font-weight: bold;")
        summary_layout.addWidget(self.critical_lbl)
        
        self.system_status_lbl = QLabel("Status: -")
        self.system_status_lbl.setStyleSheet("font-weight: bold; font-size: 12pt;")
        summary_layout.addWidget(self.system_status_lbl)
        
        summary_layout.addStretch()
        layout.addWidget(summary_group)
        
        # tabela de requisitos
        table_group = QGroupBox("Status dos Servicos")
        table_layout = QVBoxLayout(table_group)
        
        self.requirements_table = QTableWidget()
        self.requirements_table.setColumnCount(5)
        self.requirements_table.setHorizontalHeaderLabels([
            "Servico", "Status", "Requisitos", "Problemas Detectados", "Acao Corretiva"
        ])
        
        # configuracoes da tabela
        header = self.requirements_table.horizontalHeader()
        header.setSectionResizeMode(0, QHeaderView.ResizeToContents)
        header.setSectionResizeMode(1, QHeaderView.ResizeToContents)
        header.setSectionResizeMode(2, QHeaderView.Stretch)
        header.setSectionResizeMode(3, QHeaderView.Stretch)
        header.setSectionResizeMode(4, QHeaderView.Stretch)
        
        self.requirements_table.setAlternatingRowColors(True)
        self.requirements_table.setEditTriggers(QTableWidget.NoEditTriggers)
        self.requirements_table.setSelectionBehavior(QTableWidget.SelectRows)
        
        table_layout.addWidget(self.requirements_table)
        layout.addWidget(table_group)
        
        # area de acoes corretivas
        actions_group = QGroupBox("Acoes Corretivas Sugeridas")
        actions_layout = QVBoxLayout(actions_group)
        
        self.actions_text = QTextEdit()
        self.actions_text.setReadOnly(True)
        self.actions_text.setMaximumHeight(150)
        actions_layout.addWidget(self.actions_text)
        
        layout.addWidget(actions_group)
        
        # timer para verificacao automatica
        self._timer = QTimer(self)
        self._timer.timeout.connect(self._check_all_requirements)
        self._timer.setInterval(30000)  # 30 segundos
    
    def _load_initial_data(self):
        # carrega dados iniciais apos um pequeno delay
        QTimer.singleShot(500, self._check_all_requirements)
    
    def _toggle_auto_check(self, state):
        # ativa ou desativa verificacao automatica
        if state == Qt.Checked:
            self._timer.start()
            self.status_lbl.setText("Verificacao automatica ativada (30s)")
        else:
            self._timer.stop()
            self.status_lbl.setText("Verificacao automatica desativada")
    
    def _check_all_requirements(self):
        # aqui estou verificando todos os requisitos do sistema
        self.status_lbl.setText("Verificando...")
        self.check_btn.setEnabled(False)
        
        def fetch():
            return self.java_client.check_requirements()
        
        def on_complete(result):
            self.check_btn.setEnabled(True)
            if result:
                self._update_table(result)
                self._update_summary(result)
                self._update_actions(result)
            else:
                self.status_lbl.setText("Erro ao verificar requisitos")
                QMessageBox.warning(
                    self, "Erro",
                    "Nao foi possivel verificar os requisitos. "
                    "Verifique se o backend Java esta rodando."
                )
        
        worker = AsyncActionWorker(fetch)
        worker.finished.connect(on_complete)
        self._active_workers.append(worker)
        worker.finished.connect(
            lambda _: self._active_workers.remove(worker) if worker in self._active_workers else None
        )
        worker.start()
    
    def _check_can_start(self):
        # verifica se o sistema pode iniciar
        self.status_lbl.setText("Verificando se pode iniciar...")
        
        def fetch():
            return self.java_client.can_system_start()
        
        def on_complete(result):
            if result:
                can_start = result.get("canStart", False)
                status = result.get("status", "UNKNOWN")
                
                if can_start:
                    QMessageBox.information(
                        self, "Sistema Pronto",
                        f"Status: {status}\n\n"
                        "Todos os requisitos criticos estao satisfeitos. "
                        "O sistema pode ser iniciado."
                    )
                else:
                    details = result.get("correctiveActions", [])
                    msg = f"Status: {status}\n\nAcoes corretivas necessarias:\n"
                    for action in details:
                        msg += f"  - {action}\n"
                    
                    QMessageBox.warning(
                        self, "Sistema Bloqueado",
                        msg
                    )
            else:
                QMessageBox.warning(
                    self, "Erro",
                    "Nao foi possivel verificar se o sistema pode iniciar."
                )
        
        worker = AsyncActionWorker(fetch)
        worker.finished.connect(on_complete)
        self._active_workers.append(worker)
        worker.start()
    
    def _update_table(self, result: dict):
        # atualiza a tabela com os resultados
        check_results = result.get("checkResults", [])
        
        self.requirements_table.setRowCount(len(check_results))
        
        for i, row_data in enumerate(check_results):
            servico = row_data.get("servico", "Desconhecido")
            status = row_data.get("status", "UNKNOWN")
            requisitos = row_data.get("requisitos", "")
            problemas = row_data.get("problemas", "Nenhum")
            sugestao = row_data.get("sugestao", "")
            
            # cria itens da tabela
            self.requirements_table.setItem(i, 0, QTableWidgetItem(servico))
            self.requirements_table.setItem(i, 1, QTableWidgetItem(status))
            self.requirements_table.setItem(i, 2, QTableWidgetItem(requisitos))
            self.requirements_table.setItem(i, 3, QTableWidgetItem(problemas))
            self.requirements_table.setItem(i, 4, QTableWidgetItem(sugestao))
            
            # colore o status
            status_item = self.requirements_table.item(i, 1)
            if status == "OK":
                status_item.setBackground(QColor(34, 197, 94))
                status_item.setForeground(QColor(255, 255, 255))
            else:
                status_item.setBackground(QColor(239, 68, 68))
                status_item.setForeground(QColor(255, 255, 255))
    
    def _update_summary(self, result: dict):
        # atualiza o resumo do sistema
        total = result.get("totalCount", 0)
        passed = result.get("passedCount", 0)
        failed = result.get("failedCount", 0)
        critical = result.get("criticalCount", 0)
        overall = result.get("overallStatus", "UNKNOWN")
        
        self.total_services_lbl.setText(f"Total: {total}")
        self.passed_lbl.setText(f"OK: {passed}")
        self.failed_lbl.setText(f"Falhas: {failed}")
        self.critical_lbl.setText(f"Criticos: {critical}")
        self.system_status_lbl.setText(f"Status: {overall}")
        
        # colore o status geral
        if overall == "READY":
            self.system_status_lbl.setStyleSheet(
                "color: #22c55e; font-weight: bold; font-size: 12pt;"
            )
        else:
            self.system_status_lbl.setStyleSheet(
                "color: #ef4444; font-weight: bold; font-size: 12pt;"
            )
        
        self.status_lbl.setText(f"Ultima verificacao: {result.get('timestamp', 'N/A')}")
    
    def _update_actions(self, result: dict):
        # atualiza a area de acoes corretivas
        actions = result.get("correctiveActions", [])
        
        if not actions or (len(actions) == 1 and "nenhuma" in actions[0].lower()):
            self.actions_text.setHtml(
                "<p style='color: #22c55e;'>"
                "Nenhuma acao corretiva necessaria. "
                "Todos os requisitos criticos estao satisfeitos."
                "</p>"
            )
        else:
            html = "<ul>"
            for action in actions:
                html += f"<li>{action}</li>"
            html += "</ul>"
            self.actions_text.setHtml(html)
