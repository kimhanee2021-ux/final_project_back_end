"""Load backend connection settings only when explicitly applying an import."""
import os
from pathlib import Path
from urllib.parse import urlsplit, unquote

ROOT = Path(__file__).resolve().parent


def connect():
    import pymysql
    env = {}
    path = Path(os.environ.get("RANK_BACKEND_ENV", str(ROOT.parents[1] / ".env")))
    if path.exists():
        for line in path.read_text(encoding="utf-8-sig").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                env[key.strip()] = value.strip().strip('"').strip("'")
    for key in ("DB_URL", "DB_USERNAME", "DB_PASSWORD"):
        if key in os.environ:
            env[key] = os.environ[key]
    if any(not env.get(key) for key in ("DB_URL", "DB_USERNAME", "DB_PASSWORD")):
        raise ValueError("Set DB_URL, DB_USERNAME, DB_PASSWORD in backend .env or environment")
    address = urlsplit(env["DB_URL"].removeprefix("jdbc:"))
    if address.scheme != "mysql" or not address.hostname or not address.path.strip("/"):
        raise ValueError("Expected MySQL JDBC URL with host and database")
    return pymysql.connect(host=address.hostname, port=address.port or 3306,
                           user=env["DB_USERNAME"], password=env["DB_PASSWORD"],
                           database=unquote(address.path.lstrip("/")), charset="utf8mb4",
                           autocommit=False, connect_timeout=15, read_timeout=60, write_timeout=60)
