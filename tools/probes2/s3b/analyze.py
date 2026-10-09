"""Derive timing and conditional acceptance from per-message real measurements."""
import argparse,json,re,statistics,hashlib,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
def records(path):
    out=[]
    for line in path.read_text(encoding='utf-8').splitlines():
        if 'CW3 ' not in line:continue
        payload=line.split('CW3 ',1)[1];event=payload.split()[0]
        row={'event':event};row.update(dict(re.findall(r'(\w+)=([^\s]+)',payload)));out.append(row)
    return out
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--label',required=True);args=ap.parse_args();allmetrics=[]
    for ev in sorted((ROOT/'docs/probes/round2/evidence').glob(args.label+'-rtt*')):
        summary=json.loads((ev/'summary.json').read_text());rows=records(ev/'server-measurements.log');client=records(ev/'defender-measurements.log')
        groups={e:{int(x['id']):x for x in rows if x['event']==e and 'id'in x and (e!='RECEIVE' or x['kind']=='2')} for e in ('PLAN','RECEIVE','CONSUME','RESULT')}
        sends={int(x['id']):x for x in client if x['event']=='SEND'};plans={int(x['id']):x for x in client if x['event']=='CLIENT_PLAN'}
        serverwire={int(x['id']):x for x in rows if x['event']=='WIRE' and x['kind']=='2'}
        clientwire={int(x['id']):x for x in client if x['event']=='WIRE' and x['kind']=='1'}
        traced=[];counts={};eligible=eligiblepass=0;late=0;boundary=0
        for id,r in sorted(groups['RESULT'].items()):
            p=groups['PLAN'][id];s=sends.get(id);a=groups['RECEIVE'].get(id);c=groups['CONSUME'].get(id)
            if not s or not a:raise ValueError(f'missing message id {id}')
            if id not in serverwire or id not in clientwire:raise ValueError(f'missing wire trace id {id}')
            sw=serverwire[id];cw=clientwire[id]
            actualstamp=int(s['stamp']);hit=int(r['hit']);inside=int(sw['wall'])<=int(r['commitWall']);window=hit-(summary['window']-1)<=actualstamp<=hit
            iseligible=inside and window;passed=r['parry']=='true' and float(r['loss'])==0
            if p['legacy']=='false':
                eligible+=iseligible;eligiblepass+=iseligible and passed
                key=(int(p['defer']),int(p['lead']));g=counts.setdefault(key,{'count':0,'parries':0,'eligible':0,'eligiblePass':0,'windowMiss':0,'late':0})
                g['count']+=1;g['parries']+=passed;g['eligible']+=iseligible;g['eligiblePass']+=iseligible and passed;g['windowMiss']+=not window;g['late']+=not inside
            cp=plans[id]
            traced.append({'id':id,'legacy':p['legacy']=='true','anchorTick':int(p['anchorTick']),'stamp':actualstamp,'intendedStamp':int(p['stamp']),'actualLead':hit-actualstamp,'hit':hit,'commit':int(r['commit']),
                           'anchorWall':int(p['anchorWall']),'clientNetReceiveWall':int(cw['wall']),'clientHandlerWall':int(cp['receiveWall']),'clientConsumeWall':int(cp['consumeWall']),'sendWall':int(s['sendWall']),'serverNetReceiveWall':int(sw['wall']),'serverHandlerWall':int(a['recvWall']),'serverConsumeWall':int(c['consumeWall']) if c else None,
                           's2cNetworkMs':int(cw['wall'])-int(p['anchorWall']),'clientNativeQueueMs':int(cp['receiveWall'])-int(cw['wall']),'clientExperimentQueueMs':int(cp['consumeWall'])-int(cp['receiveWall']),
                           'declaredPressWall':int(p['anchorWall'])+50*(int(s['stamp'])-int(p['anchorTick'])),
                           'sendAfterDeclaredMs':int(s['sendWall'])-int(p['anchorWall'])-50*(actualstamp-int(p['anchorTick'])),
                           'c2sMs':int(sw['wall'])-int(s['sendWall']),'serverNativeQueueMs':int(a['recvWall'])-int(sw['wall']),'serverExperimentQueueMs':int(c['consumeWall'])-int(a['recvWall']) if c else None,
                           'consumeAge':int(c['age']) if c else None,'wireDeadlineMarginMs':int(r['commitWall'])-int(sw['wall']),'eligible':iseligible,'passed':passed})
        leadcounts=[{'defer':d,'lead':l,**v}for(d,l),v in sorted(counts.items())]
        minima={str(d):next((l for l in range(5) if counts[d,l]['count']==summary['repetitions_per_lead'] and counts[d,l]['parries']==counts[d,l]['count']),None)for d in (2,3)}
        legacyrows=[t for t in traced if t['legacy']];legacyages={str(age):sum(t['consumeAge']==age for t in legacyrows) for age in sorted({t['consumeAge'] for t in legacyrows})}
        legacy_stats={key:{'min':min(t[key]for t in legacyrows),'median':statistics.median(t[key]for t in legacyrows),'max':max(t[key]for t in legacyrows)}for key in ('s2cNetworkMs','clientNativeQueueMs','clientExperimentQueueMs','sendAfterDeclaredMs','c2sMs','serverNativeQueueMs','serverExperimentQueueMs')} if legacyrows else {}
        newtraces=[t for t in traced if not t['legacy']]
        wirestats={key:{'min':min(t[key]for t in newtraces),'median':statistics.median(t[key]for t in newtraces),'max':max(t[key]for t in newtraces)}for key in ('c2sMs','s2cNetworkMs','serverNativeQueueMs','clientNativeQueueMs')}
        ages=[t['consumeAge']for t in newtraces if t['consumeAge']is not None]
        actualleads={str(lead):{'count':sum(t['actualLead']==lead for t in newtraces),'eligible':sum(t['actualLead']==lead and t['eligible']for t in newtraces),'eligiblePass':sum(t['actualLead']==lead and t['eligible']and t['passed']for t in newtraces)}for lead in sorted({t['actualLead']for t in newtraces})}
        actual_by_delay={str(delay):{str(lead):sum(t['actualLead']==lead and int(groups['PLAN'][t['id']]['defer'])==delay for t in newtraces)for lead in range(-1,5)}for delay in (2,3)}
        strict=[t for t in newtraces if t['eligible']and t['wireDeadlineMarginMs']>0]
        metrics={'rtt_ms':summary['rtt_ms'],'one_way_ms':summary['one_way_ms'],'input_age_range':[min(ages),max(ages)],'actual_lead_histogram':actualleads,'actual_lead_by_defer':actual_by_delay,'guard_wire_timing_stats_ms':wirestats,'groups':leadcounts,'minimum_lead_for_all_repetitions':minima,'window_and_network_on_time':eligible,'window_and_network_on_time_pass':eligiblepass,'strict_before_deadline':len(strict),'strict_before_deadline_pass':sum(t['passed']for t in strict),'conditional_all':eligible==eligiblepass,'eligible_min_deadline_margin_ms':min((t['wireDeadlineMarginMs']for t in newtraces if t['eligible']),default=None),'deadline_same_ms_count':sum(t['wireDeadlineMarginMs']==0 for t in newtraces),'legacy_consume_age_counts':legacyages,'legacy_timing_stats_ms':legacy_stats,'legacy_trace':legacyrows}
        (ev/'timing.json').write_text(json.dumps(metrics,indent=2)+'\n');(ev/'per-message.json').write_text(json.dumps(traced,indent=2)+'\n');allmetrics.append(metrics)
    out=ROOT/'docs/probes/round2/evidence'/f'{args.label}-metrics.json';out.write_text(json.dumps(allmetrics,indent=2)+'\n')
    mcroot=ROOT/'build/rfg/minecraft-src/java'
    names=('net/minecraft/client/Minecraft.java','net/minecraft/client/multiplayer/PlayerControllerMP.java','net/minecraft/network/NetworkManager.java','net/minecraft/network/NetworkSystem.java','net/minecraft/server/MinecraftServer.java','cpw/mods/fml/common/network/internal/FMLProxyPacket.java','cpw/mods/fml/common/network/simpleimpl/SimpleChannelHandlerWrapper.java')
    hashes={name:hashlib.sha256((mcroot/name).read_bytes()).hexdigest()for name in names};hashes['tools/probes2/s3b/analyze.py']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    (out.parent/f'{args.label}-context-hashes.json').write_text(json.dumps(hashes,indent=2)+'\n');print(json.dumps(allmetrics,indent=2))
    clocks={name:vars(time.get_clock_info(name))for name in ('monotonic','perf_counter')}
    (out.parent/f'{args.label}-clock-info.json').write_text(json.dumps(clocks,indent=2)+'\n')
if __name__=='__main__':main()
