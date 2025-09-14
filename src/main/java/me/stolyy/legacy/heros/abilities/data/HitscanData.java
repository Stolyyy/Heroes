package me.stolyy.legacy.heros.abilities.data;

import org.bukkit.Color;
import org.bukkit.Particle;

public class HitscanData extends AbilityData {
    private double radius = 1, range = 75;
    private boolean piercesPlayers = true, piercesWalls;
    private Particle particle;
    private Particle.DustOptions dustOptions;
    private int customModelData;

    public HitscanData() {
    }
    public HitscanData(double radius, double range) {
        this.radius = radius;
        this.range = range;
    }
    public HitscanData(double radius, double range, Particle particle) {
        this.radius = radius;
        this.range = range;
        this.particle = particle;
    }
    public HitscanData(double radius, double range, Particle particle, Particle.DustOptions dustOptions) {
        this.radius = radius;
        this.range = range;
        this.particle = particle;
        this.dustOptions = dustOptions;
    }
    public HitscanData(double radius, double range, int customModelData) {
        this.radius = radius;
        this.range = range;
        this.customModelData = customModelData;
    }
    public HitscanData(double radius, double range, boolean piercesPlayers) {
        this.radius = radius;
        this.range = range;
        this.piercesPlayers = piercesPlayers;
    }
    public HitscanData(double radius, double range, boolean piercesPlayers, Particle particle) {
        this.radius = radius;
        this.range = range;
        this.piercesPlayers = piercesPlayers;
        this.particle = particle;
    }
    public HitscanData(double radius, double range, boolean piercesPlayers, Particle particle, Particle.DustOptions dustOptions) {
        this.radius = radius;
        this.range = range;
        this.piercesPlayers = piercesPlayers;
        this.particle = particle;
        this.dustOptions = dustOptions;
    }
    public HitscanData(double radius, double range, boolean piercesPlayers, boolean piercesWalls) {
        this.radius = radius;
        this.piercesWalls = piercesWalls;
        this.range = range;
        this.piercesPlayers = piercesPlayers;
    }
    public HitscanData(double radius, double range, boolean piercesPlayers, boolean piercesWalls, Particle particle) {
        this.radius = radius;
        this.range = range;
        this.piercesWalls = piercesWalls;
        this.piercesPlayers = piercesPlayers;
        this.particle = particle;
    }
    public HitscanData(double radius, double range, boolean piercesPlayers, boolean piercesWalls, Particle particle, Particle.DustOptions dustOptions) {
        this.radius = radius;
        this.range = range;
        this.piercesPlayers = piercesPlayers;
        this.piercesWalls = piercesWalls;
        this.particle = particle;
        this.dustOptions = dustOptions;
    }
    public HitscanData(double radius, double range, boolean piercesPlayers, int customModelData) {
        this.radius = radius;
        this.range = range;
        this.piercesPlayers = piercesPlayers;
        this.customModelData = customModelData;
    }
    public HitscanData(double radius, double range, boolean piercesPlayers, boolean piercesWalls, int customModelData) {
        this.radius = radius;
        this.range = range;
        this.piercesPlayers = piercesPlayers;
        this.piercesWalls = piercesWalls;
        this.customModelData = customModelData;
    }

    public HitscanData setRadius(double radius) {
        this.radius = radius;
        return this;
    }
    public HitscanData setRange(double range) {
        this.range = range;
        return this;
    }
    public HitscanData setPiercesPlayers(boolean piercesPlayers) {
        this.piercesPlayers = piercesPlayers;
        return this;
    }
    public HitscanData setPiercesWalls(boolean piercesWalls) {
        this.piercesWalls = piercesWalls;
        return this;
    }
    public HitscanData setParticle(Particle particle) {
        this.particle = particle;
        return this;
    }
    public HitscanData setDustOptions(Color color, float size) {
        this.dustOptions = new Particle.DustOptions(color, size);
        return this;
    }
    public HitscanData setCustomModelData(int customModelData) {
        this.customModelData = customModelData;
        return this;
    }

    public double radius() {
        return radius;
    }
    public double range() {
        return range;
    }
    public boolean piercesPlayers() {
        return piercesPlayers;
    }
    public boolean piercesWalls() {
        return piercesWalls;
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
