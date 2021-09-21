package no.hyp.farmingupgrade;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class FarmingUpgradePlugin extends JavaPlugin implements Listener {

    FarmingUpgradeConfiguration configuration;

    Random random;

    /*
     * The plugin replaces Vanilla features by catching events, cancelling them, and then executing its own logic. The
     * plugin calls its own events in this logic, whose types matches those caught by the plugin. To prevent the plugin
     * from catching its own events, these booleans are set when the events are being called.
     */

    boolean callingBlockBreakEvent;

    boolean callingBlockFadeEvent;

    boolean callingFertiliseEvent;

    boolean callingMoistureChangeEvent;

    boolean callingPlayerInteractEvent;

    /*
     * Plugin
     */

    @Override
    public void onEnable() {
        // Save the default config if it does not exist.
        this.saveDefaultConfig();
        // Upgrade the configuration if necessary.
        this.configurationUpgrade();
        this.configuration = new FarmingUpgradeConfiguration(this.getConfig());
        this.random = new Random();
        // Register the event listeners.
        this.getServer().getPluginManager().registerEvents(this, this);
        // Set listeners.
        this.callingBlockBreakEvent = false;
        this.callingBlockFadeEvent = false;
        this.callingFertiliseEvent = false;
        this.callingMoistureChangeEvent = false;
        this.callingPlayerInteractEvent = false;
    }

    @Override
    public void onDisable() { }

    /**
     * If the configuration file is of an old version, save it as another file and replace the configuration file
     * with the default new configuration.
     */
    public void configurationUpgrade() {
        int version = this.getConfig().getInt("version");
        if (version == 4) {
            return;
        } else {
            // Save old config.
            Path directory = this.getDataFolder().toPath();
            File config = directory.resolve("config.yml").toFile();
            File save = directory.resolve(String.format("config.old.%d.yml", version)).toFile();
            config.renameTo(save);
            // Delete old config.
            config.delete();
            // Save the new default configuration.
            this.saveDefaultConfig();
            this.reloadConfig();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] arguments) {
        if (!command.getName().equalsIgnoreCase("farmingupgrade")) return null;
        if (arguments.length == 1) {
            return ImmutableList.of("reload");
        } else {
            return ImmutableList.of();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] arguments) {
        if (!command.getName().equalsIgnoreCase("farmingupgrade")) return false;
        if (arguments.length == 1) {
            var subcommand = arguments[0];
            if (subcommand.equalsIgnoreCase("reload")) {
                this.reloadConfig();
                this.configuration = new FarmingUpgradeConfiguration(this.getConfig());
                sender.sendMessage("FarmingUpgrade configuration was reloaded.");
                return true;
            } else {
                sender.sendMessage(ChatColor.RESET + "/farmingupgrade reload" + ChatColor.RED + " - Reload the configuration.");
                return true;
            }
        } else {
            sender.sendMessage(ChatColor.RESET + "/farmingupgrade reload" + ChatColor.RED + " - Reload the configuration.");
            return true;
        }
    }

    /*
     * Configuration
     */

    FarmingUpgradeConfiguration configuration() {
        return configuration;
    }

    final record FarmingUpgradeConfiguration (

            boolean required,

            boolean harvestUpgrade,

            ImmutableList<HarvestToolType> harvestTools,

            double harvestRadiusPerEfficiencyLevel,

            boolean harvestApplyUnbreaking,

            boolean harvestOnlyMature,

            boolean harvestReplant,

            boolean harvestCollect,

            ImmutableList<ReplantableCrop> harvestCrops,

            boolean hydrationUpgrade,

            int hydrationHorizontalRadius,

            int hydrationUpperAltitude,

            int hydrationLowerAltitude,

            boolean hydrationDry,

            boolean bonemealUpgrade,

            ImmutableList<FertilisablePlant> bonemealPlants,

            boolean trampleUpgrade,

            boolean trampleWalk

    ) {

        FarmingUpgradeConfiguration(ConfigurationSection configuration) {
            this(
                    configuration.getBoolean("required"),
                    configuration.getBoolean("harvest.upgrade"),
                    readTools(configuration, "harvest.tools"),
                    configuration.getDouble("harvest.radiusPerEfficiencyLevel"),
                    configuration.getBoolean("harvest.applyUnbreaking"),
                    configuration.getBoolean("harvest.onlyMature"),
                    configuration.getBoolean("harvest.replant"),
                    configuration.getBoolean("harvest.collect"),
                    readCrops(configuration, "harvest.crops"),
                    configuration.getBoolean("hydrate.upgrade"),
                    configuration.getInt("hydrate.horizontalRadius"),
                    configuration.getInt("hydrate.upperAltitude"),
                    configuration.getInt("hydrate.lowerAltitude"),
                    configuration.getBoolean("hydrate.dry"),
                    configuration.getBoolean("fertilise.upgrade"),
                    readPlants(configuration, "fertilise.plants"),
                    configuration.getBoolean("trample.upgrade"),
                    configuration.getBoolean("trample.walk")
            );
        }

        static ImmutableList<HarvestToolType> readTools(ConfigurationSection configuration, String path) {
            var tools = new LinkedList<HarvestToolType>();
            var toolSectionMaps = (List<Map<?, ?>>) configuration.getList(path);
            for (var toolSectionMap : toolSectionMaps) {
                var toolSection = new MemoryConfiguration().createSection("section", toolSectionMap);
                @Nullable var materialString = toolSection.getString("material");
                @Nullable var material = materialString != null ? Material.matchMaterial(materialString) : null;
                @Nullable var lore = toolSection.getString("lore");
                var radius = toolSection.getDouble("radius");
                var damage = toolSection.getInt("damage");
                tools.add(new HarvestToolType(material, lore, radius, damage));
            }
            return ImmutableList.copyOf(tools);
        }

        static ImmutableList<ReplantableCrop> readCrops(ConfigurationSection configuration, String path) {
            var crops = new LinkedList<ReplantableCrop>();
            var cropSectionMaps = (List<Map<?, ?>>) configuration.getList(path);
            for (var cropSectionMap : cropSectionMaps) {
                var cropSection = new MemoryConfiguration().createSection("section", cropSectionMap);
                var cropName = cropSection.getString("crop");
                var cropMaterial = Material.matchMaterial(cropName);
                @Nullable var seedsName = cropSection.getString("seeds");
                @Nullable var seedsMaterial = seedsName != null ? Material.matchMaterial(seedsName) : null;
                crops.add(new ReplantableCrop(cropMaterial, seedsMaterial));
            }
            return ImmutableList.copyOf(crops);
        }

        static ImmutableList<FertilisablePlant> readPlants(ConfigurationSection configuration, String path) {
            var plants = new LinkedList<FertilisablePlant>();
            var plantSectionMaps = (List<Map<?, ?>>) configuration.getList(path);
            for (var plantSectionMap : plantSectionMaps) {
                var plantSection = new MemoryConfiguration().createSection("section", plantSectionMap);
                var plantName = plantSection.getString("plant");
                var plantMaterial = Material.matchMaterial(plantName);
                var growth = plantSection.getDouble("growth");
                plants.add(new FertilisablePlant(plantMaterial, growth));
            }
            return ImmutableList.copyOf(plants);
        }

        public Optional<HarvestToolType> toolType(ItemStack toolItem) {
            for (var toolType : harvestTools) {
                // If the type has a material, the item must be of the same material.
                @Nullable var typeMaterial = toolType.material();
                if (typeMaterial != null) {
                    if (typeMaterial != toolItem.getType()) {
                        continue;
                    }
                }
                // If the type has a lore, some line in the item lore must contain the type lore as a substring.
                @Nullable var lore = toolType.lore();
                if (lore != null) {
                    if (!toolItem.hasItemMeta()) continue;
                    @Nullable var itemLore = toolItem.getItemMeta().getLore();
                    if (itemLore == null) continue;
                    var foundLore = false;
                    for (var itemLoreLine : itemLore) {
                        if (itemLoreLine.contains(lore)) {
                            foundLore = true;
                            break;
                        }
                    }
                    if (!foundLore) continue;
                }
                return Optional.of(toolType);
            }
            return Optional.empty();
        }

        public boolean isCrop(Material type) {
            for (var crop : harvestCrops) {
                if (crop.crop() == type) return true;
            }
            return false;
        }

        public Collection<Material> cropMaterials() {
            return harvestCrops.stream().map(ReplantableCrop::crop).collect(Collectors.toUnmodifiableSet());
        }

        public Optional<Material> seeds(Material material) throws IllegalArgumentException {
            for (var crop : harvestCrops) {
                if (crop.crop() == material) {
                    return Optional.ofNullable(crop.seeds());
                }
            }
            throw new IllegalArgumentException(String.format("Material %s is not harvestable.", material.name()));
        }

        public boolean isFertilisable(Material material) {
            return bonemealPlants.stream().anyMatch(x -> x.plant() == material);
        }

        public Collection<Material> fertilisableMaterials() {
            return bonemealPlants.stream().map(FertilisablePlant::plant).collect(Collectors.toSet());
        }

        public double fertilisableGrowth(Material material) throws IllegalArgumentException {
            for (var plant : bonemealPlants) {
                if (plant.plant() == material) return plant.growth();
            }
            throw new IllegalArgumentException(String.format("Material %s is not fertilisable.", material.name()));
        }

    }

    /*
     * Harvest upgrade
     */

    /**
     * If a player breaks a crop with a hoe, handle this event manually. Find the adjacent crops
     * and call custom UpgradedBlockBreakEvents on the to give other plugins a chance to catch them.
     *
     * Since this is a mechanic the event should be caught and cancelled as early as possible (LOWEST priority).
     * Then we can dispatch new events that we can control the outcome of ourselves.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFarm(BlockBreakEvent event) {
        if (!configuration.harvestUpgrade()) return;
        if (callingBlockBreakEvent) return; // Do not handle events that are called by FarmingUpgrade.
        var player = event.getPlayer();
        var centre = event.getBlock();
        if (!configuration.isCrop(centre.getType())) return; // Farming only applies to crops.
        var toolItem = player.getInventory().getItemInMainHand();
        @Nullable var toolType = configuration().toolType(toolItem).orElse(null);
        if (toolType == null) return; // If the crop was not broken by a harvest tool, proceed with Vanilla mechanics.
        event.setCancelled(true); // Cancel the Vanilla event to cancel the Vanilla mechanics.
        initiateHarvest(player, toolType, toolItem, centre);
    }

    /**
     * Called when a player uses a harvest tool on a crop.
     */
    void initiateHarvest(Player player, HarvestToolType toolType, ItemStack toolItem, Block centre) {
        harvestSwingParticles(player);
        var toolDamage = toolType.damage();
        var radius = calculateRadius(toolType, toolItem);
        var cropMaterials = configuration().cropMaterials();
        var adjacentCropBlocks = findAdjacentMaterials(cropMaterials, centre, radius);
        var replant = configuration().harvestReplant();
        var applyUnbreaking = configuration().harvestApplyUnbreaking();
        var collect = configuration().harvestCollect();
        var onlyMature = configuration().harvestOnlyMature();
        for (var adjacentCropBlock : adjacentCropBlocks) {
            @Nullable var seeds = configuration().seeds(adjacentCropBlock.getType()).orElse(null);
            var harvested = harvestCrop(player, adjacentCropBlock, toolItem, replant, collect, onlyMature, seeds);
            if (harvested) {
                var destroyed = damageTool(random, player, toolItem, toolDamage, applyUnbreaking);
                if (destroyed) break; // Stop harvesting if the tool breaks.
            }
        }
    }

    /**
     * Create a harvest tool swing particle effect.
     */
    void harvestSwingParticles(Player player) {
        Vector direction = player.getLocation().getDirection();
        //direction.add(new Vector(0.0, -direction.getY(), 0.0)).multiply(1.0 / direction.length());
        Location location = player.getEyeLocation();
        location.add(direction.multiply(1.5));
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, location, 1);
    }

    int calculateRadius(HarvestToolType toolType, ItemStack toolItem) {
        var radius = 0.0;
        radius += toolType.radius();
        var efficiencyRangePerLevel = configuration().harvestRadiusPerEfficiencyLevel();
        radius += toolItem.getEnchantmentLevel(Enchantment.DIG_SPEED) * efficiencyRangePerLevel;
        radius = Math.min(10, radius); // Do not allow the radius to crash the server.
        return (int) radius;
    }

    /**
     * Called to harvest one of the crops within range.
     */
    boolean harvestCrop(Player player, Block block, ItemStack tool, boolean replant, boolean collect, boolean onlyMature, @Nullable Material seed) {
        // If harvest is enabled, only break crops that are mature.
        if (onlyMature) {
            var grown = isMature(block);
            if (!(grown.isPresent() && grown.get())) {
                return false;
            }
        }
        // Call an event that is handled by the plugin.
        BlockBreakEvent upgradedEvent = new BlockBreakEvent(block, player);
        callingBlockBreakEvent = true;
        Bukkit.getServer().getPluginManager().callEvent(upgradedEvent);
        callingBlockBreakEvent = false;
        if (upgradedEvent.isCancelled()) return false;
        // A state representing the crop after the harvest.
        BlockState state = block.getState();
        // Calculate drops depending on tool.
        Collection<ItemStack> itemDrops = block.getDrops(tool);
        // Break the crop and spawn effects.
        FarmingUpgradePlugin.breakBlockEffect(block, state, Sound.BLOCK_CROP_BREAK);
        block.setType(Material.AIR);

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
                if (itemDrop.getType().isItem() && itemDrop.getType() != Material.AIR && itemDrop.getAmount() >= 1) {
                    drops.add(block.getWorld().dropItemNaturally(block.getLocation(), itemDrop));
                }
            }
            // Send a BlockDropItemEvent for the drops.
            List<Item> copy = Lists.newArrayList(drops);
            BlockDropItemEvent dropEvent = new BlockDropItemEvent(block, state, player, copy);
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
        if (mode == GameMode.CREATIVE && replant) seedFound = true; // A crop is always replanted in creative mode.
        if (seedFound) block.setType(state.getType()); // Replant the crop if the conditions are satisfied.
        return true;
    }

    /*
     * Upgraded hydration
     */

    /**
     * TODO: Ugly hack
     *
     * When a player tramples farmland, Bukkit calls a PlayerInteractEvent, and then
     * a BlockFadeEvent. Minecraft also calls FadeEvent when a Farmland is turning to
     * Dirt from dehydration. To distinguish between trampling and drying, store the
     * block that is trampled in InteractEvent here and check if it equal in the FadeEvent.
     */
    Block trampledFarmland;

    /**
     * Use this event to detect if the next BlockFadeEvent is caused by trampling.
     * In that case, the hydration listener should not handle it.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFarmlandTrampleBlockRegistration(PlayerInteractEvent event) {
        if (callingPlayerInteractEvent) return; // Do not handle events that are delegated by FarmingUpgrade.
        // Check that this is a Farmland trample event.
        Block farmland = event.getClickedBlock();
        boolean trample = farmland != null && farmland.getType() == Material.FARMLAND && event.getAction() == Action.PHYSICAL;
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
     * @param event The event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFarmlandDry(BlockFadeEvent event) {
        if (callingBlockFadeEvent) return; // Do not handle events that are delegated by FarmingUpgrade.
        // Check that this is a Farmland dry or trample event.
        Block farmland = event.getBlock();
        if (farmland.getType() != Material.FARMLAND) return;
        // Check if this block was trampled or if it is drying up.
        // If this block is drying, apply upgraded hydration mechanics if they are enabled.
        // If the block is trampled, let Minecraft handle it.
        if (!farmland.equals(this.trampledFarmland)) {
            // If upgraded hydration is enabled, cancel the event and handle it manually.
            // Otherwise, let Minecraft handle the event as normal.
            if (configuration.hydrationUpgrade()) {
                event.setCancelled(true);
                int range = configuration().hydrationHorizontalRadius();
                int depth = configuration().hydrationLowerAltitude();
                int height = configuration().hydrationUpperAltitude();
                boolean dry = configuration().hydrationDry();
                farmlandUpgradedChangeMoisture(farmland, range, depth, height, dry);
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
     * @param event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFarmlandMoistureChange(MoistureChangeEvent event) {
        if (!configuration.hydrationUpgrade()) return;
        if (callingMoistureChangeEvent) return; // Do not handle events that are called by FarmingUpgrade.
        Block farmland = event.getBlock();
        if (farmland.getType() != Material.FARMLAND) return;
        event.setCancelled(true);
        var range = configuration().hydrationHorizontalRadius();
        var depth = configuration().hydrationLowerAltitude();
        var height = configuration().hydrationUpperAltitude();
        var dry = configuration().hydrationDry();
        farmlandUpgradedChangeMoisture(farmland, range, depth, height, dry);
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
     * @param event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCropGrow(BlockGrowEvent event) {
        if (!configuration.hydrationUpgrade()) return;
        if (event.isCancelled()) return;
        var farmland = event.getBlock().getRelative(0, -1, 0);
        if (farmland.getType() == Material.FARMLAND) {
            var range = configuration().hydrationHorizontalRadius();
            var depth = configuration().hydrationLowerAltitude();
            var height = configuration().hydrationUpperAltitude();
            var dry = configuration().hydrationDry();
            farmlandUpgradedChangeMoisture(farmland, range, depth, height, dry);
        }
    }

    public void farmlandUpgradedChangeMoisture(Block farmland, int range, int depth, int height, boolean dry) {
        if (farmland.getType() == Material.FARMLAND) {
            BlockState state = farmland.getState();
            farmlandDetermineUpgradedMoisture(state, range, depth, height, dry);
            PluginManager manager = Bukkit.getPluginManager();
            if (state.getType() == Material.FARMLAND) {
                MoistureChangeEvent event = new MoistureChangeEvent(farmland, state);
                callingMoistureChangeEvent = true;
                manager.callEvent(event);
                callingMoistureChangeEvent = false;
                if (!event.isCancelled()) {
                    state.update(true);
                }
            } else {
                BlockFadeEvent event = new BlockFadeEvent(farmland, state);
                callingBlockFadeEvent = true;
                manager.callEvent(event);
                callingBlockFadeEvent = false;
                if (!event.isCancelled()) {
                    state.update(true);
                }
            }
        }
    }

    public void farmlandDetermineUpgradedMoisture(BlockState state, int range, int depth, int height, boolean dry) {
        Material aboveType = state.getBlock().getRelative(0, 1, 0).getType();
        if (!aboveType.isOccluding()) {
            if (isHydrated(Lists.newArrayList(Material.WATER), state.getBlock(), range, height, depth)) {
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

    /**
     * Determine if a block has water in its vicinity.
     *
     * @param block The centre block.
     * @param radius The horizontal radius (of a square circle) to search within.
     * @param highest The greatest height to search in, relative to the block.
     * @param lowest The lowest height to search in, relative to the block.
     * @return If there is water in the searched region.
     */
    public boolean isHydrated(Collection<Material> materials, Block block, int radius, int highest, int lowest) {
        var i = -radius;
        while (i <= radius) {
            var k = -radius;
            while (k <= radius) {
                var j = lowest;
                while (j <= highest) {
                    // Return true if it is raining.
                    var searchBlock = block.getRelative(i, j, k);
                    // The Farmland is hydrated if there is water or a waterlogged block in the search region.
                    if (materials.contains(searchBlock.getType())) {
                        return true;
                    } else {
                        var data = searchBlock.getBlockData();
                        if (data instanceof Waterlogged waterlogged) {
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

    /*
     * Upgraded bonemeal
     */

    /**
     * A fertilise event is either called by the server when a player tries to fertilise a crop, or by the plugin.
     *
     * @param e
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFertilise(BlockFertilizeEvent e) {
        if (!configuration().bonemealUpgrade()) return; // Only handle if upgraded fertilisation is enabled.
        if (callingFertiliseEvent) return; // Do not handle delegated BlockFertilizeEvents.
        var block = e.getBlock();
        // Upgraded fertilisation mechanics are only enabled for specific crops.
        if (!configuration().isFertilisable(block.getType())) return;
        // Cancel to let plugin handle event.
        e.setCancelled(true);
        // Find adjacent fertilisable crops, send a BlockFertilizeEvent for them and apply
        // fertiliser if the event is successful.
        var fertilisedBlocks = new ArrayList<>(FarmingUpgradePlugin.findAdjacentMaterialsRadius(configuration().fertilisableMaterials(), block, 1));
        Collections.shuffle(fertilisedBlocks, random);
        fertilisedBlocks.add(block);
        var targetGrowthStages = 7;
        var remainingGrowthStages = targetGrowthStages;
        for (var fertilisedBlock : fertilisedBlocks) {
            var fertilisedState = fertilisedBlock.getState();
            // Apply fertiliser to the crop state. Decrease the remaining growth stages by the returned amount.
            var growth = configuration().fertilisableGrowth(fertilisedState.getType());
            Function<BlockState, Integer> fertiliserFunction = x -> trialGrow(random, 8, growth, x);
            remainingGrowthStages -= fertiliserFunction.apply(fertilisedState);
            // Call an event for the fertilised block.
            var upgradedEvent = new BlockFertilizeEvent(fertilisedBlock, e.getPlayer(), Lists.newArrayList(fertilisedState));
            callingFertiliseEvent = true;
            getServer().getPluginManager().callEvent(upgradedEvent);
            callingFertiliseEvent = false;
            // If the event is allowed, apply fertiliser.
            if (!upgradedEvent.isCancelled()) {
                fertilisedState.update();
                FarmingUpgradePlugin.fertiliseEffect(fertilisedBlock);
            }
            if (remainingGrowthStages <= 0) {
                break;
            }
        }
        // Do not remove bonemeal if no crop was grown.
        if (remainingGrowthStages < targetGrowthStages) {
            // Remove a bonemeal if not in creative.
            if (e.getPlayer() != null && e.getPlayer().getGameMode() != GameMode.CREATIVE) {
                PlayerInventory inventory = e.getPlayer().getInventory();
                int first = inventory.first(Material.BONE_MEAL);
                var mainhandItem = inventory.getItemInMainHand();
                var offhandItem = inventory.getItemInOffHand();
                if (mainhandItem.getType() == Material.BONE_MEAL) {
                    mainhandItem.setAmount(mainhandItem.getAmount() - 1);
                } else if (offhandItem.getType() == Material.BONE_MEAL) {
                    offhandItem.setAmount(offhandItem.getAmount() - 1);
                }
            }
        }
    }

    /*
     * Upgraded trample
     */

    /**
     * A PlayerInteractEvent is called for farmland when it is trampled by a player, which happens when a player jumps
     * on the farmland. It is also sometimes called from the plugin itself.
     *
     * When called by the server, the server will call a BlockFadeEvent afterwards.
     *
     * If upgraded trampling is not enabled, do not handle this event.
     *
     * @param event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFarmlandTrample(PlayerInteractEvent event) {
        if (!configuration().trampleUpgrade()) return;
        if (callingPlayerInteractEvent) return; // Do not handle events that are called by FarmingUpgrade.
        // If trample upgrade is enabled, handle trampling. Otherwise, let Minecraft
        // handle the trampling but set the trample block so it can be identified.
        // Check that this is a Farmland trample event.
        var farmland = event.getClickedBlock();
        var trample = farmland != null && farmland.getType() == Material.FARMLAND && event.getAction() == Action.PHYSICAL;
        if (!trample) return;
        // Cancel to handle manually.
        event.setUseInteractedBlock(Event.Result.DENY);
        // Trample the crop above the farmland.
        var player = event.getPlayer();
        attemptCropTrample(farmland, player);
    }

    /**
     * An event that is called when a player moves. If trampling upgrade is enabled, and trampling walk is enabled,
     * crops will be trampled when walked over.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWalk(PlayerMoveEvent event) {
        if (event.isCancelled()) return;
        if (!(configuration.trampleUpgrade() && configuration.trampleWalk())) return;
        var player = event.getPlayer();
        if (player.isSneaking()) return;
        if (event.getTo() == null) return;
        var targetBlock = event.getTo().getBlock();
        var sourceBlock = event.getFrom().getBlock();
        if (targetBlock.equals(sourceBlock)) return;
        var material = targetBlock.getType();
        if (material != Material.FARMLAND) return;
        attemptCropTrample(targetBlock, player);
    }

    /**
     * Attempt to trample the crop on a farmland.
     */
    void attemptCropTrample(Block farmlandBlock, Player player) {
        var crop = farmlandBlock.getRelative(0, 1, 0);
        if (!configuration().isCrop(crop.getType())) return; // TODO Switch with tramplable crops
        // Send an InteractEvent for the trampled crop.
        var trampleEvent = new PlayerInteractEvent(player, Action.PHYSICAL, null, crop, BlockFace.SELF);
        callingPlayerInteractEvent = true;
        this.getServer().getPluginManager().callEvent(trampleEvent);
        callingPlayerInteractEvent = false;
        if (trampleEvent.useInteractedBlock() == Event.Result.ALLOW) {
            // Calculate the state of the crop after being trampled.
            var oldState = crop.getState();
            var state = crop.getState();
            state.setType(Material.AIR);
            state.setType(crop.getType());
            // Send a BlockFadeEvent to indicate the crop being reset.
            var fadeEvent = new BlockFadeEvent(crop, state);
            callingBlockFadeEvent = true;
            this.getServer().getPluginManager().callEvent(fadeEvent);
            callingBlockFadeEvent = false;
            if (!fadeEvent.isCancelled()) {
                state.update(true);
                breakBlockEffect(crop, oldState, Sound.BLOCK_CROP_BREAK);
            }
        }
    }

    /*
     * Utility
     */

    /**
     * Use Bernoulli trials to determine how many growth stages to add to a crop.
     */
    public static int trialGrow(Random random, int trials, double probability, BlockState state) {
        var stageDifference = 0;
        var data = state.getBlockData();
        if (data instanceof Ageable) {
            // Run Bernoulli trials to determine growth stage increase.
            var stages = 0;
            var i = 0;
            while (i < trials) {
                if (random.nextDouble() < probability) {
                    stages++;
                }
                i++;
            }
            // Add the growth stages to the state.
            var ageable = (Ageable) data;
            var currentStage = ageable.getAge();
            var newStage = Math.min(ageable.getAge() + stages, ageable.getMaximumAge());
            stageDifference = newStage - currentStage;
            ageable.setAge(newStage);
        }
        state.setBlockData(data);
        return stageDifference;
    }

    public static void breakBlockEffect(Block block, BlockState state, Sound sound) {
        breakBlockEffect(block, state, sound, 0);
    }

    public static void breakBlockEffect(Block block, BlockState state, Sound sound, int radius) {
        var particles = (10 * (2 * radius + 1)) / (4 * radius * radius + 4 * radius + 1);
        var location = block.getLocation().add(0.5, 0.5, 0.5);
        location.getWorld().spawnParticle(Particle.BLOCK_CRACK, location, particles, 0.5, 0.5, 0.5, state.getBlockData());
        location.getWorld().playSound(location, sound, 1, 1);
    }

    public static void fertiliseEffect(Block block) {
        var location = block.getLocation().add(0.5, 0.5, 0.375);
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
    static boolean damageTool(Random random, Player player, ItemStack tool, int damage, boolean applyUnbreaking) {
        if (!(player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)) return false;
        if (tool.getType().isAir()) return false;
        var itemMeta = tool.getItemMeta();
        if (itemMeta.isUnbreakable()) return false;
        if (tool.getType().getMaxDurability() == 0) return false; // Check if the item is a regular item, as opposed to a tool/weapon.
        if (applyUnbreaking) {
            // Calculate the chance of the tool taking damage with the standard Minecraft formula, which depends on the
            // level of the Unbreaking enchantment.
            var damageChance = 1.0 / (tool.getEnchantmentLevel(Enchantment.DURABILITY) + 1.0);
            if (random.nextFloat() < damageChance) {
                return damageTool(player, tool, damage);
            }
            return false;
        } else {
            return damageTool(player, tool, damage);
        }
    }

    static boolean damageTool(Player player, ItemStack tool, int damage) {
        boolean destroyed;
        var meta = tool.getItemMeta();
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(damageable.getDamage() + damage);
            tool.setItemMeta(meta);
            destroyed = damageable.getDamage() >= tool.getType().getMaxDurability();
        } else {
            destroyed = false;
        }
        if (destroyed) {
            player.spawnParticle(Particle.ITEM_CRACK, player.getLocation(), 1, tool);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1, 1);
            // Setting the amount to zero will delete the item.
            tool.setAmount(tool.getAmount() - 1);
        }
        return destroyed;
    }

    /**
     * Check if a block is fully grown.
     *
     * @param block The block.
     * @return If the block is fully grown or not. Empty if the block is not Ageable.
     */
    public static Optional<Boolean> isMature(Block block) {
        var data = block.getBlockData();
        if (data instanceof Ageable) {
            var ageable = (Ageable) data;
            return Optional.of(ageable.getAge() == ageable.getMaximumAge());
        } else {
            return Optional.empty();
        }
    }

    public static Set<Block> findAdjacentMaterials(Collection<Material> materials, Block centre, int radius) {
        var adjacent = new HashSet<Block>();
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
        var ring = new HashSet<Block>();
        // Centre coordinates.
        var world = centre.getWorld();
        var x = centre.getX();
        var y = centre.getY();
        var z = centre.getZ();
        // Relative to centre coordinates.
        var i = -radius;
        var k = -radius;
        // Iterate over the columns in the given radius.
        while (i < radius) {
            var block = findHighestMaterial(materials, world, x + i, z + k, y + radius, y - radius);
            block.ifPresent(ring::add);
            i++;
        }
        while (k < radius) {
            var block = findHighestMaterial(materials, world, x + i, z + k, y + radius, y - radius);
            block.ifPresent(ring::add);
            k++;
        }
        while (i > -radius) {
            var block = findHighestMaterial(materials, world, x + i, z + k, y + radius, y - radius);
            block.ifPresent(ring::add);
            i--;
        }
        while (k > -radius) {
            var block = findHighestMaterial(materials, world, x + i, z + k, y + radius, y - radius);
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
        var y = highest;
        while (y >= lowest) {
            var block = world.getBlockAt(x, y, z);
            if (materials.contains(block.getType())) {
                return Optional.of(block);
            }
            y--;
        }
        return Optional.empty();
    }

    record HarvestToolType(@Nullable Material material, @Nullable String lore, double radius, int damage) { }

    record ReplantableCrop(Material crop, @Nullable Material seeds) { }

    record FertilisablePlant(Material plant, double growth) { }

}
