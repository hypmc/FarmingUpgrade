package no.hyp.farmingupgrade;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Random;
import java.util.Set;

final class HarvestListener implements Listener {

    private final FarmingUpgrade plugin;

    private final Random random;

    public HarvestListener(FarmingUpgrade plugin, Random random) {
        this.plugin = plugin;
        this.random = random;
    }

    /**
     * If a player breaks a crop with a hoe, handle this event manually. Find the adjacent crops
     * and call custom UpgradedBlockBreakEvents on the to give other plugins a chance to catch them.
     *
     * Since this is a mechanic the event should be caught and cancelled as early as possible (LOWEST priority).
     * Then we can dispatch new events that we can control the outcome of ourselves.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFarm(BlockBreakEvent e) {
        if (!this.plugin.configurationHoeUpgrade()) {
            return;
        }
        // Do not handle events that are called by FarmingUpgrade.
        if (e instanceof UpgradedBlockBreakEvent) {
            return;
        }
        Player player = e.getPlayer();
        Block crop = e.getBlock();
        // Handle breaking of crops.
        if (!this.plugin.harvestableCrops.containsKey(crop.getType())) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        // If broken by a hoe, all crops within range are harvested and automatically replanted.
        if (!this.plugin.tools.containsKey(tool.getType())) {
            return;
        }
        e.setCancelled(true);
        Vector direction = player.getLocation().getDirection();
        //direction.add(new Vector(0.0, -direction.getY(), 0.0)).multiply(1.0 / direction.length());
        Location location = player.getEyeLocation();
        location.add(direction.multiply(1.5));
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, location, 1);
        // Calculate the radius of the hoe sweep.
        // Radius is affected by hoe material and the Efficiency enchantment (1 radius per 2 levels).
        int range;
        if (this.plugin.configurationHoeRange()) {
            if (this.plugin.configurationHoeEfficiency()) {
                range = Math.min(7, this.plugin.tools.get(tool.getType()) + tool.getEnchantmentLevel(Enchantment.DIG_SPEED) / 2);
            } else {
                range = Math.min(7, this.plugin.tools.get(tool.getType()));
            }
        } else {
            range = 0;
        }
        // Get the adjacent crops in range.
        Set<Block> adjacentCrops = FarmingUpgrade.findAdjacentMaterials(this.plugin.harvestableCrops.keySet(), crop, range);
        // For every crop block, simulate a BlockBreakEvent for other plugins to react to.
        boolean replant = this.plugin.configurationHoeReplant();
        boolean unbreaking = this.plugin.configurationHoeUnbreaking();
        boolean collect = this.plugin.configurationHoeCollect();
        boolean harvest = this.plugin.configurationHoeHarvest();
        for (Block adjacentCrop : adjacentCrops) {
            FarmingUpgrade.harvestCrop(this.random, player, adjacentCrop, tool, replant, unbreaking, collect, harvest, this.plugin.harvestableCrops.get(adjacentCrop.getType()));
        }
    }

}
