package me.stolyy.heroes.heros.abilities.data;

import org.bukkit.Color;
import org.bukkit.Particle;

public class ConeData extends AbilityData {
    private double radius = 3, length = 5;
    private boolean piercesWalls;
    private Particle particle;
    private Particle.DustOptions dustOptions;
    private int customModelData;

    public ConeData() {
    }
    public ConeData(double radius, double length) {
        this.radius = radius;
        this.length = length;
    }
    public ConeData(double radius, double length, Particle particle) {
        this.radius = radius;
        this.length = length;
        this.particle = particle;
    }
    public ConeData(double radius, double length, Particle particle, Particle.DustOptions dustOptions) {
        this.radius = radius;
        this.length = length;
        this.particle = particle;
        this.dustOptions = dustOptions;
    }
    public ConeData(double radius, double length, int customModelData) {
        this.radius = radius;
        this.length = length;
        this.customModelData = customModelData;
    }
    public ConeData(double radius, double length, boolean piercesWalls) {
        this.radius = radius;
        this.length = length;
        this.piercesWalls = piercesWalls;
    }
    public ConeData(double radius, double length, boolean piercesWalls, Particle particle) {
        this.radius = radius;
        this.length = length;
        this.piercesWalls = piercesWalls;
        this.particle = particle;
    }
    public ConeData(double radius, double length, boolean piercesWalls, Particle particle, Particle.DustOptions dustOptions) {
        this.radius = radius;
        this.length = length;
        this.piercesWalls = piercesWalls;
        this.particle = particle;
        this.dustOptions = dustOptions;
    }
    public ConeData(double radius, double length, boolean piercesWalls, int customModelData) {
        this.radius = radius;
        this.length = length;
        this.piercesWalls = piercesWalls;
        this.customModelData = customModelData;
    }

    public ConeData setRadius(double radius) {
        this.radius = radius;
        return this;
    }
    public ConeData setLength(double length) {
        this.length = length;
        return this;
    }
    public ConeData setPiercesWalls(boolean piercesWalls) {
        this.piercesWalls = piercesWalls;
        return this;
    }
    public ConeData setParticle(Particle particle) {
        this.particle = particle;
        return this;
    }
    public ConeData setDustOptions(Color color, float size) {
        this.dustOptions = new Particle.DustOptions(color, size);
        return this;
    }
    public ConeData setCustomModelData(int customModelData) {
        this.customModelData = customModelData;
        return this;
    }

    public double radius() {
        return radius;
    }
    public double length() {
        return length;
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
