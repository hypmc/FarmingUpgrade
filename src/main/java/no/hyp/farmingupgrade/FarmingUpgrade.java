package no.hyp.farmingupgrade;

import com.google.common.collect.Lists;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public final class FarmingUpgrade extends JavaPlugin implements Listener {

    Thread watcherThread;

    HarvestListener harvestListener;

    HydrationListener hydrationListener;

    FertiliseListener fertiliseListener;

    TrampleListener trampleListener;

    Random random;

    /**
     * A map of hoes and their ranges.
     */
    Map<Material, Integer> tools;

    /**
     * Crops that are harvested by being broken and replanted with a seed.
     */
    Map<Material, Material> harvestableCrops;

    Map<Material, Consumer<BlockState>> fertilisableCrops;

    boolean configurationHydrationUpgrade() {
        return this.getConfig().getBoolean("hydrationUpgrade.enabled", false);
    }

    int configurationHydrationRange() {
        return this.getConfig().getInt("hydrationUpgrade.range", 4);
    }

    int configurationHydrationDepth() {
        return this.getConfig().getInt("hydrationUpgrade.depth", 2);
    }

    int configurationHydrationHeight() {
        return this.getConfig().getInt("hydrationUpgrade.height", 0);
    }

    boolean configurationHydrationDry() {
        return this.getConfig().getBoolean("hydrationUpgrade.dry", false);
    }

    boolean configurationHoeUpgrade() {
        return this.getConfig().getBoolean("hoeUpgrade.enabled", true);
    }

    boolean configurationHoeRange() {
        return this.getConfig().getBoolean("hoeUpgrade.range", true);
    }

    boolean configurationHoeEfficiency() {
        return this.getConfig().getBoolean("hoeUpgrade.efficiency", true);
    }

    boolean configurationHoeUnbreaking() {
        return this.getConfig().getBoolean("hoeUpgrade.unbreaking", true);
    }

    boolean configurationHoeHarvest() {
        return this.getConfig().getBoolean("hoeUpgrade.harvest", true);
    }

    boolean configurationHoeReplant() {
        return this.getConfig().getBoolean("hoeUpgrade.replant", true);
    }

    boolean configurationHoeCollect() {
        return this.getConfig().getBoolean("hoeUpgrade.collect", false);
    }

    boolean configurationFertiliserUpgrade() {
        return this.getConfig().getBoolean("fertiliserUpgrade.enabled", true);
    }

    boolean configurationTrampleUpgrade() {
        return this.getConfig().getBoolean("trampleUpgrade.enabled", true);
    }

    @Override
    public void onEnable() {
        this.random = new Random();
        // Set hoe properties.
        this.tools = new HashMap<>();
        this.tools.put(Material.WOODEN_HOE, 0);
        this.tools.put(Material.STONE_HOE, 0);
        this.tools.put(Material.GOLDEN_HOE, 1);
        this.tools.put(Material.IRON_HOE, 1);
        this.tools.put(Material.DIAMOND_HOE, 2);
        this.tools.put(Material.NETHERITE_HOE, 2);
        // Set harvestable crop properties.
        this.harvestableCrops = new HashMap<>();
        this.harvestableCrops.put(Material.WHEAT, Material.WHEAT_SEEDS);
        this.harvestableCrops.put(Material.POTATOES, Material.POTATO);
        this.harvestableCrops.put(Material.CARROTS, Material.CARROT);
        this.harvestableCrops.put(Material.BEETROOTS, Material.BEETROOT_SEEDS);
        this.harvestableCrops.put(Material.NETHER_WART, Material.NETHER_WART);
        // Set fertilisable crop properties.
        Consumer<BlockState> commonFertilise = (state) -> trialGrow(this.random, 8, 0.10, state);
        Consumer<BlockState> beetrootFertilise = (state) -> trialGrow(this.random, 4, 0.05, state);
        this.fertilisableCrops = new HashMap<>();
        this.fertilisableCrops.put(Material.WHEAT, commonFertilise);
        this.fertilisableCrops.put(Material.POTATOES, commonFertilise);
        this.fertilisableCrops.put(Material.CARROTS, commonFertilise);
        this.fertilisableCrops.put(Material.BEETROOTS, beetrootFertilise);
        this.fertilisableCrops.put(Material.PUMPKIN_STEM, commonFertilise);
        this.fertilisableCrops.put(Material.MELON_STEM, commonFertilise);
        // Load the configuration. Save the default config if it does not exist.
        this.saveDefaultConfig();
        this.reloadConfig();
        // Upgrade the configuration if necessary.
        this.configurationUpgrade();
        // Enable configuration watcher.
        this.watcherEnable();
        // Register the event listeners.
        this.getServer().getPluginManager().registerEvents(this, this);
        // Set listeners.
        this.harvestListener = new HarvestListener(this, this.random);
        this.hydrationListener = new HydrationListener(this);
        this.fertiliseListener = new FertiliseListener(this, this.random);
        this.trampleListener = new TrampleListener(this);
        this.getServer().getPluginManager().registerEvents(harvestListener, this);
        this.getServer().getPluginManager().registerEvents(hydrationListener, this);
        this.getServer().getPluginManager().registerEvents(fertiliseListener, this);
        this.getServer().getPluginManager().registerEvents(trampleListener, this);
    }

    @Override
    public void onDisable() {
        this.watcherDisable();
    }

    /**
     * Upgrade configuration.
     */
    public void configurationUpgrade() {
        int version = this.getConfig().getInt("version");
        if (version == 1) {
            // Save old config.
            Path directory = this.getDataFolder().toPath();
            File config = directory.resolve("config.yml").toFile();
            File save = directory.resolve("config.old.1.yml").toFile();
            config.renameTo(save);
            // Delete old config.
            config.delete();
            // Save the new default configuration.
            this.saveDefaultConfig();
            this.reloadConfig();
        }
    }

    /**
     * Start/restart the configuration watcher thread.
     */
    public void watcherEnable() {
        this.watcherDisable();
        Path pluginDirectory = this.getDataFolder().toPath();
        this.watcherThread = new Thread(new ConfigurationWatcher(this, pluginDirectory));
        watcherThread.start();
    }

    /**
     * Stop the configuration watcher thread.
     */
    public void watcherDisable() {
        if (this.watcherThread != null) {
            this.watcherThread.interrupt();
        }
    }

    public Map<Material, Integer> getTools() {
        return this.tools;
    }

    public Map<Material, Material> getHarvestableCrops() {
        return this.harvestableCrops;
    }

    public Map<Material, Consumer<BlockState>> getFertilisableCrops() {
        return this.fertilisableCrops;
    }

    /**
     * Use Bernoulli trials to determine how many growth stages to add to a crop.
     */
    public static void trialGrow(Random random, int trials, double probability, BlockState state) {
        BlockData data = state.getBlockData();
        if (data instanceof Ageable) {
            // Run Bernoulli trials to determine growth stage increase.
            int stages = 0;
            int i = 0;
            while (i < trials) {
                if (random.nextDouble() < probability) {
                    stages++;
                }
                i++;
            }
            // Add the growth stages to the state.
            Ageable ageable = (Ageable) data;
            ageable.setAge(Math.min(ageable.getAge() + stages, ageable.getMaximumAge()));
        }
        state.setBlockData(data);
    }

    public static void farmlandUpgradedChangeMoisture(Block farmland, int range, int depth, int height, boolean dry) {
        if (farmland.getType() == Material.FARMLAND) {
            BlockState state = farmland.getState();
            farmlandDetermineUpgradedMoisture(state, range, depth, height, dry);
            PluginManager manager = Bukkit.getPluginManager();
            if (state.getType() == Material.FARMLAND) {
                UpgradedMoistureChangeEvent event = new UpgradedMoistureChangeEvent(farmland, state);
                manager.callEvent(event);
                if (!event.isCancelled()) {
                    state.update(true);
                }
            } else {
                UpgradedBlockFadeEvent event = new UpgradedBlockFadeEvent(farmland, state);
                manager.callEvent(event);
                if (!event.isCancelled()) {
                    state.update(true);
                }
            }
        }
    }

    public static void farmlandDetermineUpgradedMoisture(BlockState state, int range, int depth, int height, boolean dry) {
        Material aboveType = state.getBlock().getRelative(0, 1, 0).getType();
        if (!aboveType.isOccluding()) {
            if (isHydrated(Lists.newArrayList(Material.WATER), state.getBlock(), range, height, -depth)) {
                BlockData data = state.getBlockData();
                Farmland farmland = (Farmland) data;
                farmland.setMoisture(Math.min(farmland.getMoisture() + 1, farmland.getMaximumMoisture()));
                state.setBlockData(data);
            } else {
                BlockData data = state.getBlockData();
                Farmland farmland = (Farmland) data;
                if (farmland.getMoisture() <= 0) {
                    if (dry) {
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

    public static void breakBlockEffect(Block block, BlockState state, Sound sound) {
        Location location = block.getLocation().add(0.5, 0.5, 0.5);
        location.getWorld().spawnParticle(Particle.BLOCK_CRACK, location, 25, 0.5, 0.5, 0.5, state.getBlockData());
        location.getWorld().playSound(location, sound, 1, 1);
    }

    public static void fertiliseEffect(Block block) {
        Location location = block.getLocation().add(0.5, 0.5, 0.375);
        location.getWorld().spawnParticle(Particle.COMPOSTER, location, 15, 0.4, 0.4, 0.35);
    }

    /**
     * Apply damage to a player's item. Does nothing if the item is not Damageable.
     * Also checks that the Player is in Survival or Adventure mode before applying damage.
     *
     * @param player The player whose tool might take damage.
     * @param tool The item to take damage.
     * @param damage The amount of damage to apply.
     */
    public static void damageTool(Random random, Player player, ItemStack tool, int damage) {
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE || tool.getItemMeta().isUnbreakable()) {
            // Calculate the chance of the tool taking damage with the standard Minecraft formula,
            // which depends on the level of the Unbreaking enchantment.
            double damageChance = 1.0 / (tool.getEnchantmentLevel(Enchantment.DURABILITY) + 1.0);
            if (random.nextFloat() < damageChance) {
                damageTool(player, tool, damage);
            }
        }
    }

    public static void damageTool(Player player, ItemStack tool, int damage) {
        boolean destroyed = damageTool(tool, damage);
        if (destroyed) {
            player.spawnParticle(Particle.ITEM_CRACK, player.getLocation(), 1, tool);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1, 1);
            tool.setAmount(tool.getAmount() - 1);
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
    public static boolean damageTool(ItemStack tool, int damage) {
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
    public static Optional<Boolean> isGrown(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Ageable) {
            Ageable ageable = (Ageable) data;
            return Optional.of(ageable.getAge() == ageable.getMaximumAge());
        } else {
            return Optional.empty();
        }
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
    public static boolean isHydrated(Collection<Material> materials, Block block, int radius, int highest, int lowest) {
        int i = -radius;
        while (i <= radius) {
            int k = -radius;
            while (k <= radius) {
                int j = lowest;
                while (j <= highest) {
                    Block searchBlock = block.getRelative(i, j, k);
                    // The Farmland is hydrated if there is water or a waterlogged block in the search region.
                    if (materials.contains(searchBlock.getType())) {
                        return true;
                    } else {
                        BlockData data = searchBlock.getBlockData();
                        if (data instanceof Waterlogged) {
                            Waterlogged waterlogged = (Waterlogged) data;
                            if (waterlogged.isWaterlogged()) {
                                return true;
                            }
                        }
                    }
                    j++;
                }
                k++;
            }
            i++;
        }
        return false;
    }

    public static Set<Block> findAdjacentMaterials(Collection<Material> materials, Block centre, int radius) {
        Set<Block> adjacent = new HashSet<>();
        adjacent.add(centre);
        int r = 1;
        while (r <= radius) {
            adjacent.addAll(findAdjacentMaterialsRadius(materials, centre, r));
            r++;
        }
        return adjacent;
    }

    /**
     * In a ring centred on a block with the given radius, locate the blocks whose material is one of the given types.
     *
     * @param materials The materials to search for.
     * @param centre The centre block.
     * @param radius The radius of the ring.
     * @return The blocks in the ring whose material matches one of those searched for.
     */
    public static Set<Block> findAdjacentMaterialsRadius(Collection<Material> materials, Block centre, int radius) {
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
    public static Optional<Block> findHighestMaterial(Collection<Material> materials, World world, int x, int z, int highest, int lowest) {
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

    public static void harvestCrop(Random random, Player player, Block crop, ItemStack tool, boolean replant, boolean unbreaking, boolean collect, boolean harvest, Material seed) {
        // If harvest is enabled, only break crops that are fully grown.
        if (harvest) {
            Optional<Boolean> grown = isGrown(crop);
            if (!(grown.isPresent() && grown.get())) {
                return;
            }
        }
        // Call an event that is handled by the plugin.
        UpgradedBlockBreakEvent upgradedEvent = new UpgradedBlockBreakEvent(crop, player);
        Bukkit.getServer().getPluginManager().callEvent(upgradedEvent);
        if (!upgradedEvent.isCancelled()) {
            // A state representing the crop after the harvest.
            BlockState state = crop.getState();
            // Calculate drops depending on tool.
            Collection<ItemStack> itemDrops = crop.getDrops(tool);
            // Break the crop and spawn effects.
            breakBlockEffect(crop, state, Sound.BLOCK_CROP_BREAK);
            crop.setType(Material.AIR);
            // Damage the player's tool if they are in survival. Use Unbreaking
            // enchantment if it is enabled.
            if (unbreaking) {
                damageTool(random, player, tool, 1);
            } else {
                damageTool(player, tool, 1);
            }
            // Whether a seed has been collected for replanting.
            boolean seedFound = false;
            GameMode mode = player.getGameMode();
            // Drop items in survival.
            if (upgradedEvent.isDropItems() && mode != GameMode.CREATIVE) {
                // Search for a seed to replant the crop with if replanting is enabled.
                if (replant) {
                    for (ItemStack itemDrop : itemDrops) {
                        // If replanting is enabled, and a seed is not found yet, search for one in this ItemStack.
                        if (!seedFound) {
                            int amount = itemDrop.getAmount();
                            if (itemDrop.getType() == seed && amount >= 1) {
                                itemDrop.setAmount(amount - 1);
                                seedFound = true;
                            }
                        }
                    }
                }
                // If collect is enabled, items are sent to the inventory if there is space.
                if (collect) {
                    Inventory inventory = player.getInventory();
                    Set<ItemStack> notAddedItems = new HashSet<>();
                    for (ItemStack item : itemDrops) {
                        notAddedItems.addAll(inventory.addItem(item).values());
                    }
                    itemDrops = notAddedItems;
                    //player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.2f, 1.0f + random.nextFloat());
                }
                // Calculate the dropped item entities.
                List<Item> drops = new ArrayList<>();
                for (ItemStack itemDrop : itemDrops) {
                    if (itemDrop.getType().isItem() && itemDrop.getAmount() >= 1) {
                        drops.add(crop.getWorld().dropItemNaturally(crop.getLocation(), itemDrop));
                    }
                }
                // Send a BlockDropItemEvent for the drops.
                List<Item> copy = Lists.newArrayList(drops);
                BlockDropItemEvent dropEvent = new BlockDropItemEvent(crop, state, player, copy);
                Bukkit.getServer().getPluginManager().callEvent(dropEvent);
                // Kill those items that were removed from the copied drop list, or all of them
                // if the event is cancelled.
                if (dropEvent.isCancelled()) {
                    copy.clear();
                }
                for (Item drop : drops) {
                    if (!copy.contains(drop)) {
                        drop.remove();
                    }
                }
            }
            // A crop is always replanted in creative mode
            if (mode == GameMode.CREATIVE && replant) {
                seedFound = true;
            }
            // Replant the crop if the conditions are satisfied.
            if (seedFound) {
                crop.setType(state.getType());
            }
        }
    }

}
