"""Launch a fresh, isolated Forge server. Never use or modify a user's world."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--accept-eula", action="store_true", required=True)
    parser.add_argument("--label", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[a-zA-Z0-9_-]+", args.label):
        parser.error("label must contain only letters, digits, - or _")
    root = Path(__file__).resolve().parents[3]
    work = root / "run" / "probes2" / args.label
    evidence = root / "docs" / "probes" / "round3" / "evidence" / args.label
    if work.exists() or evidence.exists():
        parser.error("label already exists; choose a new one, nothing is overwritten")
    launch = json.loads((root / "build/probes2/s1d/server-launch.json").read_text())
    jar = root / "build/probes2/s1d/s1d-experiment.jar"
    work.mkdir(parents=True)
    (work / "mods").mkdir()
    shutil.copy2(jar, work / "mods" / jar.name)
    (work / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (work / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=25571\nonline-mode=false\n"
        "level-type=FLAT\ngenerate-structures=false\nspawn-protection=0\n"
        "max-players=2\n", encoding="utf-8"
    )
    command = [launch["java"], *launch["jvmArgs"], "-verbose:class", "-cp",
               launch["classpath"], launch["main"], *launch["args"], "nogui"]
    log = work / "server.log"
    with log.open("w", encoding="utf-8") as stream:
        process = subprocess.Popen(command, cwd=work, stdin=subprocess.PIPE,
                                   stdout=stream, stderr=subprocess.STDOUT)
        try:
            code = process.wait(timeout=180)
        except subprocess.TimeoutExpired:
            process.stdin.write(b"stop\n")
            process.stdin.flush()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.terminate()
                process.wait(timeout=20)
            raise RuntimeError(f"experiment timed out; inspect {log}")
        finally:
            process.stdin.close()
    text = log.read_text(encoding="utf-8", errors="replace")
    measurements = [line for line in text.splitlines() if "CW3 " in line]
    loads = [line for line in text.splitlines()
             if line.startswith("[Loaded ") and
             any(name in line for name in ("net.minecraft.client.", "org.lwjgl.opengl."))]
    valid = code == 0 and any("S1D_RESULT" in line for line in measurements)
    if not valid:
        raise RuntimeError(f"experiment did not finish: exit={code}; inspect {log}")
    evidence.mkdir(parents=True)
    (evidence / "measurements.log").write_text("\n".join(measurements) + "\n", encoding="utf-8")
    # Preserve actual startup/shutdown and warnings, without a huge class-load trace.
    (evidence / "server.log").write_text(
        "\n".join(line for line in text.splitlines() if not line.startswith("[Loaded ")) + "\n",
        encoding="utf-8"
    )
    sources = [root / "build/rfg/minecraft-src/java/net/minecraft/entity/EntityLivingBase.java",
               root / "build/rfg/minecraft-src/java/net/minecraft/entity/player/EntityPlayer.java",
               root / "build/rfg/minecraft-src/java/net/minecraft/entity/player/EntityPlayerMP.java",
               root / "build/rfg/minecraft-src/java/cpw/mods/fml/common/eventhandler/ListenerList.java",
               *sorted((root / "tools/probes2/s1d/java").rglob("*.java"))]
    summary = {
        "server_exit": code, "client_or_opengl_class_loads": len(loads),
        "experiment_jar_sha256": sha256(jar),
        "production_dev_jar_sha256": sha256(root / "build/libs/clashweave-0.1.0-dev-dev.jar"),
        "source_sha256": {str(path.relative_to(root)): sha256(path) for path in sources},
        "command": command, "working_directory": str(work),
        "verdict": "FAIL" if any("verdict=FAIL" in line for line in measurements) else "PASS",
        "remaining_probes": "independent probes run separately",
    }
    (evidence / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print("\n".join(measurements))
    print(f"server_exit={code} client_or_opengl_class_loads={len(loads)}")
    print(f"evidence={evidence}")


if __name__ == "__main__":
    main()
