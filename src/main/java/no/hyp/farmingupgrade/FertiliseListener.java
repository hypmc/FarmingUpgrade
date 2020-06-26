package no.hyp.farmingupgrade;

import com.google.common.collect.Lists;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Random;
import java.util.function.Consumer;

final class FertiliseListener implements Listener {

    private final FarmingUpgrade plugin;

    private final Random random;

    public FertiliseListener(FarmingUpgrade plugin, Random random) {
        this.plugin = plugin;
        this.random = random;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFertilise(BlockFertilizeEvent e) {
        // Do not handle delegated BlockFertilizeEvents.
        if (e instanceof UpgradedBlockFertilizeEvent) {
            return;
        }
        // Only handle if upgraded fertilisation is enabled.
        if (!this.plugin.configurationFertiliserUpgrade()) {
            return;
        }
        Block block = e.getBlock();
        // Upgraded fertilisation mechanics are only enabled for specific crops.
        if (!this.plugin.fertilisableCrops.containsKey(block.getType())) {
            return;
        }
        // Cancel to let plugin handle event.
        e.setCancelled(true);
        // Remove a bonemeal if not in creative.
        if (e.getPlayer() != null && e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            Inventory inventory = e.getPlayer().getInventory();
            int first = inventory.first(Material.BONE_MEAL);
            ItemStack item = inventory.getItem(first);
            if (item != null) {
                item.setAmount(item.getAmount() - 1);
            }
        }
        // Find adjacent fertilisable crops, send a BlockFertilizeEvent for them and apply
        // fertiliser if the event is successful.
        Collection<Block> fertilisedBlocks = FarmingUpgrade.findAdjacentMaterials(this.plugin.fertilisableCrops.keySet(), block, 1);
        for (Block fertilisedBlock : fertilisedBlocks) {
            BlockState fertilisedState = fertilisedBlock.getState();
            // Apply fertiliser to the crop state.
            Consumer<BlockState> fertiliserFunction = this.plugin.fertilisableCrops.get(fertilisedState.getType());
            fertiliserFunction.accept(fertilisedState);
            // Call an event for the fertilised block.
            UpgradedBlockFertilizeEvent upgradedEvent = new UpgradedBlockFertilizeEvent(fertilisedBlock, e.getPlayer(), Lists.newArrayList(fertilisedState));
            this.plugin.getServer().getPluginManager().callEvent(upgradedEvent);
            // If the event is allowed, apply fertiliser.
            if (!upgradedEvent.isCancelled()) {
                fertilisedState.update();
                FarmingUpgrade.fertiliseEffect(fertilisedBlock);
            }
        }
    }

}
