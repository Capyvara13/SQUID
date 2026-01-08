SQUID Qt Dashboard
===================

This is a PySide6-based standalone dashboard for the SQUID project.
It starts the Java IPC process and communicates with it via stdin/stdout
JSON lines (no network sockets required).

Quick start (development):

1. Install deps:

```powershell
pip install -r dashboard-qt\requirements.txt
```

1. Start the Qt dashboard:

```powershell
python dashboard-qt\main.py
```

1. In the dashboard UI, press `Start Java IPC` (the default command uses Maven to
   launch the IPCMain). Ensure `mvn` + JDK are available on PATH.

Notes:

- The Java IPC is started via a shell command. You can change the command in
  the UI if your environment requires a different invocation.
- The dashboard uses JSON-over-stdin protocol to interact with Java. Java
  orchestrates Python as needed.

Packaging to EXE (Windows)

--------------------------
Use PyInstaller to create a single-file EXE. Example:

```powershell
pip install pyinstaller; pyinstaller --onefile --name squid-dashboard main.py
```

This will produce `dist\squid-dashboard.exe`.

Security & Offline

--------------------------

This design avoids network sockets and runs entirely locally: the Qt UI starts
processes and communicates via pipes. Ensure you run the dashboard in a
trusted environment.
