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
        public boolean confirmedDamageHit;
        private boolean movementReady;

        Instance(long id, long start, int sequence, ActionCatalog.Definition definition) {
            this.id = id;
            this.start = start;
            this.sequence = sequence;
            this.definition = definition;
        }

        public boolean claim(String target, int segment) {
            return hits.add(target + ":" + segment);
        }

        public boolean claimMovementReady() {
            if (movementReady) return false;
            movementReady = true;
            return true;
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
        long compensation;
    }

    private ActionCatalog catalog;
    private WeaponDefinition weapon;
    private final Map<Intent, Request> buffered = new EnumMap<>(Intent.class);
    private final List<Result> results = new ArrayList<>();
    private long nextId;
    private Instance current;
    private boolean sheathed = true;
    private long counterUntil;
    private long finisherUntil;

    public void openCounter(long tick, int window) {
        if (current == null) counterUntil = tick + window;
    }

    public boolean counter(long tick) {
        return current == null && tick < counterUntil;
    }

    /**
     * A broken target is in reach: for the next {@code window} ticks the finisher inputs mean the finisher, idle or
     * mid-combo.
     */
    public void openFinisher(long tick, int window) {
        finisherUntil = tick + window;
    }

    public boolean finisher(long tick) {
        return tick < finisherUntil;
    }

    public Scheduler(ActionCatalog catalog) {
        this(WeaponDefinition.katana(), catalog);
    }

    public Scheduler(WeaponDefinition weapon, ActionCatalog catalog) {
        this.weapon = java.util.Objects.requireNonNull(weapon);
        this.catalog = java.util.Objects.requireNonNull(catalog);
        sheathed = catalog.hasSheathedState();
    }

    public WeaponDefinition weapon() {
        return weapon;
    }

    public void useWeapon(WeaponDefinition next, ActionCatalog actions) {
        if (current != null) throw new IllegalStateException("Weapon changes between actions only");
        if (catalog != actions) {
            clearBuffer();
            sheathed = java.util.Objects.requireNonNull(actions)
                .hasSheathedState();
        }
        weapon = java.util.Objects.requireNonNull(next);
        catalog = java.util.Objects.requireNonNull(actions);
    }

    public Instance current() {
        return current;
    }

    public boolean sheathed() {
        return sheathed;
    }

    public void request(Intent input, int sequence, long tick) {
        buffer(input, sequence, tick, 0);
    }

    private void buffer(Intent input, int sequence, long tick, long compensation) {
        Request request = new Request();
        request.sequence = sequence;
        request.compensation = compensation;
        request.expires = tick + catalog.bufferTicks;
        request.origin = current == null ? 0 : current.id;
        if (current == null) {
            request.target = catalog.entryTarget(sheathed, input);
            String counterTarget = counter(tick) ? catalog.counterTarget(input) : null;
            if (counterTarget != null) request.target = counterTarget;
            String finisherTarget = finisher(tick) ? catalog.finisherTarget(input) : null;
            if (finisherTarget != null) {
                request.target = finisherTarget;
                request.expires = Math.min(request.expires, finisherUntil);
            }
            if (input == Intent.LIGHT && counter(tick)) request.expires = Math.min(request.expires, counterUntil);
        } else {
            for (ActionCatalog.Edge edge : current.definition.edges) {
                if (edge.input == input && tick - request.compensation - current.start < edge.until) {
                    request.edge = edge;
                    request.target = catalog.normalize(edge.target);
                    // The heavy of a running combo means the finisher too while a broken target is in reach.
                    String finisherTarget = finisher(tick) ? catalog.finisherTarget(input) : null;
                    if (finisherTarget != null) request.target = finisherTarget;
                    break;
                }
            }
        }
        if (request.target == null) {
            results.add(new Result(sequence, "ILLEGAL_NODE", ""));
            return;
        }
        Request replaced = buffered.put(input, request);
        if (replaced != null)
            results.add(new Result(replaced.sequence, "REPLACED", catalog.require(replaced.target).id));
    }

    /** Wire requests bind the instance observed at the press, including an explicit idle origin. */
    public void request(Intent input, int sequence, long tick, long expectedOrigin) {
        request(input, sequence, tick, expectedOrigin, 0, 0);
    }

    /** Receipt age must come from a stamp already accepted by InputGate. */
    public void request(Intent input, int sequence, long tick, long expectedOrigin, long receiptAge, int limit) {
        if (expectedOrigin != (current == null ? 0 : current.id)) {
            results.add(new Result(sequence, "ORIGIN_CHANGED", ""));
            return;
        }
        buffer(input, sequence, tick, Math.max(0, Math.min(receiptAge, limit)));
    }

    public void tick(long tick) {
        tick(tick, true);
    }

    public void tick(long tick, boolean grounded) {
        if (buffered.isEmpty()) {
            // Idle or mid-action with nothing buffered: no request can start, so skip the per-tick scratch list.
            if (current != null && tick - current.start >= current.definition.duration()) {
                if (catalog.entersSheathed(current.definition.id)) sheathed = true;
                finish();
            }
            return;
        }
        // Expire before finishing the origin: a derivative must never become an idle request.
        List<Request> ready = new ArrayList<>();
        Iterator<Request> iterator = buffered.values()
            .iterator();
        while (iterator.hasNext()) {
            Request request = iterator.next();
            long origin = current == null ? 0 : current.id;
            if (tick >= request.expires || origin != request.origin) {
                results.add(new Result(request.sequence, "EXPIRED_OR_CHANGED", catalog.require(request.target).id));
                iterator.remove();
            } else if (request.edge != null && request.edge.requires != null
                && (!grounded || request.edge.requires.equals("AIR"))) {
                    results.add(new Result(request.sequence, "CONDITION_CHANGED", catalog.require(request.target).id));
                    iterator.remove();
                } else if (request.edge == null || (tick - current.start >= request.edge.from
                    && tick - request.compensation - current.start < request.edge.until
                    && (!request.edge.hit || current.confirmedHit)
                    && (!request.edge.damageHit || current.confirmedDamageHit))) {
                        ready.add(request);
                    } else if (tick - request.compensation - current.start >= request.edge.until) {
                        results.add(new Result(request.sequence, "WINDOW_CLOSED", catalog.require(request.target).id));
                        iterator.remove();
                    }
        }
        ready.sort(Comparator.comparingInt(request -> request.sequence));
        if (!ready.isEmpty()) {
            Request selected = ready.get(ready.size() - 1);
            for (Request request : buffered.values()) {
                if (request != selected)
                    results.add(new Result(request.sequence, "CONFLICT", catalog.require(request.target).id));
            }
            buffered.clear();
            counterUntil = 0;
            finisherUntil = 0;
            finish();
            current = new Instance(++nextId, tick, selected.sequence, catalog.require(selected.target));
            if (catalog.entersReady(selected.target)) sheathed = false;
            results.add(new Result(selected.sequence, "START", catalog.require(selected.target).id));
        } else if (current != null && tick - current.start >= current.definition.duration()) {
            if (catalog.entersSheathed(current.definition.id)) sheathed = true;
            finish();
        }
    }

    public void interrupt() {
        clearBuffer();
        finish();
    }

    public void clearBuffer() {
        counterUntil = 0;
        finisherUntil = 0;
        for (Request request : buffered.values()) {
            results.add(new Result(request.sequence, "INTERRUPTED", catalog.require(request.target).id));
        }
        buffered.clear();
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
