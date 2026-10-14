package com.layue13.clashweave.content;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.List;

import org.junit.Test;

import com.layue13.clashweave.core.HitEffect;
import com.layue13.clashweave.core.WeaponDefinition;

public class SaTest {

    private static EffectiveRules rules() throws Exception {
        ContentRegistry registry = ContentLoader.load(SaTest.class.getClassLoader(), null).registry;
        return EffectiveRules.build(registry, new EffectiveRules.Overrides(true, true, 1.2, false), null);
    }

    @Test
    public void theEquippedAbilityAttachesItsStrikeToTheIaiAndNothingElse() throws Exception {
        EffectiveRules rules = rules();
        WeaponDefinition katana = rules.weapon("katana:katana");
        assertEquals(java.util.Arrays.asList("katana:afterimage"), katana.saSlots);
        List<HitEffect> iai = rules.attached(katana, "katana:iai");
        assertEquals(1, iai.size());
        assertEquals(HitEffect.Type.DELAYED_SLASH, iai.get(0).type);
        assertEquals(6, iai.get(0).delay);
        assertTrue(
            rules.attached(katana, "katana:light_1")
                .isEmpty());
        assertTrue(
            rules.attached(katana, "katana:heavy")
                .isEmpty());
    }

    @Test
    public void aWeaponWithoutTheAbilityHasNoAttachments() throws Exception {
        EffectiveRules rules = rules();
        WeaponDefinition plain = WeaponDefinition.katana();
        assertTrue(
            rules.attached(plain, "katana:iai")
                .isEmpty());
    }

    private static HitEffect effect(String json) {
        return new com.google.gson.Gson().fromJson(json, HitEffect.class);
    }

    @Test
    public void aDelayedSlashNeedsAPlausibleDelayAndNoLift() {
        effect("{\"type\":\"DELAYED_SLASH\",\"delay\":6,\"damage\":3,\"posture\":10}").validate(1.2);
        for (String bad : new String[] { "{\"type\":\"DELAYED_SLASH\",\"delay\":0,\"damage\":3}",
            "{\"type\":\"DELAYED_SLASH\",\"delay\":61,\"damage\":3}",
            "{\"type\":\"DELAYED_SLASH\",\"delay\":6,\"damage\":-1}",
            "{\"type\":\"DELAYED_SLASH\",\"delay\":6,\"damage\":3,\"vy\":0.4}",
            "{\"type\":\"LAUNCH\",\"vy\":0.4,\"delay\":6}" }) {
            try {
                effect(bad).validate(1.2);
                fail("accepted: " + bad);
            } catch (IllegalArgumentException expected) {
                assertNotNull(expected.getMessage());
            }
        }
    }

    @Test
    public void anAbilityOnAnUnknownActionIsRejectedAtLoad() {
        String pack = "{\"schemaVersion\":1,\"namespace\":\"t\",\"sas\":[{\"id\":\"t:x\",\"attach\":[{\"action\":\"t:nothing\","
            + "\"effects\":[{\"type\":\"DELAYED_SLASH\",\"delay\":4,\"damage\":1}]}]}]}";
        try {
            ContentRegistry.load(new ContentRegistry.Source("t.json", new StringReader(pack)));
            fail("accepted an ability on a missing action");
        } catch (ContentValidationException expected) {
            assertTrue(
                expected.errors()
                    .toString(),
                expected.errors()
                    .toString()
                    .contains("unknown action"));
        }
    }
}
