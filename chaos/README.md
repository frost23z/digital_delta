# Chaos Simulation Server

To start the chaos simulation server:

1. Install the required dependency:

```bash
pip install flask
```

2. Run the server:

```bash
python chaos_server.py
```

Your application must continuously poll the following endpoint:

```
http://127.0.0.1:5000/api/network/status
```

to detect road flooding events and dynamically recompute routes in real time.
