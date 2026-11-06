package com.xxsx.earththeology;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xxsx.earthlore.api.Grade;
import com.xxsx.earthlore.core.CarrierForms;
import com.xxsx.earthlore.core.Gate;
import com.xxsx.earthlore.core.GraphValidator;
import com.xxsx.earthlore.core.NodeDef;
import com.xxsx.earthlore.core.Prereq;
import com.xxsx.earthlore.core.Reachability;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TheologyLoreIntegrationTest {
    private static final Set<String> DISCIPLINES = Set.of("earth_lore:the", "earth_lore:med", "earth_lore:xua");
    private static final GraphValidator.DependencyRule STANDALONE_DEPS =
            (owner, other) -> owner.equals(other) || "earth_lore".equals(other);

    @Test
    void allSeventeenTheologyNodesValidateAndAreReachableStandalone() throws IOException {
        List<NodeDef> nodes = loadTheologyNodes();
        assertEquals(17, nodes.size(), "Expected 17 K-THE nodes");

        GraphValidator.Result validated = GraphValidator.validate(nodes, DISCIPLINES, STANDALONE_DEPS);
        assertTrue(validated.rejected().isEmpty(), () -> "Rejected nodes: " + validated.rejected());
        assertEquals(17, validated.accepted().size());

        Reachability.Report reach = Reachability.compute(validated.accepted());
        assertTrue(reach.unreachable().isEmpty(), () -> "Unreachable nodes: " + reach.unreachable());
        assertEquals(17, reach.learnable().size());
        assertEquals(2, reach.altUsed(), "K-THE-08 and K-THE-13 should use their self-taught theology fallbacks standalone");
    }

    @Test
    void carrierFormsResolveBlankScrollOnlyForTheology() throws IOException {
        Path file = Path.of("src/main/resources/data/earth_theology/earth_lore/carrier_forms.json");
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        List<CarrierForms.Decl> decls = new ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("forms")) {
            JsonObject obj = el.getAsJsonObject();
            String carrier = obj.get("carrier").getAsString();
            String result = obj.get("result").getAsString();
            String discipline = obj.has("discipline") ? obj.get("discipline").getAsString() : null;
            Set<String> nodes = new LinkedHashSet<>();
            if (obj.has("nodes")) {
                obj.getAsJsonArray("nodes").forEach(n -> nodes.add(n.getAsString()));
            }
            decls.add(new CarrierForms.Decl(carrier, result, discipline, nodes));
        }

        assertEquals("earth_theology:scripture_scroll",
                CarrierForms.resolve(decls, "earth_theology:blank_scroll", "earth_lore:the",
                        List.of("earth_theology:the/prayer")).orElseThrow());
        assertTrue(CarrierForms.resolve(decls, "earth_theology:blank_scroll", "earth_lore:mag",
                List.of("fantasy_earth:mag/alchemy")).isEmpty());
    }

    @Test
    void sanctumScanDesecrationHolyWaterOfferingHealAndExorcismMatchSpec() {
        // earth_theo_sanctum_scan: altar(15) + 5 lit candles(10) + 2 scroll lecterns(8) = 33
        assertEquals(33, TheologyMath.computeSanctumValue(1, 5, 0, 2, 0, 0, 0, 0, false, false, 0.0D));
        // earth_theo_desecration: villager killed (-10) -> 23; exorcism clears desecration -> 33
        assertEquals(23, TheologyMath.computeSanctumValue(1, 5, 0, 2, 0, 0, 0, 0, false, false, 10.0D));
        // earth_theo_temple_form: temple bonus +25
        assertEquals(58, TheologyMath.computeSanctumValue(1, 5, 0, 2, 0, 0, 0, 0, false, true, 0.0D));

        // earth_theo_holy_water_grade: sanctum 45 -> fine if knows K-THE-06, common if not
        assertEquals(Grade.FINE, TheologyMath.resolveHolyWaterGrade(45, true, false));
        assertEquals(Grade.COMMON, TheologyMath.resolveHolyWaterGrade(45, false, false));
        assertEquals(Grade.MASTERWORK, TheologyMath.resolveHolyWaterGrade(85, true, false));
        assertEquals(Grade.HEIRLOOM, TheologyMath.resolveHolyWaterGrade(85, true, true));

        // earth_theo_offering_daily: two trays on ordinary day cap at 3 total piety
        int firstGain = TheologyMath.computeOfferingPietyGain(2, 0, false, true);
        int secondGain = TheologyMath.computeOfferingPietyGain(3, firstGain, false, true);
        assertEquals(2, firstGain);
        assertEquals(1, secondGain);
        assertEquals(3, firstGain + secondGain);

        // earth_theo_exorcism: common holy water -> 8.0 magic damage
        assertEquals(8.0F, TheologyMath.computeExorcismDamage(Grade.COMMON), 1e-5F);
        // earth_theo_heal: piety 20, common holy water -> (4 + 20/10) * 1.0 = 6.0 HP
        assertEquals(6.0F, TheologyMath.computeHealingPrayerAmount(20, Grade.COMMON), 1e-5F);
        // earth_theo_pilgrimage: horizontal distance >= 512
        assertFalse(TheologyMath.meetsPilgrimDistance(0, 0, 300, 300));
        assertTrue(TheologyMath.meetsPilgrimDistance(0, 0, 512, 0));
    }

    private static List<NodeDef> loadTheologyNodes() throws IOException {
        Path dir = Path.of("src/main/resources/data/earth_theology/earth_lore/knowledge_node/the");
        List<NodeDef> list = new ArrayList<>();
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String slug = file.getFileName().toString().replace(".json", "");
                String id = "earth_theology:the/" + slug;
                JsonObject obj = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                String discipline = obj.get("discipline").getAsString();
                int tier = obj.get("tier").getAsInt();
                Gate gate = Gate.parse(obj.get("gate").getAsString()).orElseThrow();
                List<Prereq> requires = new ArrayList<>();
                for (JsonElement reqEl : obj.getAsJsonArray("requires")) {
                    JsonObject reqObj = reqEl.getAsJsonObject();
                    if (reqObj.has("all")) {
                        requires.add(new Prereq.All(reqObj.get("all").getAsString()));
                    } else if (reqObj.has("any")) {
                        List<String> members = new ArrayList<>();
                        JsonArray arr = reqObj.getAsJsonArray("any");
                        arr.forEach(m -> members.add(m.getAsString()));
                        requires.add(new Prereq.Any(members));
                    }
                }
                List<String> unlockKeys = new ArrayList<>();
                for (JsonElement uEl : obj.getAsJsonArray("unlocks")) {
                    JsonObject uObj = uEl.getAsJsonObject();
                    Map.Entry<String, JsonElement> entry = uObj.entrySet().iterator().next();
                    String kind = entry.getKey();
                    String val = entry.getValue().getAsString();
                    unlockKeys.add("capstone".equals(kind) ? ("capstone:" + val + "@earth_theology") : (kind + ":" + val));
                }
                list.add(new NodeDef(id, discipline, tier, gate, requires, unlockKeys, false));
            }
        }
        return list;
    }
}

