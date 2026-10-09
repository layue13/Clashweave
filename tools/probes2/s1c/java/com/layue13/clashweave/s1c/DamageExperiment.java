package com.layue13.clashweave.s1c;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

@Mod(modid = "cw_s1c_experiment", name = "Clashweave S1c experiment", version = "round2")
public final class DamageExperiment {
    private static final DamageSource SOURCE = new DamageSource("clashweave.s1c");
    private final EventGate gate = new EventGate();
    private EntityLivingBase observed;
    private String scenario;
    private String path;
    private boolean nestedAccepted;
    private float nestedLoss;
    private int nestedCalls;
    private int failures;
    private int pairs;

    @Mod.EventHandler
    public void started(FMLServerStartedEvent event) {
        MinecraftServer server = MinecraftServer.getServer();
        try {
            WorldServer world = server.worldServerForDimension(0);
            pair(world, "attack-normal", false, true);
            pair(world, "hurt-high", false, true);
            pair(world, "hurt-low", false, true);
            pair(world, "attack-lowest-before", false, true);
            pair(world, "attack-lowest-after", false, false);
            pair(world, "hurt-highest-before", false, true);
            pair(world, "hurt-highest-after", false, false);
            pair(world, "player-attack", true, true);
            pair(world, "player-hurt", true, true);
            for (String name : new String[] { "cancel-attack", "cancel-hurt", "zero", "already-dead", "lethal", "throw-attack", "throw-hurt", "throw-attack-after-clear", "throw-hurt-before-restore" }) boundary(world, name);
            ledger(world);
            System.out.println("CW2 S1C_RESULT pairs=" + pairs + " mismatches=" + failures + " frameDepth=" + gate.depth() + " ledger=" + gate.ledgerSize() + " verdict=" + (failures == 0 ? "PASS" : "FAIL"));
        } finally { unregister(); observed = null; server.initiateShutdown(); }
    }

    private EntityLivingBase prepared(WorldServer world, boolean player) {
        EntityLivingBase entity = player
            ? new EntityPlayerMP(MinecraftServer.getServer(), world, new GameProfile(UUID.randomUUID(), "S1cFixture"), new ItemInWorldManager(world))
            : new EntityCow(world);
        if (player) cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(EntityPlayerMP.class, (EntityPlayerMP) entity, Integer.valueOf(0), "field_147101_bU");
        entity.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(100);
        entity.setHealth(100);
        // Exact shared baseline from real vanilla damage, not a copied formula.
        if (!entity.attackEntityFrom(DamageSource.generic, 4)) throw new IllegalStateException("Seed rejected");
        for (int i = 0; i < 7; i++) {
            entity.onEntityUpdate();
            // MP's normal onUpdate separately decrements this field; fixture is not logged in.
            if (player) entity.hurtResistantTime--;
        }
        if (entity.getHealth() != 96 || entity.hurtResistantTime != 13 || EventGate.last(entity) != 4) throw new IllegalStateException("Unexpected baseline");
        return entity;
    }
    private void unregister() {
        MinecraftForge.EVENT_BUS.unregister(this);
        MinecraftForge.EVENT_BUS.unregister(gate);
    }
    private void register(boolean callbackBeforeGate) {
        unregister();
        if (callbackBeforeGate) { MinecraftForge.EVENT_BUS.register(this); MinecraftForge.EVENT_BUS.register(gate); }
        else { MinecraftForge.EVENT_BUS.register(gate); MinecraftForge.EVENT_BUS.register(this); }
    }
    private void pair(WorldServer world, String name, boolean player, boolean before) {
        register(before);
        scenario = name;
        EntityLivingBase control = prepared(world, player);
        EntityLivingBase candidate = prepared(world, player);
        // A vanilla stronger-hit differential (6 - existing lastDamage 4 = 2)
        // reaches LivingHurtEvent with the same old gate as the candidate's 2.
        run(control, false, name.contains("hurt") ? 6 : 2);
        boolean acceptedControl = nestedAccepted;
        float lossControl = nestedLoss;
        int callsControl = nestedCalls;
        run(candidate, true, 2);
        boolean equivalent = acceptedControl == nestedAccepted && lossControl == nestedLoss;
        if (!equivalent) failures++;
        pairs++;
        System.out.println("CW2 S1C_PAIR scenario=" + name + " listenerRegisteredBeforeGate=" + before + " controlNested=" + acceptedControl + " candidateNested=" + nestedAccepted + " controlLoss=" + lossControl + " candidateLoss=" + nestedLoss + " callbacks=" + callsControl + "," + nestedCalls + " equivalent=" + equivalent);
    }
    private void run(EntityLivingBase entity, boolean candidate, float amount) {
        observed = entity; path = candidate ? "candidate" : "control";
        nestedAccepted = false; nestedLoss = 0; nestedCalls = 0;
        boolean accepted = candidate ? gate.apply(entity, SOURCE, amount) : entity.attackEntityFrom(SOURCE, amount);
        System.out.println("CW2 S1C_OUTER scenario=" + scenario + " path=" + path + " accepted=" + accepted + " hp=" + entity.getHealth() + " timer=" + entity.hurtResistantTime + " last=" + EventGate.last(entity));
        observed = null;
    }
    private void nested(String where) {
        int timer = observed.hurtResistantTime; float last = EventGate.last(observed), hp = observed.getHealth();
        // Fresh source object deliberately does not equal registered SOURCE.
        boolean result = observed.attackEntityFrom(new DamageSource("other.s1c"), 4);
        float loss = hp - observed.getHealth();
        nestedAccepted |= result; nestedLoss += loss; nestedCalls++;
        System.out.println("CW2 S1C_NESTED scenario=" + scenario + " path=" + path + " at=" + where + " timer=" + timer + " last=" + last + " accepted=" + result + " loss=" + loss);
    }
    private boolean watched(EntityLivingBase entity, DamageSource source) { return entity == observed && source == SOURCE; }
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void attackNormal(LivingAttackEvent e) {
        if (!watched(e.entityLiving, e.source)) return;
        if (scenario.equals("attack-normal") || scenario.equals("player-attack")) nested("attack-NORMAL");
        if (scenario.equals("cancel-attack")) e.setCanceled(true);
        if (scenario.equals("throw-attack")) throw new FixtureException();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void attackLowest(LivingAttackEvent e) {
        if (!watched(e.entityLiving, e.source)) return;
        if (scenario.startsWith("attack-lowest")) nested("attack-LOWEST");
        if (scenario.equals("throw-attack-after-clear")) {
            System.out.println("CW2 S1C_THROW at=attack-LOWEST timer=" + observed.hurtResistantTime + " last=" + EventGate.last(observed));
            throw new FixtureException();
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void hurtHighest(LivingHurtEvent e) {
        if (watched(e.entityLiving, e.source) && scenario.startsWith("hurt-highest")) nested("hurt-HIGHEST");
        if (watched(e.entityLiving, e.source) && scenario.equals("throw-hurt-before-restore")) {
            System.out.println("CW2 S1C_THROW at=hurt-HIGHEST timer=" + observed.hurtResistantTime + " last=" + EventGate.last(observed));
            throw new FixtureException();
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void hurtHigh(LivingHurtEvent e) {
        if (!watched(e.entityLiving, e.source)) return;
        if (scenario.equals("hurt-high") || scenario.equals("player-hurt")) nested("hurt-HIGH");
        if (scenario.equals("cancel-hurt")) e.setCanceled(true);
        if (scenario.equals("throw-hurt")) throw new FixtureException();
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public void hurtLow(LivingHurtEvent e) {
        if (watched(e.entityLiving, e.source) && scenario.equals("hurt-low")) nested("hurt-LOW");
    }
    public static final class FixtureException extends RuntimeException { private static final long serialVersionUID = 1L; }
    private void boundary(WorldServer world, String name) {
        register(name.equals("throw-hurt-before-restore")); scenario = name;
        EntityLivingBase target = prepared(world, false);
        if (name.equals("already-dead")) target.setHealth(0);
        if (name.equals("lethal")) target.setHealth(1);
        observed = target; path = "candidate";
        float hp = target.getHealth(); boolean threw = false, accepted = false;
        try { accepted = gate.apply(target, SOURCE, name.equals("zero") ? 0 : 2); }
        catch (FixtureException e) { threw = true; }
        finally { observed = null; }
        float loss = hp - target.getHealth();
        boolean restored = target.hurtResistantTime == 13 && EventGate.last(target) == 4 && gate.depth() == 0;
        boolean expectedLoss = loss == (name.equals("lethal") ? 1 : 0);
        if (!restored || !expectedLoss) failures++;
        System.out.println("CW2 S1C_BOUNDARY scenario=" + name + " accepted=" + accepted + " threw=" + threw + " loss=" + loss + " hp=" + target.getHealth() + " deadFlag=" + target.isDead + " timer=" + target.hurtResistantTime + " last=" + EventGate.last(target) + " restored=" + restored + " noExtraLoss=" + expectedLoss);
    }
    private void ledger(WorldServer world) {
        unregister(); MinecraftForge.EVENT_BUS.register(gate);
        EntityLivingBase target = prepared(world, false);
        boolean first = gate.hit("a", target, 0, SOURCE, 2);
        boolean duplicate = gate.hit("a", target, 0, SOURCE, 2);
        boolean second = gate.hit("a", target, 1, SOURCE, 2);
        int before = gate.ledgerSize(); gate.end("a");
        int after = gate.ledgerSize();
        boolean next = gate.hit("b", target, 0, SOURCE, 2); gate.end("b");
        if (!first || duplicate || !second || before != 2 || after != 0 || !next || gate.ledgerSize() != 0) failures++;
        System.out.println("CW2 S1C_LEDGER first=" + first + " duplicate=" + duplicate + " secondSegment=" + second + " beforeEnd=" + before + " afterEnd=" + after + " nextAction=" + next + " final=" + gate.ledgerSize());
    }
}
