package me.stolyy.heroes.heros.configs;


public record EquipmentConfig(
        String headName,
        String chestName,
        String legsName,
        String feetName,

        String head,
        int[] chestColors, //RGB
        int[] legsColors, //RGB
        int[] feetColors //RGB
) { }
