"""Start the same final dev jar with probes disabled; verify command/side isolation."""
import argparse
import json
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument("--accept-eula", action="store_true", required=True)
args = parser.parse_args()
data = json.loads((ROOT / "build/probes/runServer.json").read_text())
folder = ROOT / "run/probes/disabled-check"
folder.mkdir(parents=True, exist_ok=False)
(folder / "eula.txt").write_text("eula=true\n")
(folder / "server.properties").write_text("server-ip=127.0.0.1\nserver-port=25565\nonline-mode=false\nlevel-type=FLAT\nlevel-name=probe-world\nview-distance=4\n")
log = folder / "server.log"
jvm = [a for a in data["jvmArgs"] if not a.startswith(("-Dclashweave.", "-Xms", "-Xmx"))]
with log.open("wb") as out:
    process = subprocess.Popen([data["java"], *jvm, "-Xms256M", "-Xmx1G", "-verbose:class", "-cp", data["classpath"], data["main"], "nogui"], cwd=folder,
                               stdin=subprocess.PIPE, stdout=out, stderr=subprocess.STDOUT,
                               creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    try:
        deadline = time.monotonic() + 120
        while "Done (" not in log.read_text(errors="replace"):
            if process.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError("disabled server startup failed")
            time.sleep(0.5)
        process.stdin.write(b"cwprobe s1\n")
        process.stdin.flush()
        time.sleep(1)
        process.stdin.write(b"stop\n")
        process.stdin.flush()
        process.wait(timeout=30)
    finally:
        if process.poll() is None:
            process.terminate()
text = log.read_text(errors="replace")
clients = [l for l in text.splitlines() if l.startswith("[Loaded ") and any(s in l for s in ("net.minecraft.client.", "com.layue13.clashweave.probe.client.", "org.lwjgl.opengl."))]
result = {"exit": process.returncode, "probe_log_lines": text.count("CWPROBE"), "client_class_loads": len(clients),
          "unknown_probe_command": "Unknown command" in text, "client_lines": clients}
assert result["exit"] == 0 and result["probe_log_lines"] == 0 and not clients and result["unknown_probe_command"], result
evidence = ROOT / "docs/probes/evidence"
evidence.mkdir(parents=True, exist_ok=True)
(evidence / "disabled.json").write_text(json.dumps(result, indent=2)+"\n")
print(json.dumps(result))
