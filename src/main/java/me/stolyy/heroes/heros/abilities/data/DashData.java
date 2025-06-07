package me.stolyy.heroes.heros.abilities.data;

import org.bukkit.Color;
import org.bukkit.Particle;

public class DashData extends AbilityData {
    private double distance = 7, speed = 3;
    private Particle particle;
    private Particle.DustOptions dustOptions;
    private int customModelData;

    public DashData() {
    }
    public DashData(double distance, double speed) {
        this.distance = distance;
        this.speed = speed;
    }
    public DashData(double distance, double speed, Particle particle) {
        this.distance = distance;
        this.speed = speed;
        this.particle = particle;
    }
    public DashData(double distance, double speed, Particle particle, Particle.DustOptions dustOptions) {
        this.distance = distance;
        this.speed = speed;
        this.particle = particle;
        this.dustOptions = dustOptions;
    }
    public DashData(double distance, double speed, int customModelData) {
        this.distance = distance;
        this.speed = speed;
        this.customModelData = customModelData;
    }

    public DashData setDistance(double distance) {
        this.distance = distance;
        return this;
    }
    public DashData setSpeed(double speed) {
        this.speed = speed;
        return this;
    }
    public DashData setParticle(Particle particle) {
        this.particle = particle;
        return this;
    }
    public DashData setDustOptions(Color color, float size) {
        this.dustOptions = new Particle.DustOptions(color, size);
        return this;
    }
    public DashData setCustomModelData(int customModelData) {
        this.customModelData = customModelData;
        return this;
    }

    public double distance() {
        return distance;
    }
    public double speed() {
        return speed;
    }
    public Particle particle() {
        return particle;
    }
    public Particle.DustOptions dustOptions() {
        return dustOptions;
    }
    public int customModelData() {
        return customModelData;
    }
}
