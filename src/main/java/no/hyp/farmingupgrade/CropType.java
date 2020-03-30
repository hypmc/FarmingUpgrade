package no.hyp.farmingupgrade;

import org.bukkit.Material;

public class CropType {

    private final Material material;

    private final Material seed;

    public CropType(Material material, Material seed) {
        this.material = material;
        this.seed = seed;
    }

    public Material getMaterial() {
        return material;
    }

    public Material getSeed() {
        return seed;
    }

}
