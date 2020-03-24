package no.hyp.farmingupgrade;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.block.MoistureChangeEvent;

public class UpgradedMoistureChangeEvent extends MoistureChangeEvent {

    public UpgradedMoistureChangeEvent(Block block, BlockState newState) {
        super(block, newState);
    }

}
