"""Extract measurements, keep reproducible source hashes, and copy native game screenshots."""
import argparse
import hashlib
import json
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument("label")
args = parser.parse_args()
out = ROOT / "docs/probes/evidence" / args.label
out.mkdir(parents=True, exist_ok=True)
rows = []
for folder in sorted((ROOT / "run/probes").glob(args.label + "-*")):
    summary_file = folder / "summary.json"
    if not summary_file.exists():
        continue
    target = out / folder.name
    target.mkdir(exist_ok=True)
    for name in ("summary.json", "measurements.log"):
        shutil.copyfile(folder / name, target / name)
    server_text = (folder / "server.log").read_text(encoding="utf-8", errors="replace")
    client_text = (folder / "attacker.log").read_text(encoding="utf-8", errors="replace")
    defender_text = (folder / "defender.log").read_text(encoding="utf-8", errors="replace")
    for name, text in (("defender-probe.log", defender_text), ("server-errors.log", server_text), ("client-errors.log", client_text)):
        lines = [line for line in text.splitlines() if "CWPROBE" in line] if name.startswith("defender") else [line for line in text.splitlines() if re.search(r"/(ERROR|FATAL)\]|Exception|Caused by:", line) and not line.startswith("[Loaded ")]
        (target / name).write_text("\n".join(lines)+"\n", encoding="utf-8")
    summary = json.loads(summary_file.read_text())
    negative = "CWPROBE S5_TARGET_SERVER" in server_text
    legal = []
    for line in summary["client_results"]:
        if "S2_RESULT" in line:
            sample = dict(re.findall(r"(\w+)=([^\s]+)", line.split("CWPROBE ", 1)[1]))
            if int(sample["trial"]) % 3 != 2 and not (negative and int(sample["trial"]) == 0):
                legal.append(sample)
    impacts = []
    for line in server_text.splitlines():
        if "CWPROBE S3_IMPACT" in line:
            impacts.append(dict(re.findall(r"(\w+)=([^\s]+)", line.split("CWPROBE ", 1)[1])))
    row = {"scenario": folder.name, "rtt_ms": summary["rtt_ms"], "jitter_ms": summary["jitter_ms"],
           "oneway_mean_ms": summary["oneway_mean_ms"], "legal_moves": len(legal),
           "legal_move_rejects": sum(int(s["rejects"]) for s in legal),
           "legal_S08_packets": sum(int(s["S08"]) for s in legal),
           "max_endpoint_error_blocks": max(float(s["delta"]) for s in legal),
           "parry_success": sum(s["parry"] == "true" for s in impacts), "parry_trials": len(impacts),
           "parry_by_lead": {str(lead): sum(s["parry"] == "true" for s in impacts if int(s["lead"]) == lead) for lead in range(5)},
           "server_client_classes": summary["client_class_loads_on_server"]}
    row["negative_cases"] = negative
    rows.append(row)
    if summary["rtt_ms"] == 100 and summary["jitter_ms"] == 0:
        for file in (folder / "attacker/screenshots").glob("cw-probe-view-*.png"):
            shutil.copyfile(file, target / file.name)
(out / "metrics.json").write_text(json.dumps(rows, indent=2)+"\n", encoding="utf-8")
sources = ["net/minecraft/entity/EntityLivingBase.java", "net/minecraft/entity/Entity.java",
           "net/minecraft/entity/player/EntityPlayerMP.java", "net/minecraft/util/MovementInputFromOptions.java",
           "net/minecraft/network/NetHandlerPlayServer.java", "net/minecraft/network/NetworkManager.java",
           "net/minecraft/client/Minecraft.java", "net/minecraft/client/renderer/entity/RenderPlayer.java",
           "net/minecraft/entity/EntityTrackerEntry.java", "net/minecraft/entity/EntityLiving.java"]
manifest = {name: hashlib.sha256((ROOT / "build/rfg/minecraft-src/java" / name).read_bytes()).hexdigest() for name in sources}
(out / "source-sha256.json").write_text(json.dumps(manifest, indent=2)+"\n", encoding="utf-8")
print(json.dumps(rows, indent=2))
