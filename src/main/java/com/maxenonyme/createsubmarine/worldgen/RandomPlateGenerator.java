package com.maxenonyme.createsubmarine.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class RandomPlateGenerator {
    private RandomPlateGenerator() {}

    private static final String[] PLATE_NAMES = {
        "African", "Antarctic", "Eurasian", "Indo-Australian", "Australian",
        "North American", "Pacific", "South American", "Indian",
        "Amur", "Arabian", "Burma", "Caribbean", "Caroline",
        "Cocos", "Nazca", "New Hebrides", "Okhotsk", "Philippine Sea",
        "Scotia", "Somali", "Sunda", "Yangtze",
        "Adriatic", "Aegean Sea", "Anatolian", "Banda Sea",
        "Bird's Head", "Capricorn", "Coiba", "Conway Reef",
        "Easter", "Explorer", "Futuna", "Galapagos",
        "Gorda", "Greenland", "Halmahera", "Iberian", "Iranian",
        "Juan de Fuca", "Juan Fernandez", "Kermadec", "Lwandle",
        "Madagascar", "Malpelo", "Manus", "Maoke", "Mariana",
        "Molucca Sea", "Niuafo'ou", "North Andes", "North Bismarck",
        "North Galapagos", "Okinawa", "Panama", "Pelso", "Rivera",
        "Rovuma", "Sangihe", "Shetland", "Solomon Sea",
        "South Bismarck", "South Sandwich", "Timor", "Tisza",
        "Tonga", "Trobriand", "Victoria", "Woodlark",
        "Balmoral Reef", "Philippine Mobile Belt",
        "Puerto Rico-Virgin Islands", "Queen Elizabeth Islands",
        "South Jamaica", "Hreppar", "Azores", "Gonave",
        "Hispaniola", "Altiplano", "Falklands", "Danakil",
        "Seychelles", "Kerguelen", "Macquarie", "Moa", "Lord Howe Rise"
    };

    public static PlateTectonicsConfig generate(long seed) {
        Random rng = new Random(seed);
        // Pick 8-15 distinct names from the list
        int count = 8 + rng.nextInt(8);
        List<String> pool = new ArrayList<>(List.of(PLATE_NAMES));
        // Shuffle
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            String tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
        }
        // Take first `count` names
        List<String> selected = pool.subList(0, count);

        List<TectonicPlate> plates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = selected.get(i);
            double angle = rng.nextDouble() * 2 * Math.PI;
            double speed = 0.5 + rng.nextDouble() * 14.5;
            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;

            double mass = 0.2 + Math.pow(rng.nextDouble(), 1.5) * 3.0;
            boolean oceanic = rng.nextBoolean();
            double weight = 1.0 + rng.nextDouble() * 14.0;

            UUID uuid = UUID.nameUUIDFromBytes(("plate:" + name + ":" + seed).getBytes());
            plates.add(new TectonicPlate(name, uuid.toString(), vx, vz, mass, oceanic, weight));
        }

        return new PlateTectonicsConfig(5000, seed, plates);
    }
}
