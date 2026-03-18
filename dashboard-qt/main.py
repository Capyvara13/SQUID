"""Entry point do SQUID Dashboard.

Uso:
    python main.py

Requisitos:
    pip install -r requirements.txt
"""

import sys
from PySide6.QtWidgets import QApplication
from PySide6.QtGui import QPalette, QColor
from PySide6.QtCore import Qt

from squid_dashboard.app import SquidDashboard
from squid_dashboard.styles import STYLE_SHEET


def setup_palette(app: QApplication):
    """Configura paleta de cores escura."""
    palette = QPalette()
    palette.setColor(QPalette.Window, QColor(15, 23, 42))        # #0f172a
    palette.setColor(QPalette.WindowText, QColor(226, 232, 240))   # #e2e8f0
    palette.setColor(QPalette.Base, QColor(2, 6, 23))             # #020617
    palette.setColor(QPalette.AlternateBase, QColor(30, 41, 59))   # #1e293b
    palette.setColor(QPalette.ToolTipBase, QColor(15, 23, 42))
    palette.setColor(QPalette.ToolTipText, QColor(226, 232, 240))
    palette.setColor(QPalette.Text, QColor(226, 232, 240))
    palette.setColor(QPalette.Button, QColor(30, 41, 59))        # #1e293b
    palette.setColor(QPalette.ButtonText, QColor(226, 232, 240))
    palette.setColor(QPalette.BrightText, QColor(248, 113, 113)) # #f87171
    palette.setColor(QPalette.Link, QColor(56, 189, 248))        # #38bdf8
    palette.setColor(QPalette.Highlight, QColor(14, 165, 233))   # #0ea5e9
    palette.setColor(QPalette.HighlightedText, QColor(2, 6, 23))
    app.setPalette(palette)


def main():
    """Função principal."""
    app = QApplication(sys.argv)
    app.setStyle('Fusion')
    app.setStyleSheet(STYLE_SHEET)
    setup_palette(app)
    
    window = SquidDashboard()
    window.show()
    
    sys.exit(app.exec())


if __name__ == '__main__':
    main()
