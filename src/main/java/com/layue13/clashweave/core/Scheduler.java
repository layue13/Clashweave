package com.layue13.clashweave.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** No platform objects. An accepted request binds its origin instance and edge permanently. */
public final class Scheduler {

    public static final class Instance {

        public final long id;
        public final long start;
        public final int sequence;
        public final ActionCatalog.Definition definition;
        private final Set<String> hits = new HashSet<>();
        public boolean confirmedHit;

        Instance(long id, long start, int sequence, ActionCatalog.Definition definition) {
            this.id = id;
            this.start = start;
            this.sequence = sequence;
            this.definition = definition;
        }

        public boolean claim(String target, int segment) {
            return hits.add(target + ":" + segment);
        }

        public int ledgerSize() {
            return hits.size();
        }

        public void clearLedger() {
            hits.clear();
        }
    }

    public static final class Result {

        public final int sequence;
        public final String status;
        public final String action;

        Result(int sequence, String status, String action) {
            this.sequence = sequence;
            this.status = status;
            this.action = action;
        }
    }

    private static final class Request {

        int sequence;
        long expires;
        long origin;
        String target;
        ActionCatalog.Edge edge;
    }

    private final ActionCatalog catalog;
    private final Map<Intent, Request> buffered = new EnumMap<>(Intent.class);
    private final List<Result> results = new ArrayList<>();
    private long nextId;
    private Instance current;
    private boolean sheathed = true;

    public Scheduler(ActionCatalog catalog) {
        this.catalog = catalog;
    }

    public Instance current() {
        return current;
    }

    public boolean sheathed() {
        return sheathed;
    }

    public void request(Intent input, int sequence, long tick) {
        Request request = new Request();
        request.sequence = sequence;
        request.expires = tick + catalog.bufferTicks;
        request.origin = current == null ? 0 : current.id;
        if (current == null) {
            if (input == Intent.LIGHT) request.target = sheathed ? "iai" : "light_1";
            else if (input == Intent.HEAVY && !sheathed) request.target = "heavy";
            else if (input == Intent.SHEATHE && !sheathed) request.target = "sheathe";
        } else {
            for (ActionCatalog.Edge edge : current.definition.edges) {
                if (edge.input == input && tick - current.start < edge.until) {
                    request.edge = edge;
                    request.target = edge.target;
                    break;
                }
            }
        }
        if (request.target == null) {
            results.add(new Result(sequence, "ILLEGAL_NODE", ""));
            return;
        }
        Request replaced = buffered.put(input, request);
        if (replaced != null) results.add(new Result(replaced.sequence, "REPLACED", replaced.target));
    }

    public void tick(long tick) {
        // Expire before finishing the origin: a derivative must never become an idle request.
        List<Request> ready = new ArrayList<>();
        Iterator<Request> iterator = buffered.values()
            .iterator();
        while (iterator.hasNext()) {
            Request request = iterator.next();
            long origin = current == null ? 0 : current.id;
            if (tick >= request.expires || origin != request.origin) {
                results.add(new Result(request.sequence, "EXPIRED_OR_CHANGED", request.target));
                iterator.remove();
            } else if (request.edge == null
                || (tick - current.start >= request.edge.from && tick - current.start < request.edge.until
                    && (!request.edge.hit || current.confirmedHit))) {
                        ready.add(request);
                    } else
                if (tick - current.start >= request.edge.until) {
                    results.add(new Result(request.sequence, "WINDOW_CLOSED", request.target));
                    iterator.remove();
                }
        }
        ready.sort(Comparator.comparingInt(request -> request.sequence));
        if (!ready.isEmpty()) {
            Request selected = ready.get(ready.size() - 1);
            for (Request request : buffered.values()) {
                if (request != selected) results.add(new Result(request.sequence, "CONFLICT", request.target));
            }
            buffered.clear();
            finish();
            current = new Instance(++nextId, tick, selected.sequence, catalog.get(selected.target));
            if (!selected.target.equals("sheathe")) sheathed = false;
            results.add(new Result(selected.sequence, "START", selected.target));
        } else if (current != null && tick - current.start >= current.definition.duration()) {
            if (current.definition.id.equals("sheathe")) sheathed = true;
            finish();
        }
    }

    public void interrupt() {
        for (Request request : buffered.values()) {
            results.add(new Result(request.sequence, "INTERRUPTED", request.target));
        }
        buffered.clear();
        finish();
    }

    private void finish() {
        if (current != null) current.clearLedger();
        current = null;
    }

    public List<Result> drainResults() {
        List<Result> copy = new ArrayList<>(results);
        results.clear();
        return copy;
    }
}
