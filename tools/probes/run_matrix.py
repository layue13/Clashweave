"""Real Forge dedicated server + two clients, with a FIFO TCP latency proxy.

Requires probeLaunchFiles first. EULA acceptance is explicit (--accept-eula).
Only creates new run/probes folders; never edits a survival save or user's launch config.
"""
import argparse
import asyncio
import json
import random
import socket
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class DelayProxy:
    def __init__(self, listen, target, rtt, jitter, seed):
        self.listen, self.target = listen, target
        self.rtt, self.jitter = rtt, jitter
        self.rng = random.Random(seed)
        self.delays = []
        self.server = None

    async def pipe(self, reader, writer):
        pending = asyncio.Queue()

        async def read():
            previous = 0
            while chunk := await reader.read(65536):
                delay = max(0, self.rtt / 2 + self.rng.uniform(-self.jitter / 2, self.jitter / 2)) / 1000
                due = max(previous, time.monotonic() + delay)
                previous = due
                await pending.put((due, chunk, time.monotonic()))
            await pending.put(None)

        async def send():
            while (entry := await pending.get()) is not None:
                due, chunk, received = entry
                await asyncio.sleep(max(0, due - time.monotonic()))
                writer.write(chunk)
                await writer.drain()
                self.delays.append((time.monotonic() - received) * 1000)
            writer.close()

        try:
            await asyncio.gather(read(), send())
        except (ConnectionError, OSError):
            writer.close()

    async def connection(self, client_reader, client_writer):
        try:
            server_reader, server_writer = await asyncio.open_connection("127.0.0.1", self.target)
            await asyncio.gather(self.pipe(client_reader, server_writer), self.pipe(server_reader, client_writer))
        except OSError:
            client_writer.close()

    async def start(self):
        self.server = await asyncio.start_server(self.connection, "127.0.0.1", self.listen)

    async def close(self):
        self.server.close()
        await self.server.wait_closed()


def launch(kind, cwd, role, port, rtt, log_path, trace=False, trials=12, negative=False):
    data = json.loads((ROOT / f"build/probes/{kind}.json").read_text())
    cwd.mkdir(parents=True, exist_ok=False)
    (cwd / "config").mkdir()
    args = [a for a in data["jvmArgs"] if not a.startswith(("-Dclashweave.", "-Xms", "-Xmx"))]
    args += ["-Xms256M", "-Xmx1G", "-Dclashweave.probes=true", "-Dclashweave.probe.autoServer=true",
             f"-Dclashweave.probe.role={role}", f"-Dclashweave.probe.port={port}",
             f"-Dclashweave.probe.rtt={rtt}", f"-Dclashweave.probe.moveTrials={trials}",
             "-Dclashweave.probe.exit=true"]
    if negative:
        args.append("-Dclashweave.probe.negative=true")
    if trace:
        args.append("-verbose:class")
    extra = ["nogui"] if kind == "runServer" else ["--username", "ProbeA" if role == "attacker" else "ProbeB", "--width", "960", "--height", "540", "--gameDir", str(cwd)]
    if kind == "runServer":
        (cwd / "eula.txt").write_text("eula=true\n")
        (cwd / "server.properties").write_text(
            "server-ip=127.0.0.1\nserver-port=25565\nonline-mode=false\nlevel-type=FLAT\n"
            "level-name=probe-world\nspawn-protection=0\nspawn-monsters=false\nspawn-animals=false\n"
            "difficulty=1\ngamemode=0\nview-distance=4\nmax-players=2\n")
    else:
        # This host exposes the vanilla 1.7.10 OpenAL startup/reload race. No probe needs audio.
        (cwd / "options.txt").write_text("soundCategory_master:0.0\nsoundCategory_music:0.0\nrenderDistance:4\nmaxFps:60\n")
    stream = log_path.open("wb")
    process = subprocess.Popen([data["java"], *args, "-cp", data["classpath"], data["main"], *data["args"], *extra],
                               cwd=cwd, stdin=subprocess.PIPE, stdout=stream, stderr=subprocess.STDOUT,
                               creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    stream.close()
    return process


def content(path):
    return path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""


async def scenario(args, rtt, jitter, index):
    folder = ROOT / "run/probes" / f"{args.label}-{index}-rtt{rtt}-j{jitter}"
    folder.mkdir(parents=True, exist_ok=False)
    server_log, a_log, b_log = [folder / f"{s}.log" for s in ("server", "attacker", "defender")]
    proxy = DelayProxy(25566, 25565, rtt, jitter, args.seed + index)
    processes = []
    started = time.monotonic()
    try:
        server = launch("runServer", folder / "server", "", 25565, rtt, server_log, trace=True, negative=args.negative_cases)
        processes.append(server)
        deadline = time.monotonic() + 150
        while "Done (" not in content(server_log):
            if server.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError("server did not start; see " + str(server_log))
            await asyncio.sleep(1)
        await proxy.start()
        defender = launch("runClient", folder / "defender", "defender", 25566, rtt, b_log, negative=args.negative_cases)
        processes.append(defender)
        # Wait for real login before starting attacker and any S3 trial.
        deadline = time.monotonic() + 120
        while "LOGIN name=ProbeB" not in content(server_log):
            if defender.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError("defender did not log in; see " + str(b_log))
            await asyncio.sleep(1)
        attacker = launch("runClient", folder / "attacker", "attacker", 25566, rtt, a_log, trials=args.trials, negative=args.negative_cases)
        processes.append(attacker)
        deadline = time.monotonic() + 240
        next_update = time.monotonic() + 20
        while "CLIENT_COMPLETE" not in content(a_log):
            if attacker.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError("attacker did not complete; see " + str(a_log))
            if time.monotonic() >= next_update:
                text = content(server_log)
                print(json.dumps({"rtt": rtt, "jitter": jitter, "elapsed_s": round(time.monotonic()-started),
                                  "moves": text.count("S2_END"), "guardTrials": text.count("S3_IMPACT")}), flush=True)
                next_update += 20
            await asyncio.sleep(1)
        await asyncio.sleep(1)
        server.stdin.write(b"stop\n")
        server.stdin.flush()
        for _ in range(20):
            if server.poll() is not None:
                break
            await asyncio.sleep(0.5)
        lines = [line for line in content(server_log).splitlines() if "CWPROBE" in line]
        client_lines = [line for line in content(a_log).splitlines() if "CWPROBE" in line]
        (folder / "measurements.log").write_text("\n".join(lines + client_lines) + "\n", encoding="utf-8")
        client_classes = [line for line in content(server_log).splitlines() if line.startswith("[Loaded ") and
                          any(s in line for s in ("net.minecraft.client.", "com.layue13.clashweave.probe.client.", "org.lwjgl.opengl."))]
        summary = {"rtt_ms": rtt, "jitter_ms": jitter, "seed": args.seed+index,
                   "elapsed_s": round(time.monotonic()-started, 3), "server_exit": server.poll(),
                   "client_class_loads_on_server": len(client_classes), "client_class_lines": client_classes,
                   "proxy_chunks": len(proxy.delays), "oneway_mean_ms": sum(proxy.delays)/len(proxy.delays),
                   "oneway_min_ms": min(proxy.delays), "oneway_max_ms": max(proxy.delays),
                   "server_summaries": [l for l in lines if "SUMMARY" in l],
                   "client_results": [l for l in client_lines if any(s in l for s in ("S2_RESULT", "S4_VIEW", "S5_REPLAY", "CLIENT_COMPLETE"))]}
        (folder / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(summary), flush=True)
    finally:
        if proxy.server:
            await proxy.close()
        for process in processes:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()


async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--accept-eula", action="store_true", required=True)
    parser.add_argument("--rtts", default="0,50,100,200,100:20")
    parser.add_argument("--label", default=time.strftime("%Y%m%d-%H%M%S"))
    parser.add_argument("--seed", type=int, default=1710)
    parser.add_argument("--trials", type=int, default=12)
    parser.add_argument("--negative-cases", action="store_true")
    args = parser.parse_args()
    for index, entry in enumerate(args.rtts.split(",")):
        rtt, _, jitter = entry.partition(":")
        await scenario(args, int(rtt), int(jitter or "0"), index)


if __name__ == "__main__":
    asyncio.run(main())
