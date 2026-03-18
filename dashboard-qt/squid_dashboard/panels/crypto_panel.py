"""Painel de operações criptográficas."""

import json
from datetime import datetime

from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QGroupBox,
    QLabel, QLineEdit, QSpinBox, QTextEdit, QPushButton,
    QProgressDialog, QMessageBox
)

from ..workers import AsyncActionWorker


class CryptoEnginePanel(QWidget):
    """Painel para operações criptográficas (Kyber + Dilithium)."""
    
    def __init__(self, java_client, parent=None):
        super().__init__(parent)
        self.java_client = java_client
        self._active_workers: list = []
        self._setup_ui()
    
    def _setup_ui(self):
        """Configura a interface."""
        layout = QVBoxLayout(self)
        layout.setSpacing(15)
        
        # Motor de Seed Iterativo
        seed_group = QGroupBox("🔁 Motor de Seed Iterativo")
        seed_layout = QVBoxLayout(seed_group)
        
        seed_form = QHBoxLayout()
        
        seed_form.addWidget(QLabel("Seed Inicial:"))
        self.seed_input = QLineEdit("SQUID-SEED-2026")
        seed_form.addWidget(self.seed_input, 2)
        
        seed_form.addWidget(QLabel("Profundidade:"))
        self.depth_spin = QSpinBox()
        self.depth_spin.setRange(1, 10)
        self.depth_spin.setValue(3)
        seed_form.addWidget(self.depth_spin)
        
        self.run_seed_btn = QPushButton("Executar")
        self.run_seed_btn.clicked.connect(self._run_seed)
        seed_form.addWidget(self.run_seed_btn)
        
        seed_layout.addLayout(seed_form)
        
        self.seed_log = QTextEdit()
        self.seed_log.setReadOnly(True)
        self.seed_log.setMaximumHeight(120)
        seed_layout.addWidget(self.seed_log)
        
        layout.addWidget(seed_group)
        
        # Operações Criptográficas
        crypto_group = QGroupBox("🔐 Operações (Kyber + Dilithium)")
        crypto_layout = QHBoxLayout(crypto_group)
        
        # Criptografar
        enc_layout = QVBoxLayout()
        enc_layout.addWidget(QLabel("<b>Texto Plano:</b>"))
        
        self.pt_input = QTextEdit()
        self.pt_input.setPlaceholderText("Digite o texto para criptografar...")
        enc_layout.addWidget(self.pt_input)
        
        self.enc_btn = QPushButton("Criptografar")
        self.enc_btn.clicked.connect(self._encrypt)
        enc_layout.addWidget(self.enc_btn)
        
        crypto_layout.addLayout(enc_layout)
        
        # Descriptografar
        dec_layout = QVBoxLayout()
        dec_layout.addWidget(QLabel("<b>Ciphertext (Base64):</b>"))
        
        self.ct_input = QTextEdit()
        self.ct_input.setPlaceholderText("Cole o ciphertext aqui...")
        dec_layout.addWidget(self.ct_input)
        
        dec_layout.addWidget(QLabel("<b>Metadados (JSON):</b>"))
        self.meta_input = QTextEdit()
        self.meta_input.setPlaceholderText('{"key": "value"}')
        dec_layout.addWidget(self.meta_input)
        
        self.dec_btn = QPushButton("Descriptografar")
        self.dec_btn.clicked.connect(self._decrypt)
        dec_layout.addWidget(self.dec_btn)
        
        crypto_layout.addLayout(dec_layout)
        
        layout.addWidget(crypto_group)
        layout.addStretch()
    
    def _run_seed(self):
        """Executa cadeia iterativa de seeds."""
        seed = self.seed_input.text()
        depth = self.depth_spin.value()
        
        progress = QProgressDialog(
            "Executando cadeia de seeds...", None, 0, 0, self
        )
        progress.show()
        
        def run():
            return self.java_client.run_iterative_seed(seed, depth)
        
        def on_complete(result):
            progress.close()
            
            if result and 'finalSeedHex' in result:
                ts = datetime.now().strftime('%H:%M:%S')
                self.seed_log.append(f"[{ts}] Cadeia completa")
                self.seed_log.append(f"Seed Final: {result.get('finalSeedHex', 'N/A')}")
                
                for lvl in result.get('levels', []):
                    self.seed_log.append(
                        f"  L{lvl.get('level', '?')} Root: {lvl.get('merkleRootHex', 'N/A')[:16]}..."
                    )
                self.seed_log.append("-" * 40)
            else:
                self.seed_log.append("Erro ao executar cadeia")
                if result and 'error' in result:
                    self.seed_log.append(f"Erro: {result['error']}")
        
        worker = AsyncActionWorker(run)
        worker.finished.connect(on_complete)
        self._active_workers.append(worker)
        worker.finished.connect(lambda _: self._active_workers.remove(worker) if worker in self._active_workers else None)
        worker.start()
    
    def _encrypt(self):
        """Criptografa texto."""
        text = self.pt_input.toPlainText()
        if not text:
            QMessageBox.warning(self, "Aviso", "Digite o texto para criptografar!")
            return
        
        progress = QProgressDialog(
            "Criptografando (Kyber+Dilithium)...", None, 0, 0, self
        )
        progress.show()
        
        def encrypt():
            return self.java_client.encrypt(text)
        
        def on_complete(result):
            progress.close()
            
            if result:
                self.ct_input.setPlainText(result.get('ciphertext', ''))
                self.meta_input.setPlainText(
                    json.dumps(result.get('metadata', {}), indent=2)
                )
                
                ts = datetime.now().strftime('%H:%M:%S')
                self.seed_log.append(f"[{ts}] Criptografado")
                self.seed_log.append(f"Hash: {result.get('hash', 'N/A')[:16]}...")
                self.seed_log.append("-" * 40)
                
                QMessageBox.information(
                    self, "Sucesso",
                    "Dados criptografados!\n\n"
                    "Ciphertext e metadados estão nos campos à direita."
                )
            else:
                QMessageBox.critical(
                    self, "Erro",
                    "Falha na criptografia. Verifique:\n"
                    "• Serviço Java está online\n"
                    "• Configuração Kyber/Dilithium"
                )
        
        worker = AsyncActionWorker(encrypt)
        worker.finished.connect(on_complete)
        self._active_workers.append(worker)
        worker.finished.connect(lambda _: self._active_workers.remove(worker) if worker in self._active_workers else None)
        worker.start()
    
    def _decrypt(self):
        """Descriptografa texto."""
        ct = self.ct_input.toPlainText()
        if not ct:
            QMessageBox.warning(self, "Aviso", "Cole o ciphertext!")
            return
        
        try:
            meta = json.loads(self.meta_input.toPlainText() or '{}')
        except json.JSONDecodeError:
            QMessageBox.critical(self, "Erro", "Metadados JSON inválidos!")
            return
        
        progress = QProgressDialog(
            "Descriptografando e validando...", None, 0, 0, self
        )
        progress.show()
        
        def decrypt():
            return self.java_client.decrypt(ct, meta)
        
        def on_complete(result):
            progress.close()
            
            if result:
                authorized = result.get('authorized', False)
                
                if authorized:
                    plaintext = result.get('plaintext', '')
                    QMessageBox.information(
                        self, "Sucesso",
                        f"Descriptografado:\n\n{plaintext[:500]}"
                    )
                    
                    ts = datetime.now().strftime('%H:%M:%S')
                    self.seed_log.append(f"[{ts}] Descriptografado")
                    self.seed_log.append("-" * 40)
                else:
                    reason = result.get('failure_reason', 'Desconhecido')
                    QMessageBox.critical(
                        self, "Acesso Negado",
                        f"Descriptografia falhou:\n{reason}"
                    )
            else:
                QMessageBox.critical(
                    self, "Erro",
                    "Falha na descriptografia (erro de rede/servidor)."
                )
        
        worker = AsyncActionWorker(decrypt)
        worker.finished.connect(on_complete)
        self._active_workers.append(worker)
        worker.finished.connect(lambda _: self._active_workers.remove(worker) if worker in self._active_workers else None)
        worker.start()
