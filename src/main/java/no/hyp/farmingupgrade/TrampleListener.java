package no.hyp.farmingupgrade;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

final class TrampleListener implements Listener {

    private final FarmingUpgrade plugin;

    public TrampleListener(FarmingUpgrade plugin) {
        this.plugin = plugin;
    }

    /**
     * A PlayerInteractEvent is called for Farmland when it is trampled by a player. The result of this
     * event is to call a BlockFadeEvent afterwards.
     *
     * If upgraded trampling is not enabled, do not handle this event.
     *
     * @param e
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFarmlandTrample(@NotNull PlayerInteractEvent e) {
        // Check that this is a Farmland trample event.
        Block farmland = e.getClickedBlock();
        boolean trample = farmland != null && farmland.getType() == Material.FARMLAND && e.getAction() == Action.PHYSICAL;
        if (!trample) {
            return;
        }
        // Do not handle events that are called by FarmingUpgrade.
        if (e instanceof UpgradedPlayerInteractEvent) {
            return;
        }
        // If trample upgrade is enabled, handle trampling. Otherwise, let Minecraft
        // handle the trampling but set the trample block so it can be identified.
        if (this.plugin.configurationTrampleUpgrade()) {
            // Cancel to handle manually.
            e.setUseInteractedBlock(Event.Result.DENY);
            // Trample the crop above the farmland.
            Block crop = farmland.getRelative(0, 1, 0);
            if (this.plugin.harvestableCrops.containsKey(crop.getType())) {
                // Send an InteractEvent for the trampled crop.
                UpgradedPlayerInteractEvent trampleEvent = new UpgradedPlayerInteractEvent(e.getPlayer(), e.getAction(), e.getItem(), crop, e.getBlockFace());
                this.plugin.getServer().getPluginManager().callEvent(trampleEvent);
                if (trampleEvent.useInteractedBlock() == Event.Result.ALLOW) {
                    // Calculate the state of the crop after being trampled.
                    BlockState oldState = crop.getState();
                    BlockState state = crop.getState();
                    state.setType(Material.AIR);
                    state.setType(crop.getType());
                    // Send a BlockFadeEvent to indicate the crop being reset.
                    UpgradedBlockFadeEvent fadeEvent = new UpgradedBlockFadeEvent(crop, state);
                    this.plugin.getServer().getPluginManager().callEvent(fadeEvent);
                    if (!fadeEvent.isCancelled()) {
                        state.update(true);
                        FarmingUpgrade.breakBlockEffect(crop, oldState, Sound.BLOCK_CROP_BREAK);
                    }
                }
            }
        }
    }

}
