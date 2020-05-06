package no.hyp.farmingupgrade;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

final class HydrationListener implements Listener {

    private final FarmingUpgrade plugin;

    /**
     * When a player tramples farmland, Minecraft calls a PlayerInteractEvent, and then
     * a BlockFadeEvent. Minecraft also calls FadeEvent when a Farmland is turning to
     * Dirt from dehydration. To distinguish between trampling and drying, store the
     * block that is trampled in InteractEvent here and check if it equal in the FadeEvent.
     */
    private Block trampledFarmland;

    public HydrationListener(FarmingUpgrade plugin) {
        this.plugin = plugin;
    }

    /**
     * Use this event to detect if the next BlockFadeEvent is caused by trampling.
     * In that case, the hydration listener should not handle it.
     *
     * @param e
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFarmlandTrample(@NotNull PlayerInteractEvent e) {
        // Do not handle events that are delegated by FarmingUpgrade.
        if (e instanceof UpgradedPlayerInteractEvent) {
            return;
        }
        // Check that this is a Farmland trample event.
        Block farmland = e.getClickedBlock();
        boolean trample = farmland != null && farmland.getType() == Material.FARMLAND && e.getAction() == Action.PHYSICAL;
        if (!trample) {
            return;
        }
        // Store the farmland so the hydration listener does not handle it.
        this.trampledFarmland = farmland;
    }

    /**
     * A BlockFadeEvent is called when Minecraft wants to turn a Farmland block with a moisture level of 0
     * into Dirt.
     *
     * It is also called after a PlayerInteractEvent, after a player tramples (jumps on) a
     * Farmland block. This happens if upgraded trample mechanics is not enabled.
     *
     * Trample is handled elsewhere, so only handle drying here.
     *
     * @param e The event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFarmlandDry(BlockFadeEvent e) {
        // Do not handle events that are delegated by FarmingUpgrade.
        if (e instanceof UpgradedBlockFadeEvent) {
            return;
        }
        // Check that this is a Farmland dry or trample event.
        Block farmland = e.getBlock();
        if (farmland.getType() != Material.FARMLAND) {
            return;
        }
        // Check if this block was trampled or if it is drying up.
        // If this block is drying, apply upgraded hydration mechanics if they are enabled.
        // If the block is trampled, let Minecraft handle it.
        if (!farmland.equals(this.trampledFarmland)) {
            // If upgraded hydration is enabled, cancel the event and handle it manually.
            // Otherwise, let Minecraft handle the event as normal.
            if (this.plugin.configurationHydrationUpgrade()) {
                e.setCancelled(true);
                int range = this.plugin.configurationHydrationRange();
                int depth = this.plugin.configurationHydrationDepth();
                int height = this.plugin.configurationHydrationHeight();
                boolean dry = this.plugin.configurationHydrationDry();
                FarmingUpgrade.farmlandUpgradedChangeMoisture(farmland, range, depth, height, dry);
            }
        }
        // The trampled farmland check is no longer needed after handling the event.
        this.trampledFarmland = null;
    }

    /**
     * MoistureChangeEvent is called when Minecraft wants to change the moisture level of
     * Farmland. Minecraft wants to dehydrate or dry (turn to Dirt) Farmland outside the
     * Vanilla water range, and hydrate Farmland within the Vanilla water range.
     *
     * For blocks outside the Vanilla water range, but within the upgraded water range, cancel this
     * event and hydrate the Farmland instead. Remember to also catch BlockFadeEvent for Farmland with the
     * lowest humidity.
     *
     * For blocks inside the Vanilla water range, but outside the upgraded water range, dehydrate
     * the Farmland instead. For fully hydrated Farmland, no event will be thrown. Use something
     * like BlockGrowEvent to create events for fully hydrated Farmland.
     *
     * @param e
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFarmlandMoistureChange(MoistureChangeEvent e) {
        if (!this.plugin.configurationHydrationUpgrade()) {
            return;
        }
        // Do not handle events that are called by FarmingUpgrade.
        if (e instanceof UpgradedMoistureChangeEvent) {
            return;
        }
        Block farmland = e.getBlock();
        if (farmland.getType() != Material.FARMLAND) {
            return;
        }
        e.setCancelled(true);
        int range = this.plugin.configurationHydrationRange();
        int depth = this.plugin.configurationHydrationDepth();
        int height = this.plugin.configurationHydrationHeight();
        boolean dry = this.plugin.configurationHydrationDry();
        FarmingUpgrade.farmlandUpgradedChangeMoisture(farmland, range, depth, height, dry);
    }

    /**
     * BlockGrowEvent is called when a crop grows.
     *
     * Minecraft does not send any events for fully hydrated Farmland within the Vanilla water range.
     * Use BlockGrowEvents to create events for fully hydrated Farmland to use upgraded hydration mechanics.
     *
     * Minecraft does not dry Farmland into Dirt if there is a crop on it, and thus do not send
     * any events either. Use the grow event to update the Farmland moisture.
     *
     * @param e
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCropGrow(BlockGrowEvent e) {
        if (!this.plugin.configurationHydrationUpgrade()) {
            return;
        }
        if (e.isCancelled()) {
            return;
        }
        Block farmland = e.getBlock().getRelative(0, -1, 0);
        if (farmland.getType() == Material.FARMLAND) {
            int range = this.plugin.configurationHydrationRange();
            int depth = this.plugin.configurationHydrationDepth();
            int height = this.plugin.configurationHydrationHeight();
            boolean dry = this.plugin.configurationHydrationDry();
            FarmingUpgrade.farmlandUpgradedChangeMoisture(farmland, range, depth, height, dry);
        }
    }

}
