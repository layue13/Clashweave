package com.layue13.clashweave.diagnostics;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only observer shared by both frozen binaries. Never supplies a gameplay value. */
public final class TraceObserver {
    private static final Map<String,Field> FIELDS=new ConcurrentHashMap<>();
    private static final Map<Object,Object> OWNERS=new IdentityHashMap<>();
    private static final ThreadLocal<Map<String,Object>> CAMERA=new ThreadLocal<>();
    private static final ThreadLocal<Map<String,Object>> SCHEDULER=new ThreadLocal<>();
    private static Object get(Object o,String name){
        if(o==null)return null;
        String key=o.getClass().getName()+"#"+name;
        try {
            Field f=FIELDS.get(key);
            if(f==null){Class<?> c=o.getClass();while(c!=null){try{f=c.getDeclaredField(name);break;}catch(NoSuchFieldException e){c=c.getSuperclass();}}
                if(f==null)throw new IllegalStateException(key);f.setAccessible(true);FIELDS.put(key,f);}
            return f.get(o);
        }catch(IllegalAccessException e){throw new IllegalStateException(key,e);}
    }
    private static double number(Object o,String name){return ((Number)get(o,name)).doubleValue();}
    private static boolean flag(Object o,String name){return (Boolean)get(o,name);}
    private static double difference(double a,double b){return (((float)a-(float)b)%360+540)%360-180;}
    private static String actor(Object player){try{return String.valueOf(player.getClass().getMethod("getCommandSenderName").invoke(player));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void emit(String type,Map<String,Object> data){StringBuilder s=new StringBuilder("P0_DIAG_").append(type).append(" nano=").append(System.nanoTime());for(Map.Entry<String,Object> e:data.entrySet())s.append(' ').append(e.getKey()).append('=').append(e.getValue());System.out.println(s);}
    private static Map<String,Object> row(){return new LinkedHashMap<>();}

    public static void cameraBefore(Object o,double bearing,double rawX,double rawZ,double seconds,double smooth,double tau,double maxSpeed,double teleport,double stall,double recovery,boolean modern){
        double real=Double.isFinite(seconds)?Math.max(0,seconds):0,dt=Math.min(.05,real);
        double age=number(o,"rawSeconds")+real;
        double vx=age>.5?0:number(o,"rawVx"),vz=age>.5?0:number(o,"rawVz");
        double dx=rawX-number(o,"x"),dz=rawZ-number(o,"z");
        double residual=Math.hypot(dx-vx*age,dz-vz*age),change=difference(bearing,number(o,"previous"));
        double threshold=Math.max(30,maxSpeed*(modern?real:dt)*2);
        boolean ready=flag(o,"ready"),lost=modern&&flag(o,"lostHistory");
        String branch=!ready?"NOT_READY":Math.hypot(dx,dz)>1e-6&&residual>teleport?"POSITION_RESIDUAL":Math.abs(change)>threshold?"ANGLE_JUMP":"NONE";
        Map<String,Object> r=row();r.put("branch",branch);r.put("historyLost",!ready&&lost);r.put("stall",modern&&real>stall);r.put("ready",ready);r.put("residual",residual);r.put("change",change);r.put("threshold",threshold);r.put("realSeconds",real);r.put("dt",dt);r.put("beforeVelocity",number(o,"velocity"));r.put("modern",modern);CAMERA.set(r);
    }
    public static void cameraAfter(double lead,Object o){
        Map<String,Object> r=CAMERA.get();r.put("afterVelocity",number(o,"velocity"));r.put("suppressed",number(o,"suppressed"));r.put("lead",lead);
        if(!r.get("branch").equals("NONE")||(Boolean)r.get("stall")||((Double)r.get("beforeVelocity")!=0&&(Double)r.get("afterVelocity")==0))emit("CAMERA",r);
        CAMERA.remove();
    }
    public static void consume(Object server,Object input){
        Object player=get(input,"player"),message=get(input,"message"),owner=null;
        for(Object value:((Map<?,?>)get(server,"states")).values())if(get(value,"player")==player){owner=value;break;}
        if(owner==null)return;
        OWNERS.put(get(owner,"scheduler"),owner);
        if(number(message,"kind")<0)return;
        Map<String,Object> r=row();r.put("actor",actor(player));
        for(String key:new String[]{"sequence","kind","stamp","origin","yaw","pitch"})r.put(key,get(message,key));
        long received=((Number)get(input,"receivedTick")).longValue(),tick=((Number)get(server,"tick")).longValue(),stamp=((Number)get(message,"stamp")).longValue();
        r.put("receivedTick",received);r.put("consumedTick",tick);r.put("receivedAge",received-stamp);r.put("effectiveAge",tick-Math.min(received,stamp));r.put("revision",get(owner,"revision"));
        Object current=get(get(owner,"scheduler"),"current");r.put("currentOrigin",current==null?0:get(current,"id"));r.put("currentStart",current==null?0:get(current,"start"));r.put("confirmedHit",current!=null&&flag(current,"confirmedHit"));r.put("knownYaw",get(player,"rotationYaw"));r.put("promisedYaw",get(owner,"yaw"));emit("INPUT",r);
    }
    public static void press(Object message,Object proxy){
        try{
            Object visual=proxy.getClass().getMethod("own").invoke(proxy),state=visual==null?null:get(visual,"state"),player=get(get(proxy,"minecraft"),"thePlayer");
            Map<String,Object> r=row();r.put("actor",actor(player));for(String key:new String[]{"sequence","kind","stamp","origin","yaw","pitch"})r.put(key,get(message,key));
            r.put("actualYaw",get(player,"rotationYaw"));r.put("actualPitch",get(player,"rotationPitch"));
            for(String key:new String[]{"revision","tick","instance","start","action"})r.put("observed_"+key,state==null?"NONE":get(state,key));emit("PRESS",r);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    public static void schedulerBefore(Object scheduler,long tick){
        Map<String,Object> r=row();r.put("tick",tick);r.put("resultCount",((List<?>)get(scheduler,"results")).size());
        Object current=get(scheduler,"current"),owner=OWNERS.get(scheduler);r.put("currentOrigin",current==null?0:get(current,"id"));r.put("currentStart",current==null?0:get(current,"start"));r.put("currentAction",current==null?"IDLE":get(get(current,"definition"),"id"));r.put("confirmedHit",current!=null&&flag(current,"confirmedHit"));
        r.put("actor",owner==null?"UNKNOWN":actor(get(owner,"player")));r.put("revision",owner==null?-1:get(owner,"revision"));r.put("promisedYaw",owner==null?0:get(owner,"yaw"));
        Map<Integer,Map<String,Object>> requests=new HashMap<>();for(Object q:((Map<?,?>)get(scheduler,"buffered")).values()){
            Map<String,Object> x=row();for(String key:new String[]{"sequence","expires","origin","target"})x.put(key,get(q,key));Object edge=get(q,"edge");if(edge!=null)for(String key:new String[]{"from","until","hit"})x.put("edge_"+key,get(edge,key));requests.put(((Number)get(q,"sequence")).intValue(),x);}
        r.put("requests",requests);SCHEDULER.set(r);
    }
    @SuppressWarnings("unchecked")
    public static void schedulerAfter(Object scheduler){
        Map<String,Object> r=SCHEDULER.get();List<?> results=(List<?>)get(scheduler,"results");Map<Integer,Map<String,Object>> requests=(Map<Integer,Map<String,Object>>)r.remove("requests");
        int count=(Integer)r.remove("resultCount");for(int i=count;i<results.size();i++){Object result=results.get(i);if(!"EXPIRED_OR_CHANGED".equals(get(result,"status")))continue;
            int seq=((Number)get(result,"sequence")).intValue();Map<String,Object> q=requests.get(seq),out=new LinkedHashMap<>(r);out.putAll(q);out.put("expired",((Number)r.get("tick")).longValue()>=((Number)q.get("expires")).longValue());out.put("changed",((Number)r.get("currentOrigin")).longValue()!=((Number)q.get("origin")).longValue());emit("EXPIRED",out);}
        SCHEDULER.remove();
    }
}
