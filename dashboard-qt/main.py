#!/usr/bin/env python3
"""SQUID Qt Dashboard (PySide6)

Minimal functional UI that starts the Java IPCMain process and communicates
via stdin/stdout JSON lines. Provides buttons for Health, Decide, Rotate and
History. This is a scaffold mirroring core functionality from the React
dashboard.

Usage:
    python main.py

Requirements:
    pip install -r requirements.txt

Note: This script expects Maven+JDK to be available and will by default try
to start the Java IPC using Maven exec plugin. You can change the command
in the UI before starting.
"""
import sys
import json
import subprocess
import os
import threading
import time
from pathlib import Path
import shutil
import urllib.request
import urllib.error

from PySide6.QtWidgets import (QApplication, QWidget, QPushButton, QTextEdit,
                               QVBoxLayout, QHBoxLayout, QLabel, QLineEdit,
                               QMessageBox, QCheckBox, QSpinBox, QGraphicsView,
                               QGraphicsScene, QListWidget, QFileDialog)
from PySide6.QtGui import QBrush, QPen, QColor
from PySide6.QtCore import QRectF
from PySide6.QtCore import Slot, Signal, QObject

DEFAULT_JAVA_CMD = 'mvn -f java-backend\\pom.xml exec:java -Dexec.mainClass=com.squid.core.ipc.IPCMain'


class IPCWrapper(QObject):
    received = Signal(str)

    def __init__(self, cmd):
        super().__init__()
        self.cmd = cmd
        self.proc = None
        self._reader_thread = None
        self._running = False

    def start(self):
        # Start Java process via shell command
        try:
            # use shell to support complex mvn commands; user must ensure PATH
            # Run from repository root so relative -f paths resolve correctly
            repo_root = Path(__file__).resolve().parents[1]

            # Detect a host Python 3 executable and set SQUID_PYTHON env var
            # Prefer the 'py' launcher on Windows because PATH may point to the Microsoft Store shim
            chosen_py = None
            py_launcher = shutil.which('py')
            if py_launcher:
                # try to resolve the actual python executable used by py -3
                try:
                    out = subprocess.check_output([py_launcher, '-3', '-c', 'import sys;print(sys.executable)'], text=True).strip()
                    if out:
                        chosen_py = out
                except Exception:
                    # fall back to using the 'py' launcher itself
                    chosen_py = py_launcher
            else:
                # no py launcher, search for a python executable but avoid the WindowsApps shim
                for pc in ("python3", "python"):
                    ppath = shutil.which(pc)
                    if ppath and "WindowsApps" not in ppath:
                        chosen_py = ppath
                        break

            if chosen_py:
                # export for child processes (Popen inherits current env)
                try:
                    os.environ['SQUID_PYTHON'] = chosen_py
                    self.log.append('Set SQUID_PYTHON -> ' + chosen_py)
                except Exception:
                    pass

            # Replace common relative POM references with absolute path so mvn -f works
            pom_abs = str(repo_root.joinpath('java-backend', 'pom.xml'))

            # Prefer launching a pre-built JAR with java if available to avoid Maven at runtime
            jar_candidates = [repo_root.joinpath('java-backend', 'target', 'squid-core-1.0.0.jar'),
                              repo_root.joinpath('java-backend', 'target', 'squid-core-1.0.0.jar.original')]
            java_path = shutil.which('java')
            chosen_jar = None
            for j in jar_candidates:
                if j.exists():
                    chosen_jar = str(j)
                    break

            if java_path and chosen_jar:
                # Use Spring's PropertiesLauncher to run the IPCMain entrypoint inside the repackaged jar
                # This avoids starting the web app (SquidApplication) and allows IPCMain to read stdin/stdout.
                args = [java_path, '-Dloader.main=com.squid.core.ipc.IPCMain', '-cp', chosen_jar, 'org.springframework.boot.loader.PropertiesLauncher']
                self.effective_cmd = ' '.join(f'"{a}"' if ' ' in a else a for a in args)
                self.choice_message = 'Launching built JAR (PropertiesLauncher -> IPCMain): ' + self.effective_cmd
                self.proc = subprocess.Popen(args, cwd=str(repo_root), stdin=subprocess.PIPE,
                                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                             text=True, bufsize=1)
                self._running = True
                self._reader_thread = threading.Thread(target=self._reader, daemon=True)
                self._reader_thread.start()
                return

            # Prefer system 'mvn' when available to avoid wrapper download attempts
            mvn_path = shutil.which('mvn')
            mvnw = repo_root.joinpath('java-backend', 'mvnw.cmd')
            wrapper_jar = repo_root.joinpath('java-backend', '.mvn', 'wrapper', 'maven-wrapper.jar')

            if mvn_path:
                # Use system mvn
                args = [mvn_path, '-f', pom_abs, 'exec:java', '-Dexec.mainClass=com.squid.core.ipc.IPCMain']
                self.effective_cmd = ' '.join(f'"{a}"' if ' ' in a else a for a in args)
                self.choice_message = 'Using system mvn: ' + self.effective_cmd
                self.proc = subprocess.Popen(args, cwd=str(repo_root), stdin=subprocess.PIPE,
                                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                             text=True, bufsize=1)
            elif mvnw.exists() and wrapper_jar.exists():
                # Use wrapper only when its JAR is present to avoid online download attempts
                mvnw_path = str(mvnw)
                args = [mvnw_path, '-f', pom_abs, 'exec:java', '-Dexec.mainClass=com.squid.core.ipc.IPCMain']
                self.effective_cmd = ' '.join(f'"{a}"' if ' ' in a else a for a in args)
                self.choice_message = 'Using mvnw wrapper: ' + self.effective_cmd
                self.proc = subprocess.Popen(args, cwd=str(repo_root), stdin=subprocess.PIPE,
                                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                             text=True, bufsize=1)
            else:
                # No system mvn and no usable wrapper; fall back to shell command
                cmd_for_proc = self.cmd.replace('java-backend\\pom.xml', pom_abs).replace('java-backend/pom.xml', pom_abs)
                cmd_for_proc = cmd_for_proc.replace('-f java-backend', f'-f "{pom_abs}"').replace("-f java-backend", f'-f "{pom_abs}"')
                self.effective_cmd = cmd_for_proc
                self.choice_message = 'Falling back to shell command: ' + self.effective_cmd
                self.proc = subprocess.Popen(cmd_for_proc, shell=True, cwd=str(repo_root), stdin=subprocess.PIPE,
                                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                             text=True, bufsize=1)
        except Exception as e:
            raise

        self._running = True
        self._reader_thread = threading.Thread(target=self._reader, daemon=True)
        self._reader_thread.start()

    def _reader(self):
        try:
            while self._running and self.proc:
                line = self.proc.stdout.readline()
                if not line:
                    break
                self.received.emit(line.strip())
        except Exception:
            pass

    def stop(self):
        self._running = False
        try:
            if self.proc and self.proc.poll() is None:
                self.proc.terminate()
                time.sleep(0.5)
                if self.proc.poll() is None:
                    self.proc.kill()
        except Exception:
            pass

    def send(self, obj: dict):
        if not self.proc or self.proc.stdin is None:
            raise RuntimeError('Process not started')
        s = json.dumps(obj)
        self.proc.stdin.write(s + '\n')
        self.proc.stdin.flush()


class MainWindow(QWidget):
    def __init__(self):
        super().__init__()
        self.setWindowTitle('SQUID Dashboard (Qt)')
        self.resize(900, 600)

        self.ipc = None
        # Per-leaf metadata from AI decisions (index -> action)
        self._leaf_actions = {}

        self.javaCmdInput = QLineEdit(DEFAULT_JAVA_CMD)
        self.startBtn = QPushButton('Start Java IPC')
        self.stopBtn = QPushButton('Stop Java IPC')
        self.healthBtn = QPushButton('Health')
        self.decideBtn = QPushButton('Decide (sample)')
        self.rotateBtn = QPushButton('Rotate (sample)')
        self.historyBtn = QPushButton('Get History')
        self.refreshHealthBtn = QPushButton('Refresh System Health')
        self.refreshCryptoBtn = QPushButton('Refresh Crypto Ops')
        self.autoApplyCheck = QCheckBox('Auto-apply rotation')

        self.log = QTextEdit()
        self.log.setReadOnly(True)

        topLayout = QHBoxLayout()
        topLayout.addWidget(QLabel('Java start command:'))
        topLayout.addWidget(self.javaCmdInput)
        topLayout.addWidget(self.startBtn)
        topLayout.addWidget(self.stopBtn)

        # Python executable selector
        pyLayout = QHBoxLayout()
        pyLayout.addWidget(QLabel('Python exe:'))
        self.pythonPathInput = QLineEdit()
        self.pythonPathInput.setPlaceholderText('Optional: full path to python.exe')
        pyLayout.addWidget(self.pythonPathInput)
        self.pythonBrowseBtn = QPushButton('Browse')
        pyLayout.addWidget(self.pythonBrowseBtn)
        topLayout.addLayout(pyLayout)

        # Branching params
        paramsLayout = QHBoxLayout()
        paramsLayout.addWidget(QLabel('b:'))
        self.bSpin = QSpinBox(); self.bSpin.setRange(2, 8); self.bSpin.setValue(4)
        paramsLayout.addWidget(self.bSpin)
        paramsLayout.addWidget(QLabel('m:'))
        self.mSpin = QSpinBox(); self.mSpin.setRange(1, 6); self.mSpin.setValue(3)
        paramsLayout.addWidget(self.mSpin)
        paramsLayout.addWidget(QLabel('t:'))
        self.tSpin = QSpinBox(); self.tSpin.setRange(1, 1024); self.tSpin.setValue(128)
        paramsLayout.addWidget(self.tSpin)
        paramsLayout.addWidget(QLabel('Seed:'))
        self.seedInput = QLineEdit()
        self.seedInput.setPlaceholderText('Enter seed string for PQC key / leaf derivation')
        paramsLayout.addWidget(self.seedInput)
        self.generateBtn = QPushButton('Generate PQC & Build Merkle')
        paramsLayout.addWidget(self.generateBtn)

        btnLayout = QHBoxLayout()
        btnLayout.addWidget(self.healthBtn)
        btnLayout.addWidget(self.decideBtn)
        btnLayout.addWidget(self.rotateBtn)
        btnLayout.addWidget(self.historyBtn)
        btnLayout.addWidget(self.refreshHealthBtn)
        btnLayout.addWidget(self.refreshCryptoBtn)
        btnLayout.addWidget(self.autoApplyCheck)

        # Left area: controls and log
        leftLayout = QVBoxLayout()
        leftLayout.addLayout(topLayout)
        leftLayout.addLayout(paramsLayout)
        leftLayout.addLayout(btnLayout)
        leftLayout.addWidget(QLabel('IPC Log:'))
        leftLayout.addWidget(self.log)

        # Right area: merkle tree visualization and history
        rightLayout = QVBoxLayout()
        rightLayout.addWidget(QLabel('Merkle Tree View:'))
        self.scene = QGraphicsScene()
        self.treeView = QGraphicsView(self.scene)
        self.treeView.setMinimumWidth(380)
        self.treeView.setMinimumHeight(380)
        rightLayout.addWidget(self.treeView)
        # Merkle root display
        rightLayout.addWidget(QLabel('Latest Merkle Root:'))
        self.merkleRootLabel = QLabel('(none)')
        rightLayout.addWidget(self.merkleRootLabel)

        # SR/C metrics from last AI decision
        rightLayout.addWidget(QLabel('Last SR / C (AI metrics):'))
        self.metricsLabel = QLabel('(no decisions yet)')
        rightLayout.addWidget(self.metricsLabel)

        # Global system health panel
        rightLayout.addWidget(QLabel('Global System Health:'))
        self.healthStatusLabel = QLabel('Crypto: (unknown)')
        self.fpModeLabel = QLabel('FP Mode: -')
        self.fpConfidenceLabel = QLabel('FP Confidence: -')
        self.aiModeStatusLabel = QLabel('AI Mode: -')
        self.merkleStateLabel = QLabel('Merkle: -')
        rightLayout.addWidget(self.healthStatusLabel)
        rightLayout.addWidget(self.fpModeLabel)
        rightLayout.addWidget(self.fpConfidenceLabel)
        rightLayout.addWidget(self.aiModeStatusLabel)
        rightLayout.addWidget(self.merkleStateLabel)

        rightLayout.addWidget(QLabel('Leaf Actions (last AI decide):'))
        self.historyList = QListWidget()
        rightLayout.addWidget(self.historyList)

        rightLayout.addWidget(QLabel('Crypto Operations (recent):'))
        self.cryptoOpsList = QListWidget()
        rightLayout.addWidget(self.cryptoOpsList)

        mainLayout = QHBoxLayout()
        mainLayout.addLayout(leftLayout, 2)
        mainLayout.addLayout(rightLayout, 1)

        self.setLayout(mainLayout)

        # Connect
        self.startBtn.clicked.connect(self.on_start)
        self.stopBtn.clicked.connect(self.on_stop)
        self.pythonBrowseBtn.clicked.connect(self.on_browse_python)
        self.healthBtn.clicked.connect(self.on_health)
        self.decideBtn.clicked.connect(self.on_decide)
        self.rotateBtn.clicked.connect(self.on_rotate)
        self.historyBtn.clicked.connect(self.on_history)
        self.refreshHealthBtn.clicked.connect(self.on_refresh_health)
        self.refreshCryptoBtn.clicked.connect(self.on_refresh_crypto)
        self.autoApplyCheck.stateChanged.connect(lambda _: None)
        self.generateBtn.clicked.connect(self.on_generate)

    @Slot()
    def on_start(self):
        if self.ipc:
            QMessageBox.information(self, 'Info', 'IPC already started')
            return
        cmd = self.javaCmdInput.text().strip()
        try:
            self.ipc = IPCWrapper(cmd)
            self.ipc.received.connect(self.on_received)
            # If user provided a python path, export it so Java IPC can use it
            user_py = self.pythonPathInput.text().strip() if hasattr(self, 'pythonPathInput') else ''
            if user_py:
                try:
                    os.environ['SQUID_PYTHON'] = user_py
                except Exception:
                    pass

            self.ipc.start()
            effective = getattr(self.ipc, 'effective_cmd', cmd)
            choice = getattr(self.ipc, 'choice_message', None)
            if choice:
                self.log.append(choice)
            self.log.append('Started Java IPC: ' + effective)
        except Exception as e:
            QMessageBox.critical(self, 'Error', f'Failed to start Java IPC:\n{e}')
            self.ipc = None

    @Slot()
    def on_stop(self):
        if self.ipc:
            self.ipc.stop()
            self.ipc = None
            self.log.append('Stopped Java IPC')

    @Slot()
    def on_health(self):
        if not self.ipc:
            QMessageBox.warning(self, 'Warning', 'Start Java IPC first')
            return
        self.ipc.send({ 'cmd': 'health' })
        self.log.append('> health')

    @Slot()
    def on_decide(self):
        if not self.ipc:
            QMessageBox.warning(self, 'Warning', 'Start Java IPC first')
            return
        # Prepare a small sample payload
        payload = {
            'params': {'b': int(self.bSpin.value()), 'm': int(self.mSpin.value()), 't': int(self.tSpin.value())},
            'features': [
                {
                    'depth': 3,
                    'index': 0,
                    'index_hash': 0,
                    'local_entropy': 7.8,
                    'timestamp': int(time.time() * 1000),
                    'global_L': 64,
                    'global_b': 4,
                    'global_m': 3,
                    'global_t': 128,
                    'last_access_count': 0,
                    'leaf_hist_score': 0.5
                }
            ]
        }
        # include auto_apply flag at root so IPCMain can read it directly
        msg = {'cmd': 'decide', 'payload': payload}
        if self.autoApplyCheck.isChecked():
            msg['auto_apply'] = True
            self.log.append('> decide (auto-apply)')
        else:
            self.log.append('> decide')

        self.ipc.send(msg)

        # optimistic draw: draw a tree skeleton based on params
        try:
            total_leaves = int(self.bSpin.value()) ** int(self.mSpin.value())
            self.draw_tree(total_leaves)
        except Exception:
            pass

    @Slot()
    def on_rotate(self):
        if not self.ipc:
            QMessageBox.warning(self, 'Warning', 'Start Java IPC first')
            return
        # Ask Java to rotate indices 0..2 as sample
        self.ipc.send({'cmd': 'rotate_indices', 'payload': {'indices': [0,1,2], 'reason': 'qt-ui-request'}})
        self.log.append('> rotate_indices [0,1,2]')

    @Slot()
    def on_generate(self):
        if not self.ipc:
            QMessageBox.warning(self, 'Warning', 'Start Java IPC first')
            return

        seed = self.seedInput.text().strip()
        if not seed:
            QMessageBox.warning(self, 'Warning', 'Enter a seed string to derive PQC key / leaves')
            return

        payload = {
            'seed': seed,
            'params': {'b': int(self.bSpin.value()), 'm': int(self.mSpin.value()), 't': int(self.tSpin.value())}
        }
        self.ipc.send({'cmd': 'generate', 'payload': payload})
        self.log.append('> generate (seed provided)')

    def _fetch_json(self, url: str):
        try:
            req = urllib.request.Request(url, headers={'Accept': 'application/json'})
            with urllib.request.urlopen(req, timeout=3) as resp:
                data = resp.read().decode('utf-8')
            return json.loads(data)
        except Exception as e:
            try:
                self.log.append(f'REST error: {e}')
            except Exception:
                pass
            return None

    @Slot()
    def on_history(self):
        if not self.ipc:
            QMessageBox.warning(self, 'Warning', 'Start Java IPC first')
            return
        self.ipc.send({'cmd': 'history'})
        self.log.append('> history')

    @Slot()
    def on_refresh_health(self):
        data = self._fetch_json('http://localhost:8080/api/v1/system/health')
        if not data:
            return
        try:
            self.healthStatusLabel.setText(f"Crypto: {data.get('cryptography_status', 'UNKNOWN')}")
            self.fpModeLabel.setText(f"FP Mode: {data.get('fingerprint_mode', '-')}")
            self.fpConfidenceLabel.setText(f"FP Confidence: {data.get('fingerprint_confidence', '-')}")
            self.aiModeStatusLabel.setText(f"AI Mode: {data.get('ai_mode', '-')}")
            self.merkleStateLabel.setText(f"Merkle: {data.get('merkle_state', '-')}")
        except Exception:
            pass

    @Slot()
    def on_refresh_crypto(self):
        data = self._fetch_json('http://localhost:8080/api/v1/crypto/operations')
        if not data or not isinstance(data, list):
            return
        try:
            self.cryptoOpsList.clear()
            for entry in data[-50:]:  # show last 50 operations
                t = entry.get('type', '?')
                ok = entry.get('success', False)
                ts = entry.get('timestamp', '')
                meta = entry.get('metadata', {}) or {}
                root = meta.get('merkle_root', '')
                self.cryptoOpsList.addItem(f"{ts}  {t}  {'OK' if ok else 'FAIL'}  root={root}")
        except Exception:
            pass

    @Slot(str)
    def on_received(self, text: str):
        try:
            # pretty print JSON responses
            parsed = json.loads(text)
            pretty = json.dumps(parsed, indent=2, ensure_ascii=False)
            self.log.append(pretty)
            # react to applied_rotation or rotatedIndices keys to update visualization
            rotated = None
            if isinstance(parsed, dict):
                # If this looks like an IPC decide response, extract AI metrics/actions.
                # ipc_daemon.py returns { ok: bool, result: { sr, c, actions, rotation_plan } }.
                try:
                    result = None
                    if parsed.get('ok') and isinstance(parsed.get('result'), dict):
                        result = parsed['result']
                    elif all(k in parsed for k in ('sr', 'c', 'actions')):
                        result = parsed
                    if result:
                        sr_val = result.get('sr')
                        c_val = result.get('c')
                        if sr_val is not None and c_val is not None:
                            self.metricsLabel.setText(f"SR={sr_val:.4f}  C={c_val:.4f}")
                        actions = result.get('actions')
                        if isinstance(actions, list):
                            # store per-leaf actions and update list widget
                            self._leaf_actions = {i: a for i, a in enumerate(actions)}
                            self.historyList.clear()
                            for i, a in self._leaf_actions.items():
                                self.historyList.addItem(f"Leaf {i}: {a}")
                            # redraw tree with action-based colors if we know leaf count
                            try:
                                total_leaves = len(actions)
                                self.draw_tree(total_leaves)
                            except Exception:
                                pass
                except Exception:
                    pass
                if 'applied_rotation' in parsed and isinstance(parsed['applied_rotation'], dict):
                    ar = parsed['applied_rotation']
                    if 'rotatedIndices' in ar:
                        rotated = ar['rotatedIndices']
                elif 'rotatedIndices' in parsed:
                    rotated = parsed['rotatedIndices']

                # handle generate/addLeaves responses: dynamicStats.total_nodes -> draw tree
                if 'dynamicStats' in parsed and isinstance(parsed['dynamicStats'], dict):
                    try:
                        total_nodes = parsed['dynamicStats'].get('total_nodes')
                        if total_nodes is not None:
                            # draw tree with total_nodes (clamped)
                            cnt = int(total_nodes)
                            cnt = max(1, min(cnt, 256))
                            self.draw_tree(cnt)
                    except Exception:
                        pass

                # store leaf metadata if present in response (e.g. 'leaves' list)
                if 'leaves' in parsed and isinstance(parsed['leaves'], list):
                    try:
                        self._leaf_meta = getattr(self, '_leaf_meta', {})
                        for i, v in enumerate(parsed['leaves']):
                            # store raw value; if dict, keep as-is
                            self._leaf_meta[i] = v
                    except Exception:
                        pass

                # update merkle root label if present
                if 'newRoot' in parsed:
                    try:
                        self.merkleRootLabel.setText(str(parsed.get('newRoot')))
                    except Exception:
                        pass
            

            if rotated:
                try:
                    idxs = set(int(x) for x in rotated)
                    self.highlight_leaves(idxs)
                except Exception:
                    pass
        except Exception:
            # raw text
            self.log.append(text)

    @Slot()
    def on_browse_python(self):
        fname, _ = QFileDialog.getOpenFileName(self, 'Select Python executable', str(Path.home()), 'Executables (*.exe);;All Files (*)')
        if fname:
            self.pythonPathInput.setText(fname)

    def draw_tree(self, total_leaves: int):
        # Simple layered tree drawing: place leaves at bottom and parents above
        self.scene.clear()
        max_width = 340
        leaf_count = max(1, min(total_leaves, 256))
        cols = leaf_count
        # spacing with center alignment and min spacing
        min_spacing = 18
        spacing_x = max(min_spacing, max_width / max(1, cols))
        y_leaf = 320
        radius = 12
        self._leaf_items = []
        # center the line of leaves
        total_width = spacing_x * cols
        start_x = 10 + max(0, (max_width - total_width) / 2)
        for i in range(leaf_count):
            x = start_x + i * spacing_x
            # Base color depends on last known AI action for this leaf, if any
            base_brush = QBrush(QColor('lightgray'))
            if hasattr(self, '_leaf_actions') and i in self._leaf_actions:
                act = self._leaf_actions[i]
                if act == 'DECOY':
                    base_brush = QBrush(QColor('orange'))
                elif act == 'MUTATE':
                    base_brush = QBrush(QColor('purple'))
                elif act == 'REASSIGN':
                    base_brush = QBrush(QColor('red'))
                else:  # VALID or unknown
                    base_brush = QBrush(QColor('lightgreen'))
            ell = self.scene.addEllipse(QRectF(x, y_leaf, radius*2, radius*2), QPen(QColor('black')), base_brush)
            txt = self.scene.addText(str(i))
            txt.setPos(x, y_leaf)
            # add tooltip showing index and any stored metadata
            meta = None
            if hasattr(self, '_leaf_meta') and i in self._leaf_meta:
                try:
                    meta = json.dumps(self._leaf_meta[i], ensure_ascii=False)
                except Exception:
                    meta = str(self._leaf_meta[i])
            tooltip = f"Index: {i}"
            # Include last AI action if available
            if hasattr(self, '_leaf_actions') and i in self._leaf_actions:
                tooltip += f"\nAction: {self._leaf_actions[i]}"
            if meta:
                tooltip += "\n" + meta
            ell.setToolTip(tooltip)
            self._leaf_items.append(ell)

    def highlight_leaves(self, indices:set):
        if not hasattr(self, '_leaf_items'):
            return
        for i, item in enumerate(self._leaf_items):
            if i in indices:
                item.setBrush(QBrush(QColor('orange')))
            else:
                item.setBrush(QBrush(QColor('lightgray')))


def main():
    app = QApplication(sys.argv)
    w = MainWindow()
    w.show()
    sys.exit(app.exec())


if __name__ == '__main__':
    main()
