package me.stolyy.heroes.heros.characters;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.*;
import me.stolyy.heroes.heros.abilities.*;
import me.stolyy.heroes.heros.abilities.interfaces.Cone;
import me.stolyy.heroes.heros.abilities.interfaces.Dash;
import me.stolyy.heroes.heros.abilities.interfaces.Projectile;
import me.stolyy.heroes.heros.abilities.interfaces.Shockwave;
import me.stolyy.heroes.heros.abilities.data.*;
import me.stolyy.heroes.heros.minions.SpooderEntity;
import me.stolyy.heroes.utility.Interactions;
import me.stolyy.heroes.utility.effects.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.*;

public class Bug extends HeroEnergy implements Dash, Projectile, Cone, Shockwave {
    public static int CHARM_NOTCHES = 9;

    private final Map<Player, Boolean> diveHitPlayers = new HashMap<>();
    private DashData dive;
    private ShockwaveData diveShockwave;
    private ProjectileData spirit;
    private ConeData shriek;
    private Set<Charms> charms;
    private double soulsPerHit;
    private double soulsPerCast;


    public Bug(Player player){
        super(player);
    }

    @Override
    public void usePrimaryAbility() {
        if(!primary.ready()) return;
        dash(player, AbilityType.PRIMARY, (DashData) primary.abilityData());
        cooldown(primary);
    }

    @Override
    public void onDashHit(Player target, Location location, AbilityType abilityType) {
        if(abilityType == AbilityType.PRIMARY) {
            Interactions.handleInteraction(player.getEyeLocation().getDirection(), primary.dmg(), primary.kb(), player, target);
            Sounds.playSoundToWorld(player, "melee.sword.meleehit", 1.0f, 1.0f);
        } else {
            diveHitPlayers.put(target, true);
            Interactions.handleInteraction(player.getEyeLocation().getDirection(), secondary.dmg(), secondary.kb(), player, target);
            Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> diveHitPlayers.remove(target), 10L);
        }
    }

    @Override
    public void onDashEnd(Location location, AbilityType abilityType){
        if(abilityType != AbilityType.SECONDARY) return;
        if(player.isOnGround()){
            shockwave(player, AbilityType.SECONDARY, diveShockwave);
        }
    }

    @Override
    public void useSecondaryAbility() {
        double pitch = player.getLocation().getPitch();
        if(!secondary.ready()) return;
        if(energy() < soulsPerCast) {
            player.sendMessage(Component.text("Not enough souls! ", NamedTextColor.RED)
                    .append(Component.text((int) (soulsPerCast - energy()) ,NamedTextColor.WHITE))
                    .append(Component.text(" more needed." , NamedTextColor.RED)));
            return;
        }

        if(Math.abs(pitch) < 45) {
            //neutral: spirit
            projectile(player, AbilityType.SECONDARY, spirit);
        } else if(pitch > 45) {
            //shockwave logic
            dash(player, AbilityType.SECONDARY, dive);
        } else{
            //up: shriek
            cone(player, AbilityType.SECONDARY, shriek);
        }
        cooldown(secondary);
        subtractEnergy(soulsPerCast);
    }

    @Override
    public void onProjectileHit(Player target, Location location, AbilityType abilityType) {
        Sounds.playSoundToPlayer(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        Interactions.handleInteraction(secondary.dmg(), secondary.kb(), player, target);
    }

    @Override
    public void onShockwaveHit(Player target, AbilityType abilityType) {
        if(diveHitPlayers.get(target)) return;
        Sounds.playSoundToPlayer(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        Interactions.handleInteraction(secondary.dmg(), secondary.kb(), player, target);
    }

    @Override
    public void onConeHit(Player target, AbilityType abilityType) {
        Sounds.playSoundToPlayer(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        Interactions.handleInteraction(secondary.dmg(), secondary.kb(), player, target);
    }

    @Override
    public void useUltimateAbility() {
        if(!ultimate.ready() || ultimate.inUse()) {
            player.sendMessage(
                    Component.text("Ultimate ability is on cooldown! ", NamedTextColor.YELLOW)
                            .append(Component.text((int) ultimate.timeUntilUse(), NamedTextColor.WHITE))
                            .append(Component.text(" seconds remaining.", NamedTextColor.YELLOW)));
            return;
        }

        ultimate.setInUse(true);
        ultTimer();
        Set<Charms> oldCharms = new HashSet<>(charms);
        charms.addAll(EnumSet.allOf(Charms.class));
        registerCharms();

        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            charms = oldCharms;
            registerCharms();
            cooldown(ultimate);
        },  (long) (ultimate.duration() * 20L));
    }

    @Override
    public void onPunch(){
        super.onPunch();
        addEnergy(soulsPerHit);
    }

    public void onHit(){
        if(charms.contains(Charms.GRUBSONG)) addEnergy(soulsPerHit);
    }



    @Override
    protected void stats() {
        setHeroType(HeroType.HYBRID);
        setWeight(1.5);

        primary = new Ability(AbilityType.PRIMARY, 5, 2.5, 2, new DashData());

        secondary = new Ability(AbilityType.SECONDARY, 7, 3, 0.5);
        dive = new DashData(25, 5);
        diveShockwave = new ShockwaveData(4, Particle.DUST).setDustOptions(Color.GRAY, 3.0f);
        spirit = new ProjectileData(3, 2.5, 50);
        shriek = new ConeData(3.5, 4.5, Particle.DUST).setDustOptions(Color.GRAY, 3.0f);
        ultimate = new Ability(AbilityType.ULTIMATE, 0, 0, 90, 10);

        setEnergyStats(0, 100, 0, false);
        initializeEnergyUpdates();

        charms = new HashSet<>(HeroManager.getCharms(player));
        registerCharms();
    }


    private void registerCharms() {
        primary.setCd(2.0).setKb(2.5).setDmg(5.0);
        ((DashData) primary.abilityData()).setDistance(7).setSpeed(3);

        secondary.setKb(3.0).setDmg(7.0);
        diveShockwave.setRadius(4.0);
        spirit.setRadius(2.5);
        shriek.setRadius(3.5).setLength(4.5);

        setJabCooldown(0.5);
        setJabReach(5.0);
        setJabDamage(4.0);

        soulsPerHit = 11.5;
        soulsPerCast = 33.0;
        setEnergyPerSecond(0);
        setCanIncreaseEnergy(false);

        Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)).setBaseValue(.14);
        setWeight(1.5);

        if (charms.contains(Charms.DASHMASTER)) {
            primary.setCd(1.5);
        }
        //Grub song
        if (charms.contains(Charms.HEAVY_BLOW)) {
            primary.setKb(primary.kb() * 1.2);
            secondary.setKb(secondary.kb() * 1.2);
        }
        if (charms.contains(Charms.KINGSOUL)) {
            setEnergyPerSecond(3);
            setCanIncreaseEnergy(true);
        }
        if (charms.contains(Charms.MARK_OF_PRIDE)) {
            setJabReach(jabReach() + 1.5);
        }
        //Nail Master
        if (charms.contains(Charms.QUICK_SLASH)) {
            setJabCooldown(0.333);
        }
        if (charms.contains(Charms.SHAMAN_STONE)) {
            secondary.setDmg(secondary.dmg() * 1.2);
            diveShockwave.setRadius(diveShockwave.radius() * 1.35);
            spirit.setRadius(spirit.radius() * 1.35);
            shriek.setRadius(shriek.radius() * 1.35).setLength(shriek.length() * 1.35);
        }
        if (charms.contains(Charms.SHARP_SHADOW)) {
            primary.setDmg(primary.dmg() * 1.2);
            ((DashData) primary.abilityData()).setDistance(10).setSpeed(4);
        }
        if (charms.contains(Charms.SOUL_EATER)) {
            soulsPerHit *= 1.5;
        }
        if (charms.contains(Charms.SOUL_TWISTER)) {
            soulsPerCast *= 0.75;
        }
        if (charms.contains(Charms.SPRINTMASTER)) {
            Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)).setBaseValue(.16);
        }
        if (charms.contains(Charms.STEADY_BODY)) {
            setWeight(weight() + 1.5);
        }
        if (charms.contains(Charms.STRENGTH)) {
            setJabDamage(6);
        }
        //Weaversong
    }

    public enum Charms {
        DASHMASTER(2, "Faster Dash Cooldown", 17010), //less dash cd
        GRUBSONG(1, "Get soul when hit", 17011), //soul when hit
        HEAVY_BLOW(1, "Deal more knockback", 17012), //more kb on dash/spells
        KINGSOUL(4, "Passively gain soul", 17013), //auto soul regen
        MARK_OF_PRIDE(3, "Longer jabs", 17014), //more jab range
        NAIL_MASTER(1, "Nothing.", 17015),
        QUICK_SLASH(3, "Faster Jabs", 17016), //quicker jabs
        SHAMAN_STONE(3, "Improves spells", 17017), //more spell dmg
        SHARP_SHADOW(2, "Improves dash", 17018), //more dash dmg
        SOUL_EATER(3, "More souls per hit", 17019), //most souls per hit
        SOUL_TWISTER(2, "Less souls per spell cast", 17020), //less cost per spells
        SPRINTMASTER(1, "Move slightly faster", 17021), //faster move speed
        STEADY_BODY(3, "Increase weight", 17022), //weight
        STRENGTH(3, "Stronger jabs", 17023), //jab dmg
        WEAVERSONG(2, "Spider Minions (coming soon)", 17024); //spider minions

        final int cost;
        final String description;
        final int texture;

        Charms(int cost, String description, int texture) {
            this.cost = cost;
            this.description = description;
            this.texture = texture;
        }

        public int cost(){return this.cost; }
        public String description(){return this.description;}
        public int texture(){return this.texture;}
    }
}
