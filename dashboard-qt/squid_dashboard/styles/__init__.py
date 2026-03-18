"""Estilos e temas do dashboard."""

STYLE_SHEET = """
/* Main Window */
QMainWindow {
    background-color: #0f172a;
    color: #e2e8f0;
}

/* Group Box */
QGroupBox {
    background-color: #1e293b;
    border: 1px solid #334155;
    border-radius: 6px;
    margin-top: 12px;
    padding-top: 12px;
    font-weight: bold;
    color: #f8fafc;
}

QGroupBox::title {
    subcontrol-origin: margin;
    left: 10px;
    padding: 0 5px;
    color: #94a3b8;
}

/* Buttons */
QPushButton {
    background-color: #334155;
    color: #f1f5f9;
    border: 1px solid #475569;
    border-radius: 4px;
    padding: 8px 16px;
    font-weight: 500;
}

QPushButton:hover {
    background-color: #475569;
    border-color: #64748b;
}

QPushButton:pressed {
    background-color: #1e293b;
}

QPushButton:disabled {
    background-color: #1e293b;
    color: #64748b;
    border-color: #334155;
}

/* Start Button (Green) */
QPushButton#StartBtn {
    background-color: #059669;
    border-color: #047857;
}

QPushButton#StartBtn:hover {
    background-color: #047857;
}

/* Stop Button (Red) */
QPushButton#StopBtn {
    background-color: #dc2626;
    border-color: #b91c1c;
}

QPushButton#StopBtn:hover {
    background-color: #b91c1c;
}

/* Critical Button */
QPushButton#CriticalBtn {
    background-color: #7f1d1d;
    border-color: #991b1b;
    color: #fca5a5;
}

QPushButton#CriticalBtn:hover {
    background-color: #991b1b;
}

/* Action Button */
QPushButton#ActionBtn {
    background-color: #2563eb;
    border-color: #1d4ed8;
    padding: 4px 12px;
    font-size: 11px;
}

QPushButton#ActionBtn:hover {
    background-color: #1d4ed8;
}

/* Labels */
QLabel {
    color: #e2e8f0;
}

QLabel#MetricTitle {
    color: #94a3b8;
    font-size: 10pt;
}

QLabel#MetricLabel {
    color: #f8fafc;
    font-size: 14pt;
    font-weight: bold;
}

/* Status Labels */
QLabel#StatusOnline {
    color: #22c55e;
    font-weight: bold;
}

QLabel#StatusOffline {
    color: #ef4444;
    font-weight: bold;
}

QLabel#StatusError {
    color: #f59e0b;
    font-weight: bold;
}

QLabel#StatusStarting {
    color: #38bdf8;
    font-weight: bold;
}

/* Line Edit */
QLineEdit {
    background-color: #0f172a;
    color: #f8fafc;
    border: 1px solid #334155;
    border-radius: 4px;
    padding: 6px;
}

QLineEdit:focus {
    border-color: #38bdf8;
}

/* Text Edit */
QTextEdit {
    background-color: #0f172a;
    color: #f8fafc;
    border: 1px solid #334155;
    border-radius: 4px;
    padding: 6px;
}

QTextEdit#LogConsole {
    background-color: #020617;
    color: #94a3b8;
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 9pt;
}

/* Combo Box */
QComboBox {
    background-color: #0f172a;
    color: #f8fafc;
    border: 1px solid #334155;
    border-radius: 4px;
    padding: 6px;
}

QComboBox:hover {
    border-color: #475569;
}

QComboBox::drop-down {
    border: none;
    width: 20px;
}

QComboBox QAbstractItemView {
    background-color: #1e293b;
    color: #f8fafc;
    selection-background-color: #334155;
}

/* Spin Box */
QSpinBox {
    background-color: #0f172a;
    color: #f8fafc;
    border: 1px solid #334155;
    border-radius: 4px;
    padding: 6px;
}

/* Table Widget */
QTableWidget {
    background-color: #0f172a;
    color: #f8fafc;
    border: 1px solid #334155;
    border-radius: 4px;
    gridline-color: #334155;
}

QTableWidget::item {
    padding: 6px;
}

QTableWidget::item:selected {
    background-color: #334155;
    color: #f8fafc;
}

QHeaderView::section {
    background-color: #1e293b;
    color: #94a3b8;
    padding: 8px;
    border: 1px solid #334155;
    font-weight: bold;
}

/* Tree Widget */
QTreeWidget {
    background-color: #0f172a;
    color: #f8fafc;
    border: 1px solid #334155;
    border-radius: 4px;
}

QTreeWidget::item {
    padding: 4px;
}

QTreeWidget::item:selected {
    background-color: #334155;
}

/* Tab Widget */
QTabWidget::pane {
    border: 1px solid #334155;
    border-radius: 4px;
    background-color: #0f172a;
}

QTabBar::tab {
    background-color: #1e293b;
    color: #94a3b8;
    padding: 10px 20px;
    border-top-left-radius: 4px;
    border-top-right-radius: 4px;
    margin-right: 2px;
}

QTabBar::tab:selected {
    background-color: #334155;
    color: #f8fafc;
}

QTabBar::tab:hover {
    background-color: #475569;
    color: #f8fafc;
}

/* Progress Bar */
QProgressBar {
    border: 1px solid #334155;
    border-radius: 4px;
    text-align: center;
    color: #f8fafc;
    background-color: #0f172a;
}

QProgressBar::chunk {
    background-color: #38bdf8;
    border-radius: 3px;
}

/* Menu */
QMenu {
    background-color: #1e293b;
    color: #f8fafc;
    border: 1px solid #334155;
    padding: 5px;
}

QMenu::item {
    padding: 6px 20px;
    border-radius: 3px;
}

QMenu::item:selected {
    background-color: #334155;
}

QMenu::separator {
    height: 1px;
    background-color: #334155;
    margin: 5px 0;
}

/* Dialog */
QDialog {
    background-color: #0f172a;
}

/* Splitter */
QSplitter::handle {
    background-color: #334155;
}

QSplitter::handle:horizontal {
    width: 2px;
}

QSplitter::handle:vertical {
    height: 2px;
}

/* Scroll Bar */
QScrollBar:vertical {
    background-color: #0f172a;
    width: 12px;
    border-radius: 6px;
}

QScrollBar::handle:vertical {
    background-color: #475569;
    border-radius: 6px;
    min-height: 20px;
}

QScrollBar::handle:vertical:hover {
    background-color: #64748b;
}

QScrollBar::add-line:vertical,
QScrollBar::sub-line:vertical {
    height: 0px;
}

QScrollBar:horizontal {
    background-color: #0f172a;
    height: 12px;
    border-radius: 6px;
}

QScrollBar::handle:horizontal {
    background-color: #475569;
    border-radius: 6px;
    min-width: 20px;
}

QScrollBar::handle:horizontal:hover {
    background-color: #64748b;
}

QScrollBar::add-line:horizontal,
QScrollBar::sub-line:horizontal {
    width: 0px;
}

/* Tool Tip */
QToolTip {
    background-color: #1e293b;
    color: #f8fafc;
    border: 1px solid #334155;
    padding: 8px;
    border-radius: 4px;
}
"""
