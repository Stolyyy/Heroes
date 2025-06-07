package me.stolyy.heroes.heros.abilities.data;

public class ProjectileData extends AbilityData {
    private double speed = 3, radius = 1, range = 75;
    private boolean gravity, piercesWalls, piercesPlayers = true;
    private int customModelData;

    public ProjectileData() {
    }
    public ProjectileData(double speed, double radius, double range) {
        this.speed = speed;
        this.radius = radius;
        this.range = range;
    }
    public ProjectileData(double speed, double radius, double range, int customModelData) {
        this.speed = speed;
        this.radius = radius;
        this.range = range;
        this.customModelData = customModelData;
    }
    public ProjectileData(double speed, double radius, double range, boolean gravity) {
        this.speed = speed;
        this.radius = radius;
        this.range = range;
        this.gravity = gravity;
    }
    public ProjectileData(double speed, double radius, double range, boolean gravity, boolean piercesPlayers) {
        this.speed = speed;
        this.radius = radius;
        this.range = range;
        this.gravity = gravity;
        this.piercesPlayers = piercesPlayers;
    }
    public ProjectileData(double speed, double radius, double range, boolean gravity, boolean piercesPlayers, boolean piercesWalls) {
        this.speed = speed;
        this.radius = radius;
        this.range = range;
        this.gravity = gravity;
        this.piercesPlayers = piercesPlayers;
        this.piercesWalls = piercesWalls;
    }
    public ProjectileData(double speed, double radius, double range, boolean gravity, int customModelData) {
        this.speed = speed;
        this.radius = radius;
        this.range = range;
        this.gravity = gravity;
        this.customModelData = customModelData;
    }
    public ProjectileData(double speed, double radius, double range, boolean gravity, boolean piercesPlayers, int customModelData) {
        this.speed = speed;
        this.radius = radius;
        this.range = range;
        this.gravity = gravity;
        this.piercesPlayers = piercesPlayers;
        this.customModelData = customModelData;
    }
    public ProjectileData(double speed, double radius, double range, boolean gravity, boolean piercesPlayers, boolean piercesWalls, int customModelData) {
        this.speed = speed;
        this.radius = radius;
        this.range = range;
        this.gravity = gravity;
        this.piercesPlayers = piercesPlayers;
        this.piercesWalls = piercesWalls;
        this.customModelData = customModelData;
    }

    public ProjectileData setSpeed(double speed) {
        this.speed = speed;
        return this;
    }
    public ProjectileData setRadius(double radius) {
        this.radius = radius;
        return this;
    }
    public ProjectileData setGravity(boolean gravity) {
        this.gravity = gravity;
        return this;
    }
    public ProjectileData setRange(double range) {
        this.range = range;
        return this;
    }
    public ProjectileData setPiercesWalls(boolean piercesWalls) {
        this.piercesWalls = piercesWalls;
        return this;
    }
    public ProjectileData setPiercesPlayers(boolean piercesPlayers) {
        this.piercesPlayers = piercesPlayers;
        return this;
    }
    public ProjectileData setCustomModelData(int customModelData) {
        this.customModelData = customModelData;
        return this;
    }

    public double speed() {
        return speed;
    }
    public double radius() {
        return radius;
    }
    public boolean gravity() {
        return gravity;
    }
    public double range() {
        return range;
    }
    public boolean piercesWalls() {
        return piercesWalls;
    }
    public boolean piercesPlayers() {
        return piercesPlayers;
    }
    public int customModelData() {
        return customModelData;
    }
}
