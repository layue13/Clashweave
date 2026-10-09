"""Real S2b/S5b server + client; FIFO per-direction TCP delay, fresh worlds only."""
import argparse
import asyncio
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[3]


def wait_due(due):
    while (remaining := due - time.perf_counter()) > 0:
        time.sleep(remaining)


class Proxy:
    def __init__(self, rtt):
        self.delay = rtt / 2000
        self.samples = []
        self.tasks = set()

    async def pipe(self, reader, writer):
        pending = asyncio.Queue()

        async def collect():
            while data := await reader.read(65536):
                await pending.put((time.perf_counter(), data))
            await pending.put(None)

        async def transmit():
            while (item := await pending.get()) is not None:
                start, data = item
                due = start + self.delay
                # A worker uses Windows' waitable timer, bypassing asyncio's coarse clock.
                await asyncio.to_thread(wait_due, due)
                writer.write(data)
                await writer.drain()
                self.samples.append((time.perf_counter() - start) * 1000)
            writer.close()
        try:
            await asyncio.gather(collect(), transmit())
        except (OSError, ConnectionError):
            writer.close()

    async def connection(self, reader, writer):
        task = asyncio.current_task()
        self.tasks.add(task)
        try:
            remote_reader, remote_writer = await asyncio.open_connection("127.0.0.1", 25575)
            await asyncio.gather(self.pipe(reader, remote_writer), self.pipe(remote_reader, writer))
        finally:
            self.tasks.discard(task)


def text(path):
    return path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""


def start(kind, work, log, rtt, state_only=False):
    launch = json.loads((ROOT / f"build/probes2/movementstate/{kind}.json").read_text())
    work.mkdir()
    (work / "mods").mkdir()
    (work / "config").mkdir()
    shutil.copy2(ROOT / "build/probes2/movementstate/movement-state-experiment.jar", work / "mods")
    args = [value for value in launch["jvmArgs"] if not value.startswith(("-Xms", "-Xmx"))]
    args += ["-Xms256M", "-Xmx1G"]
    if state_only:
        args.append("-Dcw.ms.stateOnly=true")
    if kind == "runServer":
        args += ["-verbose:class"]
        (work / "eula.txt").write_text("eula=true\n")
        (work / "server.properties").write_text(
            "server-ip=127.0.0.1\nserver-port=25575\nonline-mode=false\n"
            "level-type=FLAT\ngenerate-structures=false\nspawn-protection=0\n"
            "spawn-monsters=false\nspawn-animals=false\nview-distance=4\n"
            "difficulty=1\ngamemode=0\nmax-players=2\n")
        extra = ["nogui"]
    else:
        (work / "options.txt").write_text(
            "soundCategory_master:0.0\nsoundCategory_music:0.0\nrenderDistance:4\nmaxFps:60\n")
        extra = ["--username", "MoveState", "--width", "960", "--height", "540", "--gameDir", str(work)]
    command = [launch["java"], *args, "-cp", launch["classpath"], launch["main"], *launch["args"], *extra]
    with log.open("wb") as stream:
        process = subprocess.Popen(command, cwd=work, stdin=subprocess.PIPE, stdout=stream,
                                   stderr=subprocess.STDOUT, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    return process, command


async def run(args, rtt):
    label = f"{args.label}-rtt{rtt}"
    work = ROOT / "run/probes2" / label
    out = ROOT / "docs/probes/round2/evidence" / label
    if work.exists() or out.exists():
        raise RuntimeError("Existing label, nothing overwritten: " + label)
    work.mkdir(parents=True)
    logs = [work / f"{name}.log" for name in ("server", "client")]
    processes = []
    listener = None
    proxy = Proxy(rtt)
    try:
        server, server_command = start("runServer", work / "server", logs[0], rtt, args.state_only)
        processes.append(server)
        deadline = time.monotonic() + 150
        while "Done (" not in text(logs[0]):
            if server.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError("Server startup failed: " + str(logs[0]))
            await asyncio.sleep(0.5)
        listener = await asyncio.start_server(proxy.connection, "127.0.0.1", 25576)
        client, client_command = start("runClient", work / "client", logs[1], rtt)
        processes.append(client)
        deadline = time.monotonic() + 350
        next_progress = time.monotonic() + 25
        while "CLIENT_COMPLETE" not in text(logs[1]):
            if client.poll() is not None or server.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError("Scenario incomplete: " + str(work))
            if time.monotonic() > next_progress:
                print(f"rtt={rtt} completed_moves={text(logs[0]).count('S2B_END')} state_cases={text(logs[0]).count('S5B_CASE')} proxy_chunks={len(proxy.samples)}", flush=True)
                next_progress += 25
            await asyncio.sleep(0.5)
        server.stdin.write(b"stop\n")
        server.stdin.flush()
        for process in processes:
            await asyncio.to_thread(process.wait, timeout=25)
        out.mkdir(parents=True)
        contents = [text(path) for path in logs]
        for name, content in zip(("server", "client"), contents):
            (out / f"{name}-measurements.log").write_text(
                "\n".join(line for line in content.splitlines() if "CWMS " in line) + "\n", encoding="utf-8")
            (out / f"{name}-errors.log").write_text(
                "\n".join(line for line in content.splitlines() if any(tag in line for tag in ("WARN", "ERROR", "Exception", "Caused by"))) + "\n", encoding="utf-8")
        actual_loads = [line for line in contents[0].splitlines() if line.startswith("[Loaded ")
                        and any(tag in line for tag in ("net.minecraft.client.", ".ms.client.", "org.lwjgl.opengl."))]
        results = re.findall(r"S2B_CLIENT trial=(\d+) mode=(\d+) S08=(\d+)", contents[0])
        s5 = re.findall(r"S5B_APPLIED.*elapsedMs=([\d.]+)", contents[0])
        sources = sorted((ROOT / "tools/probes2/movementstate").rglob("*.java"))
        jar = ROOT / "build/probes2/movementstate/movement-state-experiment.jar"
        summary = {
            "state_only": args.state_only,
            "rtt_ms": rtt, "server_exit": server.returncode, "client_exit": client.returncode,
            "client_class_loads_on_server": len(actual_loads),
            "legal_trials": sum(int(i) < 40 for i, mode, s08 in results),
            "legal_s08": sum(int(s08) for i, mode, s08 in results if int(i) < 40),
            "malicious_trials": [{"trial": int(i), "mode": int(mode), "s08": int(s08)} for i, mode, s08 in results if int(i) >= 40],
            "snapshot_apply_ms": [float(value) for value in s5],
            "proxy_oneway_mean_ms": sum(proxy.samples) / len(proxy.samples),
            "proxy_oneway_min_ms": min(proxy.samples), "proxy_oneway_max_ms": max(proxy.samples),
            "jar_sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
            "source_sha256": {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
            "server_command": server_command, "client_command": client_command,
        }
        (out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({k: v for k, v in summary.items() if k not in ("server_command", "client_command", "source_sha256")}), flush=True)
    finally:
        if listener:
            listener.close()
            await listener.wait_closed()
        for task in list(proxy.tasks):
            task.cancel()
        if proxy.tasks:
            await asyncio.gather(*proxy.tasks, return_exceptions=True)
        for process in processes:
            if process.poll() is None:
                process.terminate()
                await asyncio.to_thread(process.wait, timeout=15)
            if process.stdin:
                process.stdin.close()


async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--accept-eula", required=True, action="store_true")
    parser.add_argument("--label", required=True)
    parser.add_argument("--rtts", default="0,100")
    parser.add_argument("--state-only", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"[\w-]+", args.label):
        parser.error("Invalid label")
    for value in args.rtts.split(","):
        await run(args, int(value))


if __name__ == "__main__":
    asyncio.run(main())
