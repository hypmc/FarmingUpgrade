package no.hyp.farmingupgrade;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockFertilizeEvent;

import java.util.List;

public class UpgradedBlockFertilizeEvent extends BlockFertilizeEvent {

    public UpgradedBlockFertilizeEvent(Block theBlock, Player player, List<BlockState> blocks) {
        super(theBlock, player, blocks);
    }

}
