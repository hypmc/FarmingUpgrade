package no.hyp.farmingupgrade;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

public class UpgradedBlockBreakEvent extends BlockBreakEvent {

    public UpgradedBlockBreakEvent(Block block, Player player) {
        super(block, player);
    }

}
