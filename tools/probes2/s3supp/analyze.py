"""Audit every real input against independently joined wire and commit clocks."""
import argparse,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
def records(path,tag):
    out=[]
    for line in path.read_text(encoding="utf-8").splitlines():
        if "CW3 "+tag+" " in line:out.append(dict(re.findall(r"(\w+)=([^ ]+)",line)))
    return out

def main():
    p=argparse.ArgumentParser();p.add_argument("--label",required=True);a=p.parse_args();aggregate=[]
    for directory in sorted((ROOT/"docs/probes/round3/evidence").glob(a.label+"-rtt*")):
        server=directory/"server-measurements.log";client=directory/"defender-measurements.log"
        sends={int(x["id"]):x for x in records(client,"SEND")}
        wires={int(x["id"]):x for x in records(server,"WIRE") if x.get("side")=="SERVER" and x.get("kind")=="2"}
        plans={int(x["id"]):x for x in records(server,"PLAN")}
        rows=[]
        for result in records(server,"RESULT"):
            i=int(result["id"]);send=sends[i];wire=wires[i];plan=plans[i]
            stamp=int(send["stamp"]);hit=int(result["hit"]);arrival=int(wire["nano"]);deadline=int(result["deadlineNano"])
            window=hit-4<=stamp<=hit;before=arrival<deadline;expected=window and before
            parry=result["parry"]=="true";loss=float(result["loss"])
            violation=parry!=expected or loss!=(0 if expected else 1)
            rows.append({"id":i,"defer":int(result["defer"]),"planned_lead":int(plan["lead"]),"actual_lead":hit-stamp,"stamp":stamp,"hit":hit,"send_nano":int(send["sendNano"]),"arrival_nano":arrival,"deadline_nano":deadline,"window":window,"before_deadline":before,"expected_parry":expected,"actual_parry":parry,"health_loss":loss,"violation":violation,"single_ms_bin":arrival//1000000==deadline//1000000,"margin_ms":(deadline-arrival)/1e6,"oneway_ms":(arrival-int(send["sendNano"]))/1e6,"allowed_oneway_ms":(deadline-int(send["sendNano"]))/1e6,"feedback_delay_ms":float(result["feedbackDelayMs"])})
        summary={"rtt":int(directory.name.split("rtt")[-1]),"inputs":len(rows),"violations":sum(r["violation"] for r in rows),"eligible":sum(r["expected_parry"] for r in rows),"late":sum(not r["before_deadline"] for r in rows),"same_ms_bins":sum(r["single_ms_bin"] for r in rows),"by_defer":{}}
        for defer in [2,3]:
            group=[r for r in rows if r["defer"]==defer];last=[r for r in group if r["actual_lead"]==0]
            summary["by_defer"][str(defer)]={"inputs":len(group),"eligible":sum(r["expected_parry"] for r in group),"late":sum(not r["before_deadline"] for r in group),"feedback_ms_range":[min((r["feedback_delay_ms"] for r in group),default=0),max((r["feedback_delay_ms"] for r in group),default=0)],"oneway_ms_range":[min((r["oneway_ms"] for r in group),default=0),max((r["oneway_ms"] for r in group),default=0)],"last_tick_allowed_oneway_ms_range":[min(r["allowed_oneway_ms"] for r in last),max(r["allowed_oneway_ms"] for r in last)] if last else None}
        (directory/"per-input-nano.json").write_text(json.dumps(rows,indent=2)+"\n",encoding="utf-8")
        aggregate.append(summary)
    target=ROOT/"docs/probes/round3/evidence"/(a.label+"-invariants.json")
    target.write_text(json.dumps(aggregate,indent=2)+"\n",encoding="utf-8");print(json.dumps(aggregate,indent=2))
if __name__=="__main__":main()
