package com.layue13.clashweave.validation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;

import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.core.Scheduler;
import com.layue13.clashweave.forge.CombatServer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * WF-1c server driver (-Dcw.wf1c=weapon2|lifecycle|float-death|float-dimension|float-disconnect). It applies one
 * lifecycle event at a time to a player who is attacking through the real input path, then reads authoritative state
 * back. Every check prints WF1C_CHECK with the before/after state; nothing is retried or dropped.
 */
public final class Wf1cScenario {

    private static final class Snap {

        long session;
        int revision, corrections, contacts, dimension;
        long instance;
        String action, style, held;
        boolean sheathed, engaged, verified, sentDefinitions, dead, riding, container;
        double x, y, z;
        float health;

        @Override
        public String toString() {
            return "session=" + session + " rev=" + revision + " instance=" + instance + " action=" + action
                + " style="
                + style
                + " sheathed="
                + sheathed
                + " engaged="
                + engaged
                + " verified="
                + verified
                + " sentDef="
                + sentDefinitions
                + " corrections="
                + corrections
                + " contacts="
                + contacts
                + " held="
                + held
                + " dead="
                + dead
                + " riding="
                + riding
                + " gui="
                + container
                + " dim="
                + dimension
                + String.format(java.util.Locale.ROOT, " pos=%.1f,%.1f,%.1f hp=%.0f", x, y, z, health);
        }
    }

    private abstract class Step {

        final String name;
        int settle = 40;

        Step(String name) {
            this.name = name;
        }

        boolean ready() {
            return true;
        }

        abstract void act();

        /** Null when the expectation holds, otherwise what was observed instead. */
        abstract String verify();
    }

    private final String mode = System.getProperty("cw.wf1c", "");
    private final List<Step> steps = new ArrayList<>();
    private EntityPlayerMP a, b;
    private EntityZombie dummy;
    private int ticks, waiting, index = -1, settleLeft;
    private boolean started, finished;
    private Snap before;
    private long deathSession, disconnectSession;
    private EntityPlayerMP kicked;
    private int passed, failed;

    private CombatServer.PlayerState state(EntityPlayerMP player) {
        return player == null ? null : Clashweave.server.state(player);
    }

    private Snap snap(EntityPlayerMP player) {
        Snap s = new Snap();
        CombatServer.PlayerState st = state(player);
        s.x = player.posX;
        s.y = player.posY;
        s.z = player.posZ;
        s.health = player.getHealth();
        s.dead = player.isDead || player.getHealth() <= 0;
        s.dimension = player.dimension;
        s.riding = player.ridingEntity != null;
        s.container = player.openContainer != player.inventoryContainer;
        ItemStack held = player.getHeldItem();
        s.held = held == null ? "-" : held.getItem()
            .getClass()
            .getSimpleName() + "#" + net.minecraft.item.Item.getIdFromItem(held.getItem());
        if (st == null) {
            s.action = "no-state";
            s.style = "-";
            return s;
        }
        Scheduler.Instance current = st.scheduler.current();
        s.session = st.session;
        s.revision = st.revision;
        s.corrections = st.corrections;
        s.contacts = st.contacts.size();
        s.instance = st.instance;
        s.action = current == null ? "-" : current.definition.id;
        s.style = st.scheduler.weapon().style;
        s.sheathed = st.scheduler.sheathed();
        s.engaged = st.engaged;
        s.verified = st.verified;
        s.sentDefinitions = st.sentDefinitions;
        return s;
    }

    private boolean midAction() {
        CombatServer.PlayerState st = state(a);
        return st != null && st.verified && st.scheduler.current() != null && !a.isDead;
    }

    private void note(String text) {
        System.out.println("WF1C_NOTE " + text);
    }

    // ---- lifecycle steps ----------------------------------------------------------------------------------

    private void buildLifecycle() {
        steps.add(new Step("teleport-mid-action") {

            @Override
            boolean ready() {
                return midAction();
            }

            @Override
            void act() {
                a.playerNetServerHandler.setPlayerLocation(18, 64, 18, 0, 0);
            }

            @Override
            String verify() {
                Snap s = snap(a);
                note("teleport corrections " + before.corrections + "->" + s.corrections + " position " + s.x + "," + s.z);
                a.playerNetServerHandler.setPlayerLocation(0, 64, 0, 0, 0);
                if (s.session != before.session) return "state replaced by a teleport";
                if (s.action.equals("no-state")) return "player lost combat state";
                if (s.instance == before.instance) return "no new action started within " + settle + " ticks";
                return null;
            }
        });
        steps.add(new Step("gui-open-mid-action") {

            {
                settle = 15;
            }

            @Override
            boolean ready() {
                return midAction();
            }

            @Override
            void act() {
                a.displayGUIChest(new InventoryBasic("wf1c", true, 9));
            }

            @Override
            String verify() {
                Snap s = snap(a);
                CombatServer.PlayerState st = state(a);
                if (!s.container) return "container did not open";
                if (st.guard.held()) return "guard still held after the GUI opened";
                note("gui open: action=" + s.action + " engaged=" + s.engaged + " instance " + before.instance + "->" + s.instance);
                return null;
            }
        });
        steps.add(new Step("gui-close-resume") {

            {
                settle = 80;
            }

            @Override
            void act() {
                a.closeScreen();
            }

            @Override
            String verify() {
                Snap s = snap(a);
                if (s.container) return "container still open";
                if (s.instance == before.instance) return "combat did not resume after the GUI closed";
                return null;
            }
        });
        steps.add(new Step("ride-mid-action") {

            EntityPig pig;

            @Override
            boolean ready() {
                return midAction();
            }

            @Override
            void act() {
                pig = new EntityPig(a.worldObj);
                pig.setPosition(a.posX, a.posY, a.posZ);
                pig.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
                    .setBaseValue(0);
                a.worldObj.spawnEntityInWorld(pig);
                a.mountEntity(pig);
            }

            @Override
            String verify() {
                Snap s = snap(a);
                note("ride: riding=" + s.riding + " corrections " + before.corrections + "->" + s.corrections + " action=" + s.action + " instance " + before.instance + "->" + s.instance);
                a.mountEntity(null);
                pig.setDead();
                if (!s.riding) return "player did not mount";
                if (s.session != before.session) return "state replaced by mounting";
                return s.corrections > before.corrections ? "movement corrections while mounted: " + (s.corrections - before.corrections) : null;
            }
        });
        steps.add(new Step("dismount-resume") {

            {
                settle = 60;
            }

            @Override
            void act() {}

            @Override
            String verify() {
                Snap s = snap(a);
                if (s.riding) return "still riding";
                return s.instance == before.instance ? "combat did not resume after dismounting" : null;
            }
        });
        steps.add(new Step("drop-weapon-mid-action") {

            EntityItem dropped;
            float hpAfterDrop;
            long startsAtDrop;

            {
                settle = 40;
            }

            @Override
            boolean ready() {
                return midAction() && a.getHeldItem() != null;
            }

            @Override
            void act() {
                dropped = a.dropOneItem(true);
                startsAtDrop = state(a).instance;
                hpAfterDrop = Float.NaN;
            }

            @Override
            String verify() {
                Snap s = snap(a);
                if (dropped != null) dropped.setDead();
                float zombie = dummy == null ? 0 : dummy.getHealth();
                note("drop: held=" + s.held + " action=" + s.action + " zombieHp=" + zombie);
                a.inventory.setInventorySlotContents(0, new ItemStack(Clashweave.katana));
                a.inventory.currentItem = 0;
                if (!s.held.equals("-")) return "weapon still in hand";
                if (!s.action.equals("-")) return "action still running " + settle + " ticks after the weapon was dropped: " + s.action;
                return null;
            }
        });
        steps.add(new Step("weapon-back-resume") {

            {
                settle = 80;
            }

            @Override
            void act() {}

            @Override
            String verify() {
                Snap s = snap(a);
                return s.instance == before.instance ? "combat did not resume with the weapon back in hand" : null;
            }
        });
        steps.add(new Step("swap-katana-to-glaive-mid-action") {

            {
                settle = 120;
            }

            @Override
            boolean ready() {
                return midAction() && state(a).scheduler.weapon().style.equals("katana:default");
            }

            @Override
            void act() {
                a.inventory.setInventorySlotContents(1, new ItemStack(Items.stick));
                a.inventory.currentItem = 1;
            }

            @Override
            String verify() {
                Snap s = snap(a);
                note("swap->glaive: style=" + s.style + " action=" + s.action + " sheathed=" + s.sheathed + " sentDef=" + s.sentDefinitions);
                if (!s.style.equals("wf1test:reach")) return "style is " + s.style;
                if (!s.sentDefinitions) return "definitions for the new style never acknowledged";
                if (!s.action.startsWith("wf1test:") && !s.action.equals("-")) return "unexpected action " + s.action;
                if (s.sheathed) return "ready-only style reports sheathed";
                return null;
            }
        });
        steps.add(new Step("swap-glaive-to-katana-mid-action") {

            {
                settle = 120;
            }

            @Override
            boolean ready() {
                return midAction() && state(a).scheduler.weapon().style.equals("wf1test:reach");
            }

            @Override
            void act() {
                a.inventory.currentItem = 0;
            }

            @Override
            String verify() {
                Snap s = snap(a);
                note("swap->katana: style=" + s.style + " action=" + s.action + " sheathed=" + s.sheathed + " sentDef=" + s.sentDefinitions);
                if (!s.style.equals("katana:default")) return "style is " + s.style;
                if (!s.sentDefinitions) return "definitions for the restored style never acknowledged";
                return null;
            }
        });
        steps.add(new Step("death-mid-action") {

            {
                settle = 3;
            }

            @Override
            boolean ready() {
                return midAction();
            }

            @Override
            void act() {
                deathSession = before.session;
                a.attackEntityFrom(DamageSource.outOfWorld, 100000f);
            }

            @Override
            String verify() {
                Snap s = snap(a);
                note("death: " + s);
                if (!s.dead) return "player not dead";
                if (!s.action.equals("-")) return "action survived death: " + s.action;
                if (s.engaged) return "still engaged after death";
                if (s.contacts != 0) return "contacts not cleared: " + s.contacts;
                return null;
            }
        });
        steps.add(new Step("respawn-new-state") {

            {
                settle = 0;
            }

            @Override
            boolean ready() {
                Snap s = snap(a);
                return !s.dead && s.session != deathSession && s.verified;
            }

            @Override
            void act() {}

            @Override
            String verify() {
                Snap s = snap(a);
                note("respawn: " + s);
                if (s.session == deathSession) return "session reused";
                if (!s.action.equals("-")) return "action carried over: " + s.action;
                if (!s.sheathed) return "not sheathed after respawn";
                if (s.corrections != 0) return "corrections carried over: " + s.corrections;
                if (!s.verified) return "verification lost on respawn";
                return null;
            }
        });
        steps.add(new Step("respawn-combat-resumes") {

            {
                settle = 100;
            }

            @Override
            void act() {}

            @Override
            String verify() {
                Snap s = snap(a);
                return s.instance == before.instance ? "no action started after respawn" : null;
            }
        });
        steps.add(new Step("dimension-change-mid-action") {

            {
                settle = 150;
            }

            @Override
            boolean ready() {
                return midAction();
            }

            @Override
            void act() {
                a.travelToDimension(1);
            }

            @Override
            String verify() {
                Snap s = snap(a);
                note("dimension: " + s);
                if (s.dimension != 1) return "not in dimension 1";
                if (s.session == before.session) return "state carried across dimensions";
                if (!s.verified) return "verification lost across dimensions";
                if (s.corrections != 0) return "corrections " + s.corrections;
                if (s.instance == 0) return "no action started in the new dimension";
                return null;
            }
        });
        steps.add(new Step("dimension-return") {

            {
                settle = 150;
            }

            @Override
            boolean ready() {
                return state(a) != null && a.dimension == 1 && !a.isDead;
            }

            @Override
            void act() {
                a.travelToDimension(0);
            }

            @Override
            String verify() {
                Snap s = snap(a);
                a.playerNetServerHandler.setPlayerLocation(0, 64, 0, 0, 0);
                note("dimension return: " + s);
                if (s.dimension != 0) return "not back in dimension 0";
                return s.session == before.session ? "state carried across dimensions" : null;
            }
        });
        steps.add(new Step("disconnect-mid-action") {

            {
                settle = 10;
            }

            @Override
            boolean ready() {
                return midAction();
            }

            @Override
            void act() {
                disconnectSession = before.session;
                kicked = a;
                a.playerNetServerHandler.kickPlayerFromServer("wf1c disconnect");
            }

            @Override
            String verify() {
                boolean listed = false;
                for (Object o : MinecraftServer.getServer()
                    .getConfigurationManager().playerEntityList)
                    if (o == kicked) listed = true;
                if (listed) return "player still listed";
                if (state(kicked) != null) return "combat state survived the disconnect";
                try {
                    Field pending = CombatServer.class.getDeclaredField("pending");
                    pending.setAccessible(true);
                    for (Object contact : (List<?>) pending.get(Clashweave.server)) {
                        Field attacker = contact.getClass()
                            .getDeclaredField("attacker");
                        attacker.setAccessible(true);
                        if (((CombatServer.PlayerState) attacker.get(contact)).player == kicked)
                            return "pending contact of the disconnected attacker survived";
                    }
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
                return null;
            }
        });
        steps.add(new Step("reconnect-new-state") {

            {
                settle = 60;
            }

            private EntityPlayerMP fresh() {
                for (Object o : MinecraftServer.getServer()
                    .getConfigurationManager().playerEntityList)
                    if (((EntityPlayerMP) o).getCommandSenderName()
                        .equals("P0A")) return (EntityPlayerMP) o;
                return null;
            }

            @Override
            boolean ready() {
                EntityPlayerMP p = fresh();
                if (p == null || state(p) == null || !state(p).verified) return false;
                a = p;
                return true;
            }

            @Override
            void act() {}

            @Override
            String verify() {
                Snap s = snap(a);
                note("reconnect: " + s);
                if (s.session == disconnectSession) return "session reused across connections";
                if (!s.verified) return "new connection did not verify";
                return s.instance == 0 ? "no action started after reconnecting" : null;
            }
        });
    }

    // ---- floating (launched target) variants ---------------------------------------------------------------

    private long launchedAt;
    private Scheduler.Instance forged(String action, long start, int id) throws Exception {
        Constructor<Scheduler.Instance> c = Scheduler.Instance.class
            .getDeclaredConstructor(long.class, long.class, int.class, ActionCatalog.Definition.class);
        c.setAccessible(true);
        return c.newInstance(200000L + id, start, 0, Ids.katana()
            .get(action));
    }

    private int launches;

    /** The production contact path with an injected launcher contact, as P1-a's fixture does. */
    private void launch(EntityPlayerMP attacker, EntityPlayerMP target) throws Exception {
        CombatServer.PlayerState s = state(attacker);
        s.scheduler.interrupt();
        s.scheduler.clearBuffer();
        Field current = Scheduler.class.getDeclaredField("current");
        current.setAccessible(true);
        current.set(s.scheduler, forged("launcher", Clashweave.server.tick, launches++));
        Class<?> type = Class.forName("com.layue13.clashweave.forge.CombatServer$Contact");
        Constructor<?> c = type.getDeclaredConstructor();
        c.setAccessible(true);
        Object hit = c.newInstance();
        Object[][] values = { { "attacker", s }, { "target", target }, { "instance", forged("launcher", Clashweave.server.tick, launches++) },
            { "tick", Clashweave.server.tick }, { "frozen", System.nanoTime() }, { "facing", true } };
        for (Object[] value : values) {
            Field f = type.getDeclaredField((String) value[0]);
            f.setAccessible(true);
            f.set(hit, value[1]);
        }
        Field pending = CombatServer.class.getDeclaredField("pending");
        pending.setAccessible(true);
        ((List) pending.get(Clashweave.server)).add(hit);
        launchedAt = Clashweave.server.tick;
    }

    private int floatPhase, floatWait;
    private float apexY;
    private Snap floatBefore;
    private double lastMotionY;
    private long liftedAt = -1;

    private void floatTick() throws Exception {
        String event = mode.substring("float-".length());
        EntityPlayerMP victim = null;
        for (Object o : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList)
            if (((EntityPlayerMP) o).getCommandSenderName()
                .equals("P0B")) victim = (EntityPlayerMP) o;
        EntityPlayerMP attacker = null;
        for (Object o : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList)
            if (((EntityPlayerMP) o).getCommandSenderName()
                .equals("P0A")) attacker = (EntityPlayerMP) o;
        if (attacker == null || victim == null || state(attacker) == null || state(victim) == null
            || !state(attacker).verified
            || !state(victim).verified) return;
        a = attacker;
        b = victim;
        floatWait++;
        switch (floatPhase) {
            case 0: // stage both players, then launch
                if (floatWait < 60) return;
                a.worldObj.getGameRules()
                    .setOrCreateGameRule("keepInventory", "true");
                b.setGameType(net.minecraft.world.WorldSettings.GameType.SURVIVAL);
                for (EntityPlayerMP p : new EntityPlayerMP[] { a, b }) {
                    p.getEntityAttribute(SharedMonsterAttributes.maxHealth)
                        .setBaseValue(200);
                    p.setHealth(200);
                    p.motionX = p.motionY = p.motionZ = 0;
                }
                a.playerNetServerHandler.setPlayerLocation(0, 64, 0, 0, 0);
                b.playerNetServerHandler.setPlayerLocation(0, 64, 1.8, 180, 0);
                floatPhase = 1;
                floatWait = 0;
                return;
            case 1: // launch the first time
                if (floatWait < 20) return;
                floatBefore = snap(b);
                launch(a, b);
                System.out.println("WF1C_FLOAT launch#1 target=" + b.getEntityId() + " session=" + floatBefore.session);
                floatPhase = 2;
                floatWait = 0;
                return;
            case 2: // wait for the target to be airborne and rising
                if (floatWait > 80) {
                    fail("float-" + event, "target never lifted (motionY " + b.motionY + ")");
                    return;
                }
                if (b.motionY >= .55 && liftedAt < 0) liftedAt = Clashweave.server.tick;
                if (liftedAt >= 0 && Clashweave.server.tick - liftedAt >= 6) {
                    floatBefore = snap(b);
                    System.out.println("WF1C_FLOAT apply event=" + event + " tick=" + Clashweave.server.tick + " before=" + floatBefore + " y=" + b.posY + " motionY=" + b.motionY);
                    if (event.equals("death")) b.attackEntityFrom(DamageSource.outOfWorld, 100000f);
                    else if (event.equals("dimension")) b.travelToDimension(1);
                    else b.playerNetServerHandler.kickPlayerFromServer("wf1c float disconnect");
                    floatPhase = 3;
                    floatWait = 0;
                }
                return;
            case 3: // wait for the new state
                if (floatWait > 600) {
                    fail("float-" + event, "no new state within 600 ticks");
                    return;
                }
                for (Object o : MinecraftServer.getServer()
                    .getConfigurationManager().playerEntityList)
                    if (((EntityPlayerMP) o).getCommandSenderName()
                        .equals("P0B")) b = (EntityPlayerMP) o;
                CombatServer.PlayerState st = state(b);
                if (st != null && st.verified && st.session != floatBefore.session && !b.isDead && floatWait > 20) {
                    Snap s = snap(b);
                    System.out.println("WF1C_FLOAT after event=" + event + " " + s);
                    String problem = null;
                    if (!s.action.equals("-")) problem = "action in new state: " + s.action;
                    else if (s.corrections != 0) problem = "corrections " + s.corrections;
                    else if (st.launchCredit != 0) problem = "launch credit carried over";
                    else if (st.movement != null) problem = "movement budget carried over";
                    if (event.equals("dimension")) b.travelToDimension(0);
                    result("float-" + event + "-state", problem, floatBefore, s);
                    floatPhase = 4;
                    floatWait = 0;
                }
                return;
            case 4: // after settling, put both back and launch again: the budget must be full
                if (floatWait < 160) return;
                a.playerNetServerHandler.setPlayerLocation(0, 64, 0, 0, 0);
                b.playerNetServerHandler.setPlayerLocation(0, 64, 1.8, 180, 0);
                b.motionX = b.motionY = b.motionZ = 0;
                floatBefore = snap(b);
                launch(a, b);
                System.out.println("WF1C_FLOAT launch#2 target=" + b.getEntityId() + " session=" + floatBefore.session);
                floatPhase = 5;
                floatWait = 0;
                return;
            case 5:
                lastMotionY = Math.max(lastMotionY, b.motionY);
                if (floatWait < 40) return;
                Snap s = snap(b);
                result("float-" + event + "-relaunch", lastMotionY >= .55 ? null : "second launch did not lift (max motionY " + lastMotionY + ")", floatBefore, s);
                finish();
                return;
            default:
                return;
        }
    }

    // ---- weapon2 ---------------------------------------------------------------------------------------------

    private final java.util.Set<String> glaiveActions = new java.util.TreeSet<>();
    private final java.util.Map<String, Float> glaiveDamage = new java.util.TreeMap<>();
    private float dummyHealth = -1;
    private String dummyAction = "";

    private void weapon2Tick() {
        CombatServer.PlayerState st = state(a);
        if (st == null || !st.verified) return;
        if (!started) {
            started = true;
            a.inventory.setInventorySlotContents(1, new ItemStack(Items.stick));
            a.inventory.currentItem = 1;
            System.out.println("WF1C_WEAPON2 equipped stick for P0A; B holds katana");
        }
        Scheduler.Instance current = st.scheduler.current();
        if (current != null && current.definition.id.startsWith("wf1test:")) {
            if (glaiveActions.add(current.definition.id)) System.out.println(
                "WF1C_WEAPON2 start action=" + current.definition.id + " tick=" + Clashweave.server.tick + " style=" + st.scheduler.weapon().style + " appearance=" + st.appearance.current().skin + "/" + st.appearance.current().animations);
            dummyAction = current.definition.id;
        }
        if (dummy != null) {
            float now = dummy.getHealth();
            if (dummyHealth >= 0 && now < dummyHealth && !dummyAction.isEmpty()) {
                float delta = dummyHealth - now;
                System.out.println("WF1C_WEAPON2 hit action=" + dummyAction + " damage=" + delta + " tick=" + Clashweave.server.tick);
                glaiveDamage.put(dummyAction, delta);
            }
            dummyHealth = now;
        }
        if (waiting++ > 700) {
            boolean ok = glaiveActions.contains("wf1test:thrust") && glaiveActions.contains("wf1test:thrust_b") && glaiveActions.contains("wf1test:sweep");
            result("weapon2-actions-and-chain", ok ? null : "saw " + glaiveActions, null, snap(a));
            Float thrust = glaiveDamage.get("wf1test:thrust"), sweep = glaiveDamage.get("wf1test:sweep");
            // Vanilla armor is not ours to change: a zombie has 2 natural armor points, 4 percent each.
            float reduction = 1 - Math.min(20, dummy.getTotalArmorValue()) * .04f;
            float expectedThrust = 4 * 1.25f * reduction, expectedSweep = 7 * 1.25f * reduction;
            result("weapon2-damage-multiplier", thrust != null && sweep != null && Math.abs(thrust - expectedThrust) < .02f && Math.abs(sweep - expectedSweep) < .02f ? null : "damage " + glaiveDamage + " expected thrust " + expectedThrust + " sweep " + expectedSweep + " (damage 4 and 7, multiplier 1.25, armor factor " + reduction + ")", null, snap(a));
            finish();
        }
    }

    // ---- driver ----------------------------------------------------------------------------------------------

    private void result(String name, String problem, Snap beforeSnap, Snap afterSnap) {
        if (problem == null) passed++;
        else failed++;
        System.out.println("WF1C_CHECK step=" + name + " ok=" + (problem == null) + " detail=" + (problem == null ? "-" : problem) + " before=[" + (beforeSnap == null ? "-" : beforeSnap) + "] after=[" + (afterSnap == null ? "-" : afterSnap) + "]");
    }

    private void fail(String name, String problem) {
        result(name, problem, floatBefore, b == null ? null : snap(b));
        finish();
    }

    private void finish() {
        if (finished) return;
        finished = true;
        System.out.println("WF1C_COMPLETE mode=" + mode + " passed=" + passed + " failed=" + failed);
        MinecraftServer.getServer()
            .initiateShutdown();
    }

    private void setup() {
        a.worldObj.getGameRules()
            .setOrCreateGameRule("keepInventory", "true");
        a.worldObj.getGameRules()
            .setOrCreateGameRule("doMobSpawning", "false");
        for (EntityPlayerMP p : new EntityPlayerMP[] { a, b }) {
            p.getEntityAttribute(SharedMonsterAttributes.maxHealth)
                .setBaseValue(200);
            p.setHealth(200);
        }
        if (mode.equals("lifecycle") || mode.equals("weapon2")) {
            dummy = new EntityZombie(a.worldObj);
            dummy.getEntityAttribute(SharedMonsterAttributes.maxHealth)
                .setBaseValue(100000);
            dummy.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
                .setBaseValue(0);
            dummy.setHealth(100000);
            dummy.setPosition(0, 64, 2);
            // A pumpkin stops daylight burning without adding armor, so health changes are only our hits.
            dummy.setCurrentItemOrArmor(4, new ItemStack(net.minecraft.init.Blocks.pumpkin));
            a.worldObj.spawnEntityInWorld(dummy);
        }
        a.worldObj.getGameRules()
            .setOrCreateGameRule("doDaylightCycle", "false");
        a.worldObj.setWorldTime(6000);
        a.playerNetServerHandler.setPlayerLocation(0, 64, 0, 0, 0);
        System.out.println("WF1C_SETUP mode=" + mode + " tick=" + Clashweave.server.tick);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (mode.isEmpty() || finished || event.phase != TickEvent.Phase.END) return;
        ticks++;
        try {
            if (mode.startsWith("float-")) {
                floatTick();
                return;
            }
            if (mode.equals("handshake")) {
                // Both clients carry gameplay content the server does not: nobody may stay verified or armed.
                if (ticks == 1500) {
                    int verified = 0, states = 0;
                    for (Object o : MinecraftServer.getServer()
                        .getConfigurationManager().playerEntityList) {
                        CombatServer.PlayerState st = state((EntityPlayerMP) o);
                        if (st != null) states++;
                        if (st != null && st.verified) verified++;
                    }
                    result("handshake-gameplay-diff-refused", verified == 0 ? null : verified + " mismatching client(s) are verified", null, null);
                    System.out.println("WF1C_HANDSHAKE connected=" + MinecraftServer.getServer().getCurrentPlayerCount() + " withState=" + states + " verified=" + verified);
                    finish();
                }
                return;
            }
            for (Object o : MinecraftServer.getServer()
                .getConfigurationManager().playerEntityList) {
                EntityPlayerMP p = (EntityPlayerMP) o;
                if (p.getCommandSenderName()
                    .equals("P0A")) a = p;
                else b = p;
            }
            if (!isSetUp && (a == null || b == null || state(a) == null || !state(a).verified || !state(b).verified
                || ticks < 120)) return;
            if (!isSetUp) {
                isSetUp = true;
                setup();
                if (mode.equals("lifecycle")) buildLifecycle();
                waiting = 0;
                return;
            }
            if (dummy != null && !dummy.isDead && dummy.dimension == a.dimension) {
                dummy.setPosition(0, 64, 2);
                dummy.motionX = dummy.motionY = dummy.motionZ = 0;
                dummy.fallDistance = 0;
                dummy.extinguish();
            }
            CombatServer.PlayerState pinned = state(a);
            if (pinned != null && a.dimension == 0
                && pinned.scheduler.current() == null
                && a.ridingEntity == null
                && a.openContainer == a.inventoryContainer
                && !a.isDead
                && Math.hypot(a.posX, a.posZ) > .4) {
                // Root motion walks the attacker forward; between actions put it back, as the P0 fixtures do.
                a.playerNetServerHandler.setPlayerLocation(0, 64, 0, 0, 0);
            }
            if (mode.equals("weapon2")) {
                weapon2Tick();
                return;
            }
            if (index < 0) {
                index = 0;
                waiting = 0;
                phaseSettle = false;
            }
            if (index >= steps.size()) {
                finish();
                return;
            }
            Step step = steps.get(index);
            if (!phaseSettle) {
                waiting++;
                if (step.ready() && waiting > 5) {
                    before = snap(a);
                    System.out.println("WF1C_STEP begin=" + step.name + " tick=" + Clashweave.server.tick + " before=[" + before + "]");
                    step.act();
                    settleLeft = step.settle;
                    phaseSettle = true;
                } else if (waiting > 900) {
                    result(step.name, "precondition not reached within 900 ticks", before, snap(a));
                    index++;
                    waiting = 0;
                }
            } else if (settleLeft-- <= 0) {
                String problem = step.verify();
                result(step.name, problem, before, snap(a));
                index++;
                waiting = 0;
                phaseSettle = false;
            }
        } catch (Exception failure) {
            throw new IllegalStateException("WF-1c fixture step=" + index, failure);
        }
    }

    private boolean isSetUp, phaseSettle;
}
