package no.hyp.farmingupgrade;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.block.BlockFadeEvent;

public class UpgradedBlockFadeEvent extends BlockFadeEvent {

    public UpgradedBlockFadeEvent(Block block, BlockState newState) {
        super(block, newState);
    }

}
