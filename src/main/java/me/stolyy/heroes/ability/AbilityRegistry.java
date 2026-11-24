package me.stolyy.heroes.ability;

import me.stolyy.heroes.hero.HeroBuilder;
import me.stolyy.heroes.ability.kit.blud.*;
import me.stolyy.heroes.ability.kit.botmon.*;
import me.stolyy.heroes.ability.kit.bug.*;
import me.stolyy.heroes.ability.kit.bulk.*;
import me.stolyy.heroes.ability.kit.cakemonster.*;
import me.stolyy.heroes.ability.kit.cryomancer.*;
import me.stolyy.heroes.ability.kit.greenhood.*;
import me.stolyy.heroes.ability.kit.mrmonster.*;
import me.stolyy.heroes.ability.kit.karakot.*;
import me.stolyy.heroes.ability.kit.marauder.*;
import me.stolyy.heroes.ability.kit.minions.basher.Bash;
import me.stolyy.heroes.ability.kit.minions.princess.Sing;
import me.stolyy.heroes.ability.kit.pochita.*;
import me.stolyy.heroes.ability.kit.pug.*;
import me.stolyy.heroes.ability.kit.punishgers.*;
import me.stolyy.heroes.ability.kit.railgunner.*;
import me.stolyy.heroes.ability.kit.sgt.*;
import me.stolyy.heroes.ability.kit.shoop.*;
import me.stolyy.heroes.ability.kit.skullfire.*;
import me.stolyy.heroes.ability.kit.spooderman.*;
import me.stolyy.heroes.ability.kit.tinman.*;
import me.stolyy.heroes.ability.kit.voidcrawler.*;
import me.stolyy.heroes.ability.template.Tether;

public final class AbilityRegistry {

    private AbilityRegistry() {}

    /**
     * THE canonical ability registry — a HARDCODED list. There is no jar-scanning or
     * annotation magic: an ability class that isn't registered here does not exist as far
     * as hero ymls are concerned.
     *
     * <p><b>Failure mode:</b> a yml referencing an unregistered {@code type:} key fails
     * loudly in {@link HeroBuilder} (logged at SEVERE and omitted from the hero so it cannot
     * be instantiated).
     *
     * <p><b>Adding a new ability:</b>
     * <ol>
     *   <li>Create the class under {@code ability/kit/<hero>/} extending a template.</li>
     *   <li>Add {@code HeroBuilder.registerAbility("<key>", ClassName::new);} below.</li>
     *   <li>Reference it from the hero's yml: {@code type: <key>} (keys are lowercased).</li>
     * </ol>
     */
    public static void registerAll() {
        // --- Blud ---
        HeroBuilder.registerAbility("bludprimary", BludPrimary::new);
        HeroBuilder.registerAbility("bludswap", BludSwap::new);

        // --- Botmon ---
        HeroBuilder.registerAbility("botclaw", Botclaw::new);
        HeroBuilder.registerAbility("botrang", Botrang::new);
        HeroBuilder.registerAbility("botmobile", Botmobile::new);

        // --- Bug ---
        HeroBuilder.registerAbility("bugdash", BugDash::new);
        HeroBuilder.registerAbility("bugspell", BugSpell::new);
        HeroBuilder.registerAbility("bugvoidbeast", BugVoidBeast::new);
        HeroBuilder.registerAbility("bugcharms", BugCharms::new);

        // --- Bulk ---
        HeroBuilder.registerAbility("bulkcar", BulkCar::new);
        HeroBuilder.registerAbility("bulkdash", BulkDash::new);
        HeroBuilder.registerAbility("bulksmash", BulkSmash::new);
        HeroBuilder.registerAbility("bulkslam", BulkSlam::new);

        // --- CakeMonster ---
        HeroBuilder.registerAbility("rollingpin", RollingPin::new);
        HeroBuilder.registerAbility("cakestorm", CakeStorm::new);
        HeroBuilder.registerAbility("regurgicake", Regurgicake::new);
        HeroBuilder.registerAbility("goodbaker", GoodBaker::new);

        // --- Cryomancer ---
        HeroBuilder.registerAbility("cryomancerfrostbolt", CryomancerFrostbolt::new);
        HeroBuilder.registerAbility("cryomancerfrostbreath", CryomancerFrostbreath::new);
        HeroBuilder.registerAbility("cryomancerultimate", CryomancerUltimate::new);
        HeroBuilder.registerAbility("cryomancertimewarp", CryomancerTimeWarp::new);
        HeroBuilder.registerAbility("cryomancerfrostaura", CryomancerFrostAura::new);

        // --- Green Hood ---
        HeroBuilder.registerAbility("arrow", GreenHoodBow::new);
        HeroBuilder.registerAbility("ballista", Ballista::new);
        HeroBuilder.registerAbility("tripminearrow", TripmineArrow::new);
        HeroBuilder.registerAbility("pearlarrow", PearlArrow::new);
        HeroBuilder.registerAbility("deathsgrasp", DeathsGrasp::new);
        HeroBuilder.registerAbility("skyfall", SkyfallVolley::new);

        // Mr. Monster
        HeroBuilder.registerAbility("monsterdash", MonsterDash::new);
        HeroBuilder.registerAbility("monsterslam", MonsterSlam::new);
        HeroBuilder.registerAbility("monstergrab", MonsterGrab::new);
        HeroBuilder.registerAbility("monsterrage", MonsterRage::new);

        // --- Karakot ---
        HeroBuilder.registerAbility("kiblast", KiBlast::new);
        HeroBuilder.registerAbility("kamedo", KameDo::new);
        HeroBuilder.registerAbility("karakotultimate", Ultimate::new);
        HeroBuilder.registerAbility("karakotteleport", Teleport::new);

        // --- Marauder ---
        HeroBuilder.registerAbility("marauderlightning", MarauderLightning::new);
        HeroBuilder.registerAbility("maraudergrip", MarauderGrip::new);
        HeroBuilder.registerAbility("marauderultimate", MarauderUltimate::new);
        HeroBuilder.registerAbility("maraudersaberthrow", MarauderSaberThrow::new);

        // --- Pochita ---
        HeroBuilder.registerAbility("chainrev", ChainRev::new);
        HeroBuilder.registerAbility("chainslash", ChainSlash::new);
        HeroBuilder.registerAbility("chainhook", ChainHook::new);
        HeroBuilder.registerAbility("heroofhell", HeroOfHell::new);

        // --- Pug ---
        HeroBuilder.registerAbility("pugdash", PugDash::new);
        HeroBuilder.registerAbility("pugbork", PugBork::new);
        HeroBuilder.registerAbility("pugintruders", PugIntruders::new);
        HeroBuilder.registerAbility("pugpounce", PugPounce::new);

        // --- Punishgers ---
        HeroBuilder.registerAbility("punishgersweapon", PunishgersWeapon::new);
        HeroBuilder.registerAbility("punishgerssecondary", PunishgersSecondary::new);
        HeroBuilder.registerAbility("punishgersswap", PunishgersSwap::new);
        HeroBuilder.registerAbility("executioner", Executioner::new);

        // --- Railgunner ---
        HeroBuilder.registerAbility("railgun", Railgun::new);
        HeroBuilder.registerAbility("satchel", Satchel::new);
        HeroBuilder.registerAbility("clusterbuck", Clusterbuck::new);

        // --- Sgt Shield ---
        HeroBuilder.registerAbility("parry", Parry::new);
        HeroBuilder.registerAbility("shieldbash", ShieldBash::new);
        HeroBuilder.registerAbility("ricochet", Ricochet::new);
        HeroBuilder.registerAbility("shieldofjustice", ShieldOfJustice::new);

        // --- Shoop ---
        HeroBuilder.registerAbility("shoopprimary", ShoopPrimary::new);
        HeroBuilder.registerAbility("shoopsecondary", ShoopSecondary::new);
        HeroBuilder.registerAbility("shoopsmash", ShoopSmash::new);
        HeroBuilder.registerAbility("shooprtl", ShoopRTL::new);

        // --- Skullfire ---
        HeroBuilder.registerAbility("skullfiredeagle", SkullfireDeagle::new);
        HeroBuilder.registerAbility("skullfiregrenade", SkullfireGrenade::new);
        HeroBuilder.registerAbility("skullfireblazingdeagle", SkullfireBlazingDeagle::new);
        HeroBuilder.registerAbility("skullfirebullseye", SkullfireBullseye::new);

        // --- Spooderman ---
        HeroBuilder.registerAbility("spooderdash", SpooderDash::new);
        HeroBuilder.registerAbility("spooderweb", SpooderWeb::new);
        HeroBuilder.registerAbility("spooderclimb", SpooderClimb::new);
        HeroBuilder.registerAbility("spooderfriends", SpooderFriends::new);
        HeroBuilder.registerAbility("cavespooderman", CaveSpooderman::new);

        // --- Tinman ---
        HeroBuilder.registerAbility("tinlaser", TinLaser::new);
        HeroBuilder.registerAbility("rocketpunch", RocketPunch::new);
        HeroBuilder.registerAbility("nukeseekingmissiles", NukeSeekingMissiles::new);
        HeroBuilder.registerAbility("overload", Overload::new);

        // --- VoidCrawler ---
        HeroBuilder.registerAbility("voiddash", VoidDash::new);
        HeroBuilder.registerAbility("voidteleboom", VoidTeleboom::new);
        HeroBuilder.registerAbility("voidshadowfury", VoidShadowFury::new);
        HeroBuilder.registerAbility("voidenergy", VoidEnergy::new);

        // --- Minions ---
        HeroBuilder.registerAbility("bash", Bash::new);
        HeroBuilder.registerAbility("sing", Sing::new);
        HeroBuilder.registerAbility("spooderbuddyclimb", SpooderClimb::new);
        HeroBuilder.registerAbility("tether", Tether::new);

        // --- Mobility ---
        HeroBuilder.registerMobility("skullmobility", (stats, config, skin) -> new SkullMobility(stats, config));
        HeroBuilder.registerMobility("botglide", (stats, config, skin) -> new Botglide(stats, config));
        HeroBuilder.registerMobility("defecake", Defecake::new);
        HeroBuilder.registerMobility("marauderdarkjump", (stats, config, skin) -> new MarauderDarkJump(stats, config));

        GreenHoodListener.initialize();
        MonsterGrab.initialize();
    }
}
