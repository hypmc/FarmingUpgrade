package no.hyp.farmingupgrade;

import com.google.common.collect.Lists;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class FarmingUpgrade extends JavaPlugin implements Listener {

    private Thread watcherThread;

    private Path path;

    private Random random;

    /**
     * A map of hoes and their ranges.
     */
    private Map<Material, Integer> toolMaterials;

    private Map<Material, CropType> cropMaterials;

    private final String CONFIGURATION_TRAMPLE_CROPS = "trampleCrops";

    private final String CONFIGURATION_HYDRATION_UPGRADE = "hydrationUpgrade";

    private final String CONFIGURATION_HYDRATION_RANGE = "hydrationRange";

    private final String CONFIGURATION_HYDRATION_DEPTH = "hydrationDepth";

    private final String CONFIGURATION_DRY_FARMLAND = "dryFarmland";

    private final String CONFIGURATION_HOE_UPGRADE = "hoeUpgrade";

    private final String CONFIGURATION_HOE_REPLANT = "hoeReplant";

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.getServer().getPluginManager().registerEvents(this, this);

        random = new Random();

        toolMaterials = new HashMap<>();
        toolMaterials.put(Material.WOODEN_HOE, 0);
        toolMaterials.put(Material.STONE_HOE, 0);
        toolMaterials.put(Material.GOLDEN_HOE, 1);
        toolMaterials.put(Material.IRON_HOE, 1);
        toolMaterials.put(Material.DIAMOND_HOE, 2);

        cropMaterials = new HashMap<>();
        cropMaterials.put(Material.BEETROOTS, new CropType(Material.BEETROOTS, Material.BEETROOT_SEEDS));
        cropMaterials.put(Material.CARROTS, new CropType(Material.CARROTS, Material.CARROT));
        cropMaterials.put(Material.NETHER_WART, new CropType(Material.NETHER_WART, Material.NETHER_WART));
        cropMaterials.put(Material.POTATOES, new CropType(Material.POTATOES, Material.POTATO));
        cropMaterials.put(Material.WHEAT, new CropType(Material.WHEAT, Material.WHEAT_SEEDS));

        this.path = Paths.get(this.getDataFolder().getPath());
        configurationWatcherEnable();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        configurationWatcherDisable();
        super.onDisable();
    }

    /**
     * Start/restart the configuration watcher thread.
     */
    public void configurationWatcherEnable() {
        //
        this.configurationWatcherDisable();
        this.watcherThread = new Thread(new ConfigurationWatcher(this, this.path));
        this.watcherThread.start();
    }

    /**
     * Stop the configuration watcher thread.
     */
    public void configurationWatcherDisable() {
        if (this.watcherThread != null) {
            this.watcherThread.interrupt();
        }
    }

    /**
     * A PlayerInteractEvent is called for Farmland when it is trampled by a player. The result of this
     * event is to call a BlockFadeEvent afterwards.
     *
     * @param e
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFarmlandTrample(PlayerInteractEvent e) {
        if (!(e instanceof UpgradedPlayerInteractEvent)) {
            Block farmland = e.getClickedBlock();
            if (farmland != null && e.getClickedBlock().getType() == Material.FARMLAND && e.getAction() == Action.PHYSICAL) {
                // Cancel to handle manually.
                e.setUseInteractedBlock(Event.Result.DENY);
                // Trample crops or trample Farmland.
                if (this.getConfig().getBoolean(CONFIGURATION_TRAMPLE_CROPS, true)) {
                    // Trample the crop above the farmland.
                    Block crop = farmland.getRelative(0, 1, 0);
                    if (cropMaterials.containsKey(crop.getType())) {
                        // Send an InteractEvent for the trampled crop.
                        UpgradedPlayerInteractEvent trampleEvent = new UpgradedPlayerInteractEvent(e.getPlayer(), e.getAction(), e.getItem(), crop, e.getBlockFace());
                        this.getServer().getPluginManager().callEvent(trampleEvent);
                        if (trampleEvent.useInteractedBlock() == Event.Result.ALLOW) {
                            // Calculate the state of the crop after being trampled.
                            BlockState oldState = crop.getState();
                            BlockState state = crop.getState();
                            state.setType(Material.AIR);
                            state.setType(crop.getType());
                            // Send a BlockFadeEvent to indicate the crop being reset.
                            UpgradedBlockFadeEvent fadeEvent = new UpgradedBlockFadeEvent(crop, state);
                            this.getServer().getPluginManager().callEvent(fadeEvent);
                            if (!fadeEvent.isCancelled()) {
                                state.update(true);
                                breakBlockEffect(crop, oldState, Sound.BLOCK_CROP_BREAK);
                            }
                        }
                    }
                } else {
                    // Send an InteractEvent for the trampled Farmland.
                    UpgradedPlayerInteractEvent trampleEvent = new UpgradedPlayerInteractEvent(e.getPlayer(), e.getAction(), e.getItem(), farmland, e.getBlockFace());
                    this.getServer().getPluginManager().callEvent(trampleEvent);
                    if (trampleEvent.useInteractedBlock() == Event.Result.ALLOW) {
                        // Calculate the state of the Farmland after being trampled.
                        BlockState state = farmland.getState();
                        state.setType(Material.DIRT);
                        // Send a BlockFadeEvent to indicate the Farmland turning to Dirt.
                        UpgradedBlockFadeEvent fadeEvent = new UpgradedBlockFadeEvent(farmland, state);
                        this.getServer().getPluginManager().callEvent(fadeEvent);
                        if (!fadeEvent.isCancelled()) {
                            // Move the player up from the Farmland so they do not get stuck in the Dirt.
                            Player player = e.getPlayer();
                            Location location = player.getLocation();
                            Location blockLocation = farmland.getRelative(0, 1, 0).getLocation();
                            if (location.getY() < blockLocation.getY()) {
                                location.setY(blockLocation.getY());
                                player.teleport(location);
                            }
                            state.update(true);
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCropGrow(BlockGrowEvent e) {
        if (!e.isCancelled()) {
            Block below = e.getBlock().getRelative(0, -1, 0);
            if (below.getType() == Material.FARMLAND) {
                farmlandUpgradedChangeMoisture(below);
            }
        }
    }

    /**
     * A BlockFadeEvent is called for a Farmland block with a moisture level of 0 when Minecraft
     * wants to turn it into Dirt. It is also called when a player tramples (jumps on) a Farmland block.
     * We do not want Farmland to dry into Dirt so we will cancel this event for Farmland.
     *
     * @param e The event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFarmlandDry(BlockFadeEvent e) {
        if (!(e instanceof UpgradedBlockFadeEvent)) {
            // Check that the fading block is farmland.
            Block farmland = e.getBlock();
            if (farmland.getType() == Material.FARMLAND) {
                // Check if using upgraded hydration mechanics.
                if (this.getConfig().getBoolean(CONFIGURATION_HYDRATION_UPGRADE, true)) {
                    e.setCancelled(true);
                    farmlandUpgradedChangeMoisture(farmland);
                } else {
                    Block above = farmland.getRelative(0, 1, 0);
                    if (!configDryFarmland()) {
                        if (cropMaterials.containsKey(above.getType()) || above.getType() == Material.AIR) {
                            e.setCancelled(true);
                        }
                    }
                }
            }
        }
    }

    /**
     * Overwrite the Vanilla mechanics by cancelling all Farmland MoistureChangeEvents and
     * implementing a custom handler.
     *
     * @param e
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFarmlandMoistureChange(MoistureChangeEvent e) {
        if (!(e instanceof UpgradedMoistureChangeEvent)) {
            if (this.getConfig().getBoolean(CONFIGURATION_HYDRATION_UPGRADE, true)) {
                Block block = e.getBlock();
                if (block.getType() == Material.FARMLAND) {
                    e.setCancelled(true);
                    farmlandUpgradedChangeMoisture(block);
                }
            }
        }
    }

    public void farmlandUpgradedChangeMoisture(Block farmland) {
        if (farmland.getType() == Material.FARMLAND) {
            BlockState state = farmland.getState();
            farmlandDetermineUpgradedMoisture(state);
            if (state.getType() == Material.FARMLAND) {
                UpgradedMoistureChangeEvent event = new UpgradedMoistureChangeEvent(farmland, state);
                this.getServer().getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    state.update(true);
                }
            } else {
                UpgradedBlockFadeEvent event = new UpgradedBlockFadeEvent(farmland, state);
                this.getServer().getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    state.update(true);
                }
            }
        }
    }

    public void farmlandDetermineUpgradedMoisture(BlockState state) {
        Material aboveType = state.getBlock().getRelative(0, 1, 0).getType();
        if (aboveType == Material.AIR || cropMaterials.containsKey(aboveType)) {
            ConfigurationSection configuration = this.getConfig();
            int range = configuration.getInt(CONFIGURATION_HYDRATION_RANGE, 4);
            int depth = configuration.getInt(CONFIGURATION_HYDRATION_DEPTH, 2);
            if (isHydrated(state.getBlock(), range, 0, -depth)) {
                BlockData data = state.getBlockData();
                Farmland farmland = (Farmland) data;
                farmland.setMoisture(Math.min(farmland.getMoisture() + 1, farmland.getMaximumMoisture()));
                state.setBlockData(data);
            } else {
                BlockData data = state.getBlockData();
                Farmland farmland = (Farmland) data;
                if (farmland.getMoisture() <= 0) {
                    if (configDryFarmland()) {
                        state.setType(Material.DIRT);
                    }
                } else {
                    farmland.setMoisture(Math.max(farmland.getMoisture() - 1, 0));
                    state.setBlockData(data);
                }
            }
        } else {
            state.setType(Material.DIRT);
        }
    }

    /**
     * Calculate the drops from a broken crop. (Currently not used).
     *
     * @param itemStack The tool used to harvest the crop.
     * @param block The crop.
     * @param replant If the crop is replanted, one seed is removed from the drops.
     * @return The drops of this crop.
     */
    public Set<ItemStack> cropCalculateDrops(ItemStack itemStack, Block block, boolean replant) {
        Set<ItemStack> drops = new HashSet<>();
        // If broken with a hoe, Fortune is applied.
        boolean hoe = itemStack != null && toolMaterials.containsKey(itemStack.getType());
        Optional<Boolean> maybeGrown = isGrown(block);
        boolean grown = maybeGrown.isPresent() && maybeGrown.get() == true;
        int fortune = itemStack != null ? itemStack.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS) : 0;
        Material cropType = block.getType();
        // Beetroots
        if (cropType == Material.BEETROOTS) {
            int beetroots = 0;
            int beetrootSeeds = 1;
            if (replant) {
                beetrootSeeds--;
            }
            if (grown) {
                beetroots++;
                if (hoe) {
                    double beetrootChance = 0.25 + Math.min(0.15 * fortune, 0.45);
                    if (random.nextFloat() < beetrootChance) {
                        beetroots++;
                    }
                    // Chance to get an extra beetroot seed.
                    // Fortune 0: 0.5%, Fortune 1: 1%, Fortune 2: 1.5%, Fortune >=3: 2%.
                    double seedChance = 0.005 + Math.min(0.005 * fortune, 0.015);
                    if (random.nextFloat() < seedChance) {
                        beetrootSeeds++;
                    }
                } else {
                    double beetrootChance = 0.25;
                    if (random.nextFloat() < beetrootChance) {
                        beetroots++;
                    }
                    double seedChance = 0.005;
                    if (random.nextFloat() < seedChance) {
                        beetrootSeeds++;
                    }
                }
            }
            if (beetroots > 0) {
                drops.add(new ItemStack(Material.BEETROOT, beetroots));
            }
            if (beetrootSeeds > 0) {
                drops.add(new ItemStack(Material.BEETROOT_SEEDS, beetrootSeeds));
            }
        // Carrots
        } else if (cropType == Material.CARROTS) {
            int carrots = 1;
            if (replant) {
                carrots--;
            }
            if (grown) {
                if (hoe) {
                    double chance = 0.40 + Math.min(0.20 * fortune, 0.60);
                    if (random.nextFloat() < chance) {
                        carrots++;
                    }
                } else {
                    double chance = 0.40;
                    if (random.nextFloat() < chance) {
                        carrots++;
                    }
                }
            }
            if (carrots > 0) {
                drops.add(new ItemStack(Material.CARROT, carrots));
            }
        // Netherwart
        } else if (cropType == Material.NETHER_WART) {
            int netherwart = 1;
            if (replant) {
                netherwart--;
            }
            if (grown) {
                if (hoe) {
                    double chance = 0.40 + Math.min(0.10 * fortune, 0.30);
                    if (random.nextFloat() < chance) {
                        netherwart++;
                    }
                } else {
                    double chance = 0.40;
                    if (random.nextFloat() < chance) {
                        netherwart++;
                    }
                }
            }
            if (netherwart > 0) {
                drops.add(new ItemStack(Material.NETHER_WART, netherwart));
            }
        // Carrots
        } else if (cropType == Material.POTATOES) {
            int potatoes = 1;
            if (replant) {
                potatoes--;
            }
            if (grown) {
                if (hoe) {
                    double chance = 0.60 + Math.min(0.10 * fortune, 0.30);
                    if (random.nextFloat() < chance) {
                        potatoes++;
                    }
                } else {
                    double chance = 0.60;
                    if (random.nextFloat() < chance) {
                        potatoes++;
                    }
                }
            }
            if (potatoes > 0) {
                drops.add(new ItemStack(Material.POTATO, potatoes));
            }
        // Wheat
        } else if (cropType == Material.WHEAT) {
            int wheat = 0;
            int seeds = 1;
            if (replant) {
                seeds--;
            }
            if (grown) {
                wheat++;
                if (hoe) {
                    double wheat2Chance = 0.50 + Math.min(0.50 * fortune, 0.50);
                    if (random.nextFloat() < wheat2Chance) {
                        wheat++;
                    }
                    double wheat3Chance = 0.10 + Math.min(0.10 * fortune, 0.10);
                    if (random.nextFloat() < wheat3Chance) {
                        wheat++;
                    }
                    double wheat4Chance = Math.min(0.10 * fortune, 0.30);
                    if (random.nextFloat() < wheat4Chance) {
                        wheat++;
                    }
                    double seedChance = 0.05 + Math.min(0.10 * fortune, 0.30);
                    if (random.nextFloat() < seedChance) {
                        seeds++;
                    }
                } else {
                    double wheat2Chance = 0.50;
                    if (random.nextFloat() < wheat2Chance) {
                        wheat++;
                    }
                    double wheat3Chance = 0.10;
                    if (random.nextFloat() < wheat3Chance) {
                        wheat++;
                    }
                    double seedChance = 0.05;
                    if (random.nextFloat() < seedChance) {
                        seeds++;
                    }
                }
            }
            if (wheat > 0) {
                drops.add(new ItemStack(Material.WHEAT, wheat));
            }
            if (seeds > 0) {
                drops.add(new ItemStack(Material.WHEAT_SEEDS, seeds));
            }
        }
        return drops;
    }

    /**
     * Determine if a block has water in its vicinity.
     *
     * @param block The centre block.
     * @param radius The horizontal radius (of a square circle) to search within.
     * @param highest The greatest height to search in, relative to the block.
     * @param lowest The lowest height to search in, relative to the block.
     * @return If there is water in the searched region.
     */
    public boolean isHydrated(Block block, int radius, int highest, int lowest) {
        int i = -radius;
        while (i <= radius) {
            int k = -radius;
            while (k <= radius) {
                int j = lowest;
                while (j <= highest) {
                    if (block.getRelative(i, j, k).getType() == Material.WATER) {
                        return true;
                    }
                    j++;
                }
                k++;
            }
            i++;
        }
        return false;
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
        if (this.getConfig().getBoolean(CONFIGURATION_HOE_UPGRADE, true)) {
            if (!(e instanceof UpgradedBlockBreakEvent)) {
                Player player = e.getPlayer();
                Block block = e.getBlock();
                // Handle breaking of crops.
                if (cropMaterials.containsKey(block.getType())) {
                    ItemStack tool = player.getInventory().getItemInMainHand();
                    // If broken by a hoe, all crops within range are harvested and automatically replanted.
                    if (toolMaterials.containsKey(tool.getType())) {
                        e.setCancelled(true);
                        // Calculate the radius of the hoe sweep.
                        // Radius is affected by hoe material and the Efficiency enchantment (1 radius per 2 levels).
                        int range = Math.min(7, toolMaterials.get(tool.getType()) + tool.getEnchantmentLevel(Enchantment.DIG_SPEED) / 2);
                        // Get the adjacent crops in range.
                        Set<Block> adjacentCrops = new HashSet<>();
                        adjacentCrops.add(block);
                        int radius = 1;
                        while (radius <= range) {
                            adjacentCrops.addAll(findRadiallyAdjacentMaterials(cropMaterials.keySet(), block, radius));
                            radius++;
                        }
                        // For every crop block, simulate a BlockBreakEvent for other plugins to react to.
                        for (Block crop : adjacentCrops) {
                            // Only break crops that are fully grown.
                            Optional<Boolean> grown = isGrown(crop);
                            if (grown.isPresent() && grown.get() == true) {
                                // Call an event that is handled by the plugin.
                                UpgradedBlockBreakEvent sweepEvent = new UpgradedBlockBreakEvent(crop, player);
                                this.getServer().getPluginManager().callEvent(sweepEvent);
                                if (!sweepEvent.isCancelled()) {
                                    // Get the state of the harvested crop.
                                    BlockState state = crop.getState();
                                    Collection<ItemStack> itemDrops = crop.getDrops(tool);
                                    // Break the crop and spawn effects.
                                    breakBlockEffect(crop, state, Sound.BLOCK_CROP_BREAK);
                                    crop.setType(Material.AIR);
                                    // Damage the player's tool if they are in survival.
                                    damageTool(player, tool, 1);
                                    boolean replant = false;
                                    GameMode mode = player.getGameMode();
                                    if (mode == GameMode.CREATIVE && this.getConfig().getBoolean(CONFIGURATION_HOE_REPLANT, true)) {
                                        replant = true;
                                    }
                                    if (sweepEvent.isDropItems() && mode != GameMode.CREATIVE) {
                                        // Calculate the drops from the crop.
                                        List<Item> drops = new ArrayList<>();
                                        Material seed = cropMaterials.get(state.getType()).getSeed();
                                        for (ItemStack itemDrop : itemDrops) {
                                            // If replanting is enabled, and a seed is not found yet, search for one in this ItemStack.
                                            if (this.getConfig().getBoolean(CONFIGURATION_HOE_REPLANT, true) && !replant) {
                                                int amount = itemDrop.getAmount();
                                                if (itemDrop.getType() == seed && amount >= 1) {
                                                    itemDrop.setAmount(amount - 1);
                                                    replant = true;
                                                }
                                            }
                                            if (itemDrop.getType().isItem() && itemDrop.getAmount() >= 1) {
                                                drops.add(crop.getWorld().dropItemNaturally(crop.getLocation(), itemDrop));
                                            }
                                        }
                                        // Drop the items and send an event.
                                        List<Item> copy = Lists.newArrayList(drops);
                                        BlockDropItemEvent dropEvent = new BlockDropItemEvent(crop, state, player, copy);
                                        this.getServer().getPluginManager().callEvent(dropEvent);
                                        // Clear all items from the copy if the event is cancelled.
                                        if (dropEvent.isCancelled()) {
                                            copy.clear();
                                        }
                                        // Kill those items that were removed from the copied drop list.
                                        for (Item drop : drops) {
                                            if (!copy.contains(drop)) {
                                                drop.remove();
                                            }
                                        }
                                    }
                                    // Use a seed to replant the crop.
                                    if (replant) {
                                        crop.setType(state.getType());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void breakBlockEffect(Location location, BlockState state, Sound sound) {
        location.getWorld().spawnParticle(Particle.BLOCK_CRACK, location, 50, 0.5, 0.5, 0.1, state.getBlockData());
        location.getWorld().playSound(location, sound, 1, 1);
    }

    public void breakBlockEffect(Block block, BlockState state, Sound sound) {
        breakBlockEffect(block.getLocation().clone().add(0.5, 0.5, 0.5), state, sound);
    }

    /**
     * Apply damage to a player's item. Does nothing if the item is not Damageable.
     * Also checks that the Player is in Survival or Adventure mode before applying damage.
     *
     * @param player The player whose tool might take damage.
     * @param tool The item to take damage.
     * @param damage The amount of damage to apply.
     */
    public void damageTool(Player player, ItemStack tool, int damage) {
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE || tool.getItemMeta().isUnbreakable()) {
            // Calculate the chance of the tool taking damage with the standard Minecraft formula,
            // which depends on the level of the Unbreaking enchantment.
            double damageChance = 1.0 / (tool.getEnchantmentLevel(Enchantment.DURABILITY) + 1.0);
            if (random.nextFloat() < damageChance) {
                boolean destroyed = damageTool(tool, damage);
                if (destroyed) {
                    player.spawnParticle(Particle.ITEM_CRACK, player.getLocation(), 1, tool);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1, 1);
                    tool.setAmount(tool.getAmount() - 1);
                }
            }
        }
    }

    /**
     * Apply damage to a tool. Does nothing if the item is not Damageable.
     * If a player is in creative mode, their tools probably should not take damage.
     *
     * @param tool The item to take damage.
     * @param damage The amount of damage to apply.
     * @return If the tool reaches zero or less durability.
     */
    public boolean damageTool(ItemStack tool, int damage) {
        ItemMeta meta = tool.getItemMeta();
        if (meta instanceof Damageable) {
            Damageable damageable = (Damageable) meta;
            damageable.setDamage(damageable.getDamage() + damage);
            tool.setItemMeta(meta);
            return damageable.getDamage() >= tool.getType().getMaxDurability();
        }
        return false;
    }

    /**
     * Check if a block is fully grown.
     *
     * @param block The block.
     * @return If the block is fully grown or not. Empty if the block is not Ageable.
     */
    public Optional<Boolean> isGrown(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Ageable) {
            Ageable ageable = (Ageable) data;
            return Optional.of(ageable.getAge() == ageable.getMaximumAge());
        } else {
            return Optional.empty();
        }
    }

    /**
     * In a ring centred on a block with the given radius, locate the blocks whose material is one of the given types.
     *
     * @param materials The materials to search for.
     * @param centre The centre block.
     * @param radius The radius of the ring.
     * @return The blocks in the ring whose material matches one of those searched for.
     */
    public Set<Block> findRadiallyAdjacentMaterials(Collection<Material> materials, Block centre, int radius) {
        Set<Block> ring = new HashSet<>();
        // Centre coordinates.
        World world = centre.getWorld();
        int x = centre.getX();
        int y = centre.getY();
        int z = centre.getZ();
        // Relative to centre coordinates.
        int i = -radius;
        int k = -radius;
        // Iterate over the columns in the given radius.
        while (i < radius) {
            Optional<Block> block = findHighestMaterial(materials, world, x + i, z + k, y + radius, y - radius);
            block.ifPresent(ring::add);
            i++;
        }
        while (k < radius) {
            Optional<Block> block = findHighestMaterial(materials, world, x + i, z + k, y + radius, y - radius);
            block.ifPresent(ring::add);
            k++;
        }
        while (i > -radius) {
            Optional<Block> block = findHighestMaterial(materials, world, x + i, z + k, y + radius, y - radius);
            block.ifPresent(ring::add);
            i--;
        }
        while (k > -radius) {
            Optional<Block> block = findHighestMaterial(materials, world, x + i, z + k, y + radius, y - radius);
            block.ifPresent(ring::add);
            k--;
        }
        return ring;
    }

    /**
     * Find the highest block that has a material of one of the given types.
     *
     * @param materials The materials to search for.
     * @param world The world to search in.
     * @param x Block x coordinate.
     * @param z Block z coordinate.
     * @param highest Highest y coordinate.
     * @param lowest Lowest y coordinate.
     * @return The highest block of one of the materials in the column, otherwise empty.
     */
    public Optional<Block> findHighestMaterial(Collection<Material> materials, World world, int x, int z, int highest, int lowest) {
        int y = highest;
        while (y >= lowest) {
            Block block = world.getBlockAt(x, y, z);
            if (materials.contains(block.getType())) {
                return Optional.of(block);
            }
            y--;
        }
        return Optional.empty();
    }

    private boolean configDryFarmland() {
        return this.getConfig().getBoolean(CONFIGURATION_DRY_FARMLAND, false);
    }

}
