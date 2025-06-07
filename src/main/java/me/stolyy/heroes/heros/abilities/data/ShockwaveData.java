package me.stolyy.heroes.heros.abilities.data;

import org.bukkit.Color;
import org.bukkit.Particle;

public class ShockwaveData extends AbilityData {
    private double radius = 4;
    private boolean piercesWalls, usesBlocks;
    private Particle particle;
    private Particle.DustOptions dustOptions;
    private int customModelData;

    public ShockwaveData() {
    }
    public ShockwaveData(double radius) {
        this.radius = radius;
    }
    public ShockwaveData(double radius, Particle particle) {
        this.radius = radius;
        this.particle = particle;
    }
    public ShockwaveData(double radius, Particle particle, Particle.DustOptions dustOptions) {
        this.radius = radius;
        this.particle = particle;
        this.dustOptions = dustOptions;
    }
    public ShockwaveData(double radius, int customModelData) {
        this.radius = radius;
        this.customModelData = customModelData;
    }
    public ShockwaveData(double radius, boolean piercesWalls) {
        this.radius = radius;
        this.piercesWalls = piercesWalls;
    }
    public ShockwaveData(double radius, boolean piercesWalls, Particle particle) {
        this.radius = radius;
        this.piercesWalls = piercesWalls;
        this.particle = particle;
    }
    public ShockwaveData(double radius, boolean piercesWalls, Particle particle, Particle.DustOptions dustOptions) {
        this.radius = radius;
        this.piercesWalls = piercesWalls;
        this.particle = particle;
        this.dustOptions = dustOptions;
    }
    public ShockwaveData(double radius, boolean piercesWalls, int customModelData) {
        this.radius = radius;
        this.piercesWalls = piercesWalls;
        this.customModelData = customModelData;
    }
    public ShockwaveData(double radius, boolean piercesWalls, boolean usesBlocks) {
        this.radius = radius;
        this.piercesWalls = piercesWalls;
        this.usesBlocks = usesBlocks;
    }
    public ShockwaveData(double radius, boolean piercesWalls, boolean usesBlocks, Particle particle) {
        this.radius = radius;
        this.piercesWalls = piercesWalls;
        this.usesBlocks = usesBlocks;
        this.particle = particle;
    }
    public ShockwaveData(double radius, boolean piercesWalls, boolean usesBlocks, Particle particle, Particle.DustOptions dustOptions) {
        this.radius = radius;
        this.piercesWalls = piercesWalls;
        this.usesBlocks = usesBlocks;
        this.particle = particle;
        this.dustOptions = dustOptions;
    }
    public ShockwaveData(double radius, boolean piercesWalls, boolean usesBlocks, int customModelData) {
        this.radius = radius;
        this.piercesWalls = piercesWalls;
        this.usesBlocks = usesBlocks;
        this.customModelData = customModelData;
    }

    public ShockwaveData setRadius(double radius) {
        this.radius = radius;
        return this;
    }
    public ShockwaveData setPiercesWalls(boolean piercesWalls) {
        this.piercesWalls = piercesWalls;
        return this;
    }
    public ShockwaveData setUsesBlocks(boolean usesBlocks) {
        this.usesBlocks = usesBlocks;
        return this;
    }
    public ShockwaveData setParticle(Particle particle) {
        this.particle = particle;
        return this;
    }
    public ShockwaveData setDustOptions(Color color, float size) {
        this.dustOptions = new Particle.DustOptions(color, size);
        return this;
    }
    public ShockwaveData setCustomModelData(int customModelData) {
        this.customModelData = customModelData;
        return this;
    }

    public double radius() {
        return radius;
    }
    public boolean piercesWalls() {
        return piercesWalls;
    }
    public boolean usesBlocks() {
        return usesBlocks;
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
