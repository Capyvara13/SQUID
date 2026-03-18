"""Painel de monitoramento e logs do sistema."""

from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QGroupBox,
    QPushButton, QTableWidget, QTableWidgetItem, QLabel,
    QHeaderView, QLineEdit, QComboBox, QTextEdit,
    QDateTimeEdit, QFileDialog, QMessageBox
)
from PySide6.QtCore import Qt, QTimer, QDateTime
from PySide6.QtGui import QColor

from ..workers import AsyncActionWorker


class MonitoringPanel(QWidget):
    """Painel de monitoramento e logs detalhados do sistema.
    
    Funcionalidades:
    - logs em tempo real
    - busca e filtros
    - exportacao de dados
    - monitoramento de servicos
    """
    
    def __init__(self, java_client, python_client, parent=None):
        super().__init__(parent)
        self.java_client = java_client
        self.python_client = python_client
        self._active_workers = []
        self._logs = []  # armazena todos os logs para busca
        self._setup_ui()
        self._load_initial_data()
    
    def _setup_ui(self):
        # aqui estou configurando a interface do painel
        layout = QVBoxLayout(self)
        layout.setSpacing(12)
        
        # controles de filtro
        filter_group = QGroupBox("Filtros e Busca")
        filter_layout = QVBoxLayout(filter_group)
        
        # linha 1: busca por texto
        row1 = QHBoxLayout()
        row1.addWidget(QLabel("Buscar:"))
        self.search_input = QLineEdit()
        self.search_input.setPlaceholderText("Digite para buscar nos logs...")
        self.search_input.textChanged.connect(self._apply_filters)
        row1.addWidget(self.search_input)
        
        self.search_btn = QPushButton("Buscar")
        self.search_btn.clicked.connect(self._apply_filters)
        row1.addWidget(self.search_btn)
        
        self.clear_btn = QPushButton("Limpar")
        self.clear_btn.clicked.connect(self._clear_filters)
        row1.addWidget(self.clear_btn)
        
        filter_layout.addLayout(row1)
        
        # linha 2: filtros avancados
        row2 = QHBoxLayout()
        row2.addWidget(QLabel("Nivel:"))
        self.level_combo = QComboBox()
        self.level_combo.addItems(["Todos", "INFO", "WARNING", "ERROR", "DEBUG"])
        self.level_combo.currentTextChanged.connect(self._apply_filters)
        row2.addWidget(self.level_combo)
        
        row2.addWidget(QLabel("Servico:"))
        self.service_combo = QComboBox()
        self.service_combo.addItems(["Todos", "Java Backend", "Python IA", "Database", "Merkle Tree"])
        self.service_combo.currentTextChanged.connect(self._apply_filters)
        row2.addWidget(self.service_combo)
        
        row2.addWidget(QLabel("De:"))
        self.date_from = QDateTimeEdit()
        self.date_from.setCalendarPopup(True)
        self.date_from.setDateTime(QDateTime.currentDateTime().addDays(-1))
        row2.addWidget(self.date_from)
        
        row2.addWidget(QLabel("Ate:"))
        self.date_to = QDateTimeEdit()
        self.date_to.setCalendarPopup(True)
        self.date_to.setDateTime(QDateTime.currentDateTime())
        row2.addWidget(self.date_to)
        
        row2.addStretch()
        filter_layout.addLayout(row2)
        
        layout.addWidget(filter_group)
        
        # tabela de logs
        logs_group = QGroupBox("Logs do Sistema")
        logs_layout = QVBoxLayout(logs_group)
        
        self.logs_table = QTableWidget()
        self.logs_table.setColumnCount(5)
        self.logs_table.setHorizontalHeaderLabels([
            "Timestamp", "Nivel", "Servico", "Mensagem", "Detalhes"
        ])
        
        header = self.logs_table.horizontalHeader()
        header.setSectionResizeMode(0, QHeaderView.ResizeToContents)
        header.setSectionResizeMode(1, QHeaderView.ResizeToContents)
        header.setSectionResizeMode(2, QHeaderView.ResizeToContents)
        header.setSectionResizeMode(3, QHeaderView.Stretch)
        header.setSectionResizeMode(4, QHeaderView.Stretch)
        
        self.logs_table.setAlternatingRowColors(True)
        self.logs_table.setEditTriggers(QTableWidget.NoEditTriggers)
        self.logs_table.setSelectionBehavior(QTableWidget.SelectRows)
        self.logs_table.setMaximumHeight(400)
        
        logs_layout.addWidget(self.logs_table)
        
        # controles de atualizacao
        controls = QHBoxLayout()
        
        self.refresh_btn = QPushButton("Atualizar")
        self.refresh_btn.clicked.connect(self._refresh_logs)
        controls.addWidget(self.refresh_btn)
        
        self.auto_refresh_cb = QComboBox()
        self.auto_refresh_cb.addItems(["Manual", "5s", "10s", "30s", "60s"])
        self.auto_refresh_cb.currentTextChanged.connect(self._set_auto_refresh)
        controls.addWidget(self.auto_refresh_cb)
        
        controls.addStretch()
        
        # botoes de exportacao
        self.export_json_btn = QPushButton("Exportar JSON")
        self.export_json_btn.clicked.connect(lambda: self._export_logs("json"))
        controls.addWidget(self.export_json_btn)
        
        self.export_csv_btn = QPushButton("Exportar CSV")
        self.export_csv_btn.clicked.connect(lambda: self._export_logs("csv"))
        controls.addWidget(self.export_csv_btn)
        
        logs_layout.addLayout(controls)
        layout.addWidget(logs_group)
        
        # preview de detalhes
        details_group = QGroupBox("Detalhes do Log Selecionado")
        details_layout = QVBoxLayout(details_group)
        
        self.details_text = QTextEdit()
        self.details_text.setReadOnly(True)
        self.details_text.setMaximumHeight(150)
        details_layout.addWidget(self.details_text)
        
        layout.addWidget(details_group)
        
        # timer para auto-refresh
        self._timer = QTimer(self)
        self._timer.timeout.connect(self._refresh_logs)
        
        # conecta a selecao de linha
        self.logs_table.itemSelectionChanged.connect(self._show_log_details)
    
    def _load_initial_data(self):
        # carrega dados iniciais
        QTimer.singleShot(500, self._refresh_logs)
    
    def _set_auto_refresh(self, interval_text: str):
        # configura o intervalo de atualizacao automatica
        self._timer.stop()
        
        if interval_text == "Manual":
            return
        
        # converte texto para milissegundos
        seconds = int(interval_text.replace("s", ""))
        self._timer.setInterval(seconds * 1000)
        self._timer.start()
    
    def _refresh_logs(self):
        # aqui estou atualizando os logs do sistema
        self.refresh_btn.setEnabled(False)
        
        def fetch():
            # busca logs dos diferentes servicos
            logs = []
            
            # tenta buscar do java backend
            try:
                audit_logs = self.java_client.db_audit_logs(limit=100)
                if audit_logs:
                    for log in audit_logs:
                        logs.append({
                            "timestamp": log.get("timestamp", ""),
                            "level": "INFO",
                            "service": "Database",
                            "message": log.get("action", ""),
                            "details": f"Ator: {log.get('actor', 'N/A')}, Hash: {log.get('hash', 'N/A')[:20]}...",
                            "raw": log
                        })
            except Exception:
                pass
            
            # busca do python client
            try:
                stats = self.python_client.get_encrypted_stats()
                if stats:
                    logs.append({
                        "timestamp": QDateTime.currentDateTime().toString(),
                        "level": "INFO",
                        "service": "Python IA",
                        "message": f"Stats: {stats.get('totalEncrypted', 0)} itens criptografados",
                        "details": str(stats),
                        "raw": stats
                    })
            except Exception:
                pass
            
            return logs
        
        def on_complete(logs):
            self.refresh_btn.setEnabled(True)
            if logs:
                self._logs = logs
                self._apply_filters()
        
        worker = AsyncActionWorker(fetch)
        worker.finished.connect(on_complete)
        self._active_workers.append(worker)
        worker.finished.connect(
            lambda _: self._active_workers.remove(worker) if worker in self._active_workers else None
        )
        worker.start()
    
    def _apply_filters(self):
        # aplica filtros aos logs
        search_text = self.search_input.text().lower()
        level_filter = self.level_combo.currentText()
        service_filter = self.service_combo.currentText()
        
        filtered_logs = []
        
        for log in self._logs:
            # filtro por texto
            if search_text:
                match = (search_text in log.get("message", "").lower() or
                        search_text in log.get("details", "").lower() or
                        search_text in log.get("service", "").lower())
                if not match:
                    continue
            
            # filtro por nivel
            if level_filter != "Todos" and log.get("level") != level_filter:
                continue
            
            # filtro por servico
            if service_filter != "Todos":
                service_map = {
                    "Java Backend": "Java",
                    "Python IA": "Python",
                    "Database": "Database",
                    "Merkle Tree": "Merkle"
                }
                if service_map.get(service_filter, service_filter) not in log.get("service", ""):
                    continue
            
            filtered_logs.append(log)
        
        self._update_table(filtered_logs)
    
    def _clear_filters(self):
        # limpa todos os filtros
        self.search_input.clear()
        self.level_combo.setCurrentIndex(0)
        self.service_combo.setCurrentIndex(0)
        self._apply_filters()
    
    def _update_table(self, logs: list):
        # atualiza a tabela com os logs filtrados
        self.logs_table.setRowCount(len(logs))
        
        for i, log in enumerate(logs):
            self.logs_table.setItem(i, 0, QTableWidgetItem(log.get("timestamp", "")))
            
            level_item = QTableWidgetItem(log.get("level", "INFO"))
            # colore o nivel
            level_colors = {
                "ERROR": QColor(239, 68, 68),
                "WARNING": QColor(245, 158, 11),
                "INFO": QColor(34, 197, 94),
                "DEBUG": QColor(56, 189, 248)
            }
            if log.get("level") in level_colors:
                level_item.setBackground(level_colors[log.get("level")])
                level_item.setForeground(QColor(255, 255, 255))
            self.logs_table.setItem(i, 1, level_item)
            
            self.logs_table.setItem(i, 2, QTableWidgetItem(log.get("service", "")))
            self.logs_table.setItem(i, 3, QTableWidgetItem(log.get("message", "")))
            self.logs_table.setItem(i, 4, QTableWidgetItem(log.get("details", "")[:100]))
            
            # armazena o log completo na linha
            for col in range(5):
                item = self.logs_table.item(i, col)
                if item:
                    item.setData(Qt.UserRole, log)
    
    def _show_log_details(self):
        # mostra detalhes do log selecionado
        selected = self.logs_table.selectedItems()
        if not selected:
            return
        
        # pega o log da primeira coluna
        log_data = selected[0].data(Qt.UserRole)
        if log_data:
            import json
            details = json.dumps(log_data.get("raw", {}), indent=2, default=str)
            self.details_text.setText(details)
    
    def _export_logs(self, format_type: str):
        # exporta os logs para arquivo
        if not self._logs:
            QMessageBox.warning(self, "Aviso", "Nenhum log para exportar")
            return
        
        # abre dialogo para escolher o arquivo
        if format_type == "json":
            filename, _ = QFileDialog.getSaveFileName(
                self, "Exportar Logs", "logs.json", "JSON (*.json)"
            )
            if filename:
                import json
                try:
                    with open(filename, 'w', encoding='utf-8') as f:
                        json.dump([log.get("raw", log) for log in self._logs], f, indent=2, default=str)
                    QMessageBox.information(self, "Sucesso", f"Logs exportados para {filename}")
                except Exception as e:
                    QMessageBox.critical(self, "Erro", f"Falha ao exportar: {str(e)}")
        
        elif format_type == "csv":
            filename, _ = QFileDialog.getSaveFileName(
                self, "Exportar Logs", "logs.csv", "CSV (*.csv)"
            )
            if filename:
                import csv
                try:
                    with open(filename, 'w', newline='', encoding='utf-8') as f:
                        writer = csv.writer(f)
                        writer.writerow(["Timestamp", "Level", "Service", "Message", "Details"])
                        for log in self._logs:
                            writer.writerow([
                                log.get("timestamp", ""),
                                log.get("level", ""),
                                log.get("service", ""),
                                log.get("message", ""),
                                log.get("details", "")
                            ])
                    QMessageBox.information(self, "Sucesso", f"Logs exportados para {filename}")
                except Exception as e:
                    QMessageBox.critical(self, "Erro", f"Falha ao exportar: {str(e)}")
