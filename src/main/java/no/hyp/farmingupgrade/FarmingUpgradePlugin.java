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
import org.bukkit.configuration.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.permissions.*;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public final class FarmingUpgradePlugin extends JavaPlugin implements Listener {

    boolean required;

    @Nullable FarmingUpgradePlugin.ToolUpgrade toolUpgrade;

    @Nullable HydrationUpgrade hydrationUpgrade;

    @Nullable BonemealUpgrade bonemealUpgrade;

    @Nullable TrampleUpgrade trampleUpgrade;

    final Random random = new Random();

    /*
     * The plugin replaces Vanilla features by catching events, cancelling them, and then executing its own logic. The
     * plugin calls its own events in this logic, whose types matches those caught by the plugin. To prevent the plugin
     * from catching its own events, these booleans are set when the events are being called.
     */

    boolean callingBlockBreakEvent;

    boolean callingBlockPlaceEvent;

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
        readConfig();
        // Register the event listeners.
        this.getServer().getPluginManager().registerEvents(this, this);
        // Set listeners.
        this.callingBlockBreakEvent = false;
        this.callingBlockPlaceEvent = false;
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
        if (version == 6) {
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
                readConfig();
                sender.sendMessage("FarmingUpgrade configuration reloaded.");
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

    public void readConfig() {
        var config = this.getConfig();
        this.required = config.getBoolean("required");
        this.toolUpgrade = readFarmingToolsUpgrade(config);
        this.hydrationUpgrade = readHydrationUpgrade(config);
        this.bonemealUpgrade = readBonemealUpgrade(config);
        this.trampleUpgrade = readTrampleUpgrade(config);
    }

    @Nullable FarmingUpgradePlugin.ToolUpgrade readFarmingToolsUpgrade(Configuration configuration) {
        if (configuration.get("toolUpgrade", null) == null) return null;
        var radiusPerEfficiencyLevel = configuration.getDouble("toolUpgrade.radiusPerEfficiencyLevel");
        var applyUnbreaking = configuration.getBoolean("toolUpgrade.applyUnbreaking");
        var onlyHarvestMature = configuration.getBoolean("toolUpgrade.onlyHarvestMature");
        var minimumReplantDelay = configuration.getInt("toolUpgrade.replantDelayMinimum");
        var maximumReplantDelay = configuration.getInt("toolUpgrade.replantDelayMaximum");
        var harvestParticleMultiplier = configuration.getDouble("toolUpgrade.harvestParticleMultiplier");
        var replantParticleMultiplier = configuration.getDouble("toolUpgrade.replantParticleMultiplier");
        var plantParticleMultiplier = configuration.getDouble("toolUpgrade.plantParticleMultiplier");
        var toolSwingParticleEffect = configuration.getBoolean("toolUpgrade.toolSwingParticleEffect");
        var replantDefault = configuration.getBoolean("toolUpgrade.replantDefault");
        var collectDefault = configuration.getBoolean("toolUpgrade.collectDefault");
        var plantDefault = configuration.getBoolean("toolUpgrade.plantDefault");
        var tools = readTools(configuration, replantDefault, collectDefault, plantDefault);
        var crops = readCrops(configuration);
        return new ToolUpgrade(
                tools, crops, radiusPerEfficiencyLevel, applyUnbreaking, onlyHarvestMature, minimumReplantDelay,
                maximumReplantDelay, replantParticleMultiplier, harvestParticleMultiplier, plantParticleMultiplier,
                toolSwingParticleEffect
        );
    }

    List<HarvestToolType> readTools(Configuration configuration, boolean replantDefault, boolean collectDefault, boolean plantDefault) {
        var tools = new LinkedList<HarvestToolType>();
        var toolSectionMaps = (List<Map<?, ?>>) configuration.getList("toolUpgrade.tools");
        assert toolSectionMaps != null;
        for (var toolSectionMap : toolSectionMaps) {
            var toolSection = new MemoryConfiguration().createSection("section", toolSectionMap);
            @Nullable var materialString = toolSection.getString("material", null);
            @Nullable var material = materialString != null ? Material.matchMaterial(materialString) : null;
            @Nullable var lore = toolSection.getString("lore", null);
            @Nullable var permission = toolSection.getString("permission", null);
            var radius = toolSection.getDouble("radius", 0);
            var damage = toolSection.getInt("damage", 1);
            var replant = toolSection.getBoolean("replant", replantDefault);
            var collect = toolSection.getBoolean("collect", collectDefault);
            var plant = toolSection.getBoolean("plant", plantDefault);
            tools.add(new HarvestToolType(material, lore, permission, radius, damage, replant, collect, plant));
        }
        return tools;
    }

    record HarvestToolType(@Nullable Material material, @Nullable String lore, @Nullable String permission, double radius, int damage, boolean replant, boolean collect, boolean plant) { }

    static ImmutableList<ReplantableCrop> readCrops(ConfigurationSection configuration) {
        var crops = new LinkedList<ReplantableCrop>();
        var cropSectionMaps = (List<Map<?, ?>>) configuration.getList("toolUpgrade.crops");
        assert cropSectionMaps != null;
        for (var cropSectionMap : cropSectionMaps) {
            var cropSection = new MemoryConfiguration().createSection("section", cropSectionMap);
            var cropName = cropSection.getString("crop");
            assert cropName != null;
            var cropMaterial = Material.matchMaterial(cropName);
            @Nullable var seedsName = cropSection.getString("seeds");
            @Nullable var seedsMaterial = seedsName != null ? Material.matchMaterial(seedsName) : null;
            crops.add(new ReplantableCrop(cropMaterial, seedsMaterial));
        }
        return ImmutableList.copyOf(crops);
    }

    record ReplantableCrop(Material crop, @Nullable Material seeds) { }

    @Nullable HydrationUpgrade readHydrationUpgrade(Configuration configuration) {
        if (configuration.get("hydrationUpgrade", null) == null) return null;
        var horizontalRadius = configuration.getInt("hydrationUpgrade.horizontalSearchRadius");
        var upwardsSearchDistance = configuration.getInt("hydrationUpgrade.upwardSearchDistance");
        var downwardSearchDistance = configuration.getInt("hydrationUpgrade.downwardSearchDistance");
        var dry = configuration.getBoolean("hydrationUpgrade.dry");
        return new HydrationUpgrade(horizontalRadius, upwardsSearchDistance, downwardSearchDistance, dry);
    }

    @Nullable BonemealUpgrade readBonemealUpgrade(Configuration configuration) {
        if (configuration.get("bonemealUpgrade", null) == null) return null;
        var plants = readPlants(configuration);
        var radius = configuration.getInt("bonemealUpgrade.radius");
        var trials = configuration.getInt("bonemealUpgrade.trials");
        var targetGrowthStages = configuration.getInt("bonemealUpgrade.targetGrowthStages");
        var minimumDelay = configuration.getInt("bonemealUpgrade.minimumDelay");
        var maximumDelay = configuration.getInt("bonemealUpgrade.maximumDelay");
        var fertiliseParticleMultiplier = configuration.getInt("bonemealUpgrade.fertiliseParticleMultiplier");
        return new BonemealUpgrade(plants, radius, trials, targetGrowthStages, minimumDelay, maximumDelay, fertiliseParticleMultiplier);
    }

    static ImmutableList<FertilisablePlant> readPlants(ConfigurationSection configuration) {
        var plants = new LinkedList<FertilisablePlant>();
        var plantSectionMaps = (List<Map<?, ?>>) configuration.getList("bonemealUpgrade.plants");
        assert plantSectionMaps != null;
        for (var plantSectionMap : plantSectionMaps) {
            var plantSection = new MemoryConfiguration().createSection("section", plantSectionMap);
            var plantName = plantSection.getString("plant");
            assert plantName != null;
            var plantMaterial = Material.matchMaterial(plantName);
            var growth = plantSection.getDouble("growth");
            plants.add(new FertilisablePlant(plantMaterial, growth));
        }
        return ImmutableList.copyOf(plants);
    }

    @Nullable TrampleUpgrade readTrampleUpgrade(Configuration configuration) {
        if (configuration.get("trampleUpgrade", null) == null) return null;
        var trampleableCrops = configuration.getStringList("trampleUpgrade.trampleableCrops").stream().map(Material::matchMaterial).toList();
        var trampleByWalking = configuration.getBoolean("trampleUpgrade.trampleByWalking");
        var dryEmptyOnTrample = configuration.getBoolean("trampleUpgrade.dryEmptyOnTrample");
        var trampleParticleMultiplier = configuration.getDouble("trampleUpgrade.trampleParticleMultiplier");
        return new TrampleUpgrade(trampleableCrops, trampleByWalking, dryEmptyOnTrample, trampleParticleMultiplier);
    }

    record FertilisablePlant(Material plant, double growth) { }

    record ToolUpgrade(
            List<HarvestToolType> tools,
            List<ReplantableCrop> crops,
            double radiusPerEfficiencyLevel,
            boolean applyUnbreaking,
            boolean harvestOnlyMature,
            int minimumReplantDelay,
            int maximumReplantDelay,
            double replantParticleMultiplier,
            double harvestParticleMultiplier,
            double plantParticleMultiplier,
            boolean toolSwingParticleEffect
    ) {

        public Optional<HarvestToolType> toolType(Player player, ItemStack toolItem) {
            var potentialTools = toolType(toolItem);
            for (var tool : potentialTools) {
                @Nullable var permission = tool.permission;
                if (permission == null) return Optional.of(tool);
                if (player.hasPermission(permission)) return Optional.of(tool);
            }
            return Optional.empty();
        }

        public LinkedList<HarvestToolType> toolType(ItemStack toolItem) {
            var list = new LinkedList<HarvestToolType>();
            for (var toolType : tools) {
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
                list.add(toolType);
            }
            return list;
        }

        public boolean isCrop(Material type) {
            for (var crop : crops) {
                if (crop.crop() == type) return true;
            }
            return false;
        }

        public Collection<Material> cropMaterials() {
            return crops.stream().map(ReplantableCrop::crop).collect(Collectors.toUnmodifiableSet());
        }

        public Optional<Material> seeds(Material material) throws IllegalArgumentException {
            for (var crop : crops) {
                if (crop.crop() == material) {
                    return Optional.ofNullable(crop.seeds());
                }
            }
            throw new IllegalArgumentException(String.format("Material %s is not harvestable.", material.name()));
        }

    }

    record HydrationUpgrade(
            int horizontalSearchRadius,
            int upwardSearchDistance,
            int downwardSearchDistance,
            boolean dry
    ) { }

    record BonemealUpgrade(
            List<FertilisablePlant> plants,
            int radius,
            int trials,
            int targetGrowthStages,
            int minimumDelay,
            int maximumDelay,
            double fertiliseParticleMultiplier
    ) {

        public boolean isFertilisable(Material material) {
            return plants.stream().anyMatch(x -> x.plant() == material);
        }

        public Collection<Material> fertilisableMaterials() {
            return plants.stream().map(FertilisablePlant::plant).collect(Collectors.toSet());
        }

        public double fertilisableGrowth(Material material) throws IllegalArgumentException {
            for (var plant : plants) {
                if (plant.plant() == material) return plant.growth();
            }
            throw new IllegalArgumentException(String.format("Material %s is not fertilisable.", material.name()));
        }

    }

    record TrampleUpgrade(
            List<Material> trampleablePlants,
            boolean trampleByWalking,
            boolean dryEmptyOnTrample,
            double trampleParticleMultiplier
    ) {

        boolean isCropTrampleable(Material cropMaterial) {
            return trampleablePlants.contains(cropMaterial);
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
        if (toolUpgrade == null) return;
        if (callingBlockBreakEvent) return; // Do not handle events that are called by FarmingUpgrade.
        var player = event.getPlayer();
        var centre = event.getBlock();
        if (!toolUpgrade.isCrop(centre.getType())) return; // Farming only applies to crops.
        var toolItem = player.getInventory().getItemInMainHand();
        @Nullable HarvestToolType toolType = null;
        for (var maybeToolType : toolUpgrade.toolType(toolItem)) {
            @Nullable var permissionString = maybeToolType.permission();
            if (permissionString == null) {
                toolType = maybeToolType;
                break;
            }
            @Nullable Permission permission = getServer().getPluginManager().getPermission(permissionString);
            if (permission == null) continue; // The player does not have the tool permission because it does not exist.
            if (player.hasPermission(permission)) {
                toolType = maybeToolType;
                break;
            }
        }
        if (toolType == null) return; // If the crop was not broken by a harvest tool, proceed with Vanilla mechanics.
        event.setCancelled(true); // Cancel the Vanilla event to cancel the Vanilla mechanics.
        initiateHarvest(player, toolType, toolItem, centre);
    }

    /**
     * Called when a player uses a harvest tool on a crop.
     */
    void initiateHarvest(Player player, HarvestToolType toolType, ItemStack toolItem, Block centre) {
        assert toolUpgrade != null;
        if (toolUpgrade.toolSwingParticleEffect()) harvestSwingParticles(player);
        var toolDamage = toolType.damage();
        var radius = calculateRadius(toolType, toolItem);
        var cropMaterials = toolUpgrade.cropMaterials();
        var adjacentCropBlocks = findAdjacentMaterials(cropMaterials, centre, radius, true);
        var replant = toolType.replant;
        var applyUnbreaking = toolUpgrade.applyUnbreaking;
        var collect = toolType.collect;
        var onlyMature = toolUpgrade.harvestOnlyMature;
        for (var adjacentCropBlock : adjacentCropBlocks) {
            @Nullable var seeds = toolUpgrade.seeds(adjacentCropBlock.getType()).orElse(null);
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
        assert toolUpgrade != null;
        var efficiencyRangePerLevel = toolUpgrade.radiusPerEfficiencyLevel;
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
        assert toolUpgrade != null;
        var particleScale = toolUpgrade.harvestParticleMultiplier;
        FarmingUpgradePlugin.breakBlockEffect(block, state, Sound.BLOCK_CROP_BREAK, particleScale);
        block.setType(Material.AIR);
        // A seed that can replant the crop.
        @Nullable ItemStack foundSeed = null;
        GameMode mode = player.getGameMode();
        // If event disallows drops, do not drop anything, nor a replanting seed.
        if (!upgradedEvent.isDropItems()) itemDrops.clear();
        // Search for a seed to replant the crop with if replanting is enabled.
        if (replant) {
            for (ItemStack itemDrop : itemDrops) {
                // If replanting is enabled, and a seed is not found yet, search for one in this ItemStack.
                if (foundSeed == null) {
                    int amount = itemDrop.getAmount();
                    if (itemDrop.getType() == seed && amount >= 1) {
                        foundSeed = itemDrop.clone();
                        foundSeed.setAmount(1);
                        itemDrop.setAmount(amount - 1);
                    }
                }
            }
        }
        // Replant the crop if a seed was found.
        if (foundSeed != null) {
            var delayRange = toolUpgrade.maximumReplantDelay - toolUpgrade.minimumReplantDelay;
            int delay;
            if (delayRange == 0) {
                delay = toolUpgrade.minimumReplantDelay;
            } else {
                delay = toolUpgrade.minimumReplantDelay + random.nextInt(delayRange);
            }
            var replantParticlesMultiplier = toolUpgrade.replantParticleMultiplier;
            if (delay == 0) {
                // Send a BlockPlaceEvent for the replanted crop.
                var replacedState = block.getState();
                block.setType(state.getType());
                var plantEvent = new BlockPlaceEvent(block, replacedState, block.getRelative(0, -1, 0), foundSeed.clone(), player, true, EquipmentSlot.HAND);
                callingBlockPlaceEvent = true;
                this.getServer().getPluginManager().callEvent(plantEvent);
                callingBlockPlaceEvent = false;
                if (plantEvent.isCancelled() || !plantEvent.canBuild()) {
                    // If cancelled, revert block state to air and add seed to drops.
                    replacedState.update();
                    itemDrops.add(foundSeed);
                } else {
                    if (replantParticlesMultiplier > 0.0) fertiliseEffect(block, replantParticlesMultiplier);
                }
            } else {
                final @Nullable var finalFoundSeed = foundSeed;
                boolean drop = upgradedEvent.isDropItems() || mode != GameMode.CREATIVE;
                getServer().getScheduler().runTaskLater(this, () -> {
                    // Player must be online for a BlockPlaceEvent to happen, otherwise weird stuff could happen.
                    if (player.isOnline()) {
                        if (block.isEmpty()) {
                            var replacedState = block.getState();
                            block.setType(state.getType());
                            var plantEvent = new BlockPlaceEvent(block, replacedState, block.getRelative(0, -1, 0), finalFoundSeed.clone(), player, true, EquipmentSlot.HAND);
                            callingBlockPlaceEvent = true;
                            this.getServer().getPluginManager().callEvent(plantEvent);
                            callingBlockPlaceEvent = false;
                            if (plantEvent.isCancelled() || !plantEvent.canBuild()) {
                                replacedState.update();
                                if (drop) dropItem(block, finalFoundSeed);
                            } else {
                                if (replantParticlesMultiplier > 0.0) fertiliseEffect(block, replantParticlesMultiplier);
                            }
                        } else {
                            if (drop) dropItem(block, finalFoundSeed);
                        }
                    } else {
                        if (drop) dropItem(block, finalFoundSeed);
                    }
                }, delay);
            }
        }
        // Clear all drops if in creative.
        if (mode == GameMode.CREATIVE) itemDrops.clear();
        // If collect is enabled, items are sent to the inventory if there is space.
        if (collect) {
            Inventory inventory = player.getInventory();
            Set<ItemStack> notAddedItems = new HashSet<>();
            for (ItemStack item : itemDrops) {
                notAddedItems.addAll(inventory.addItem(item).values());
            }
            itemDrops = notAddedItems;
            // player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.2f, 0.75f + random.nextFloat() * 0.5f);
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
        return true;
    }

    /**
     * When
     * - a player plants a seed and
     * - has a harvesting tool in either the main hand or the offhand and
     * - the tool has planting enabled
     * then the seeds are planted in a radius.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    void onPlant(BlockPlaceEvent event) {
        if (callingBlockPlaceEvent) return;
        if (toolUpgrade == null) return;
        var crop = event.getBlock(); // The block/blockState is updated before the event is called. If the event is cancelled, the blockState is reverted to the original. That is why the planting task is delayed by 1 tick.
        var cropType = crop.getType();
        if (!toolUpgrade.isCrop(cropType)) return;
        var player = event.getPlayer();
        // Try to find a tool in the mainhand or offhand.
        @Nullable HarvestToolType tool;
        ItemStack toolItem;
        {
            var mainHand = player.getInventory().getItemInMainHand();
            var mainHandTool = toolUpgrade.toolType(player, mainHand).orElse(null);
            if (mainHandTool != null) {
                toolItem = mainHand.clone();
                tool = mainHandTool;
            } else {
                var offHand = player.getInventory().getItemInOffHand();
                tool = toolUpgrade.toolType(player, offHand).orElse(null);
                toolItem = offHand.clone();
            }
        }
        if (tool == null) return; // No tool was found.
        var plant = tool.plant;
        if (!plant) return;
        var seedItem = event.getItemInHand().clone();
        seedItem.setAmount(1);
        // Wait one tick, to ensure that the event has finished.
        this.getServer().getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            var inventory = player.getInventory();
            ItemStack foundItem = null;
            for (@Nullable var i : inventory) {
                if (i == null) continue;
                if (toolItem.isSimilar(i)) {
                    foundItem = i;
                    break;
                }
            }
            if (foundItem == null) return; // Cancel if the tool cannot be found.
            var centre = crop.getRelative(0, -1, 0);
            if (centre.getType() != Material.FARMLAND) return;
            var radius = calculateRadius(tool, toolItem);
            var farmlands = findAdjacentMaterials(List.of(Material.FARMLAND), centre, radius, false);
            var particleMultiplier = toolUpgrade.plantParticleMultiplier;
            var creative = player.getGameMode() == GameMode.CREATIVE;
            for (var farmland : farmlands) {
                var aboveFarmland = farmland.getRelative(0, 1, 0);
                if (aboveFarmland.getType() != Material.AIR) continue;
                var placeEvent = new BlockPlaceEvent(aboveFarmland, aboveFarmland.getState(), farmland, event.getItemInHand(), player, event.canBuild()); //TODO canBuild
                var savedState = aboveFarmland.getState();
                aboveFarmland.setType(cropType);
                callingBlockPlaceEvent = true;
                getServer().getPluginManager().callEvent(placeEvent);
                callingBlockPlaceEvent = false;
                if (placeEvent.isCancelled() || !placeEvent.canBuild()) {
                    savedState.update(); // Revert if event was cancelled.
                    continue;
                }
                if (!creative) inventory.removeItem(seedItem.clone()); // Remove the planted seed. Clone in case server implementation reduces amount.
                FarmingUpgradePlugin.fertiliseEffect(aboveFarmland, particleMultiplier);
                if (!creative && !inventory.containsAtLeast(seedItem, 1)) break; // Break if there are no more seeds left.
            }
            FarmingUpgradePlugin.fertiliseEffect(crop, particleMultiplier); // Particles for centre crop.
        }, 1);
    }

    /*
     * Upgraded hydration
     */

    /**
     * TODO: Look for better way in the future. This solution depends on the order of events.
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
            if (hydrationUpgrade != null) {
                event.setCancelled(true);
                int range = hydrationUpgrade.horizontalSearchRadius;
                int depth = hydrationUpgrade.downwardSearchDistance;
                int height = hydrationUpgrade.upwardSearchDistance;
                boolean dry = hydrationUpgrade.dry;
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
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFarmlandMoistureChange(MoistureChangeEvent event) {
        if (hydrationUpgrade == null) return;
        if (callingMoistureChangeEvent) return; // Do not handle events that are called by FarmingUpgrade.
        Block farmland = event.getBlock();
        if (farmland.getType() != Material.FARMLAND) return;
        event.setCancelled(true);
        var range = hydrationUpgrade.horizontalSearchRadius;
        var depth = hydrationUpgrade.downwardSearchDistance;
        var height = hydrationUpgrade.upwardSearchDistance;
        var dry = hydrationUpgrade.dry;
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
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCropGrow(BlockGrowEvent event) {
        if (hydrationUpgrade == null) return;
        if (event.isCancelled()) return;
        var farmland = event.getBlock().getRelative(0, -1, 0);
        if (farmland.getType() == Material.FARMLAND) {
            var range = hydrationUpgrade.horizontalSearchRadius;
            var depth = hydrationUpgrade.downwardSearchDistance;
            var height = hydrationUpgrade.upwardSearchDistance;
            var dry = hydrationUpgrade.dry;
            farmlandUpgradedChangeMoisture(farmland, range, depth, height, dry);
        }
    }

    void farmlandUpgradedChangeMoisture(Block farmland, int range, int depth, int height, boolean dry) {
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

    void farmlandDetermineUpgradedMoisture(BlockState state, int range, int depth, int height, boolean dry) {
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
    boolean isHydrated(Collection<Material> materials, Block block, int radius, int highest, int lowest) {
        var i = -radius;
        while (i <= radius) {
            var k = -radius;
            while (k <= radius) {
                var j = -lowest;
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
     */
    @EventHandler(priority = EventPriority.LOWEST) //TODO: Do not fully grow the fertilised crop until all other crops in radius is fully grown
    void onFertilise(BlockFertilizeEvent e) {
        if (bonemealUpgrade == null) return; // Only handle if upgraded fertilisation is enabled.
        if (callingFertiliseEvent) return; // Do not handle delegated BlockFertilizeEvents.
        var block = e.getBlock();
        if (!bonemealUpgrade.isFertilisable(block.getType())) return; // Upgraded fertilisation mechanics are only enabled for specific crops.
        e.setCancelled(true); // Cancel to let plugin handle event.
        var radius = bonemealUpgrade.radius;
        // Find adjacent fertilisable crops, send a BlockFertilizeEvent for them and apply fertiliser if the event is successful.
        var fertilisedBlocks = new ArrayList<>(findAdjacentMaterials(bonemealUpgrade.fertilisableMaterials(), block, radius, true));
        Collections.shuffle(fertilisedBlocks, random);
        var trials = bonemealUpgrade.trials;
        var particleMultiplier = bonemealUpgrade.fertiliseParticleMultiplier;
        var creative = e.getPlayer().getGameMode() == GameMode.CREATIVE;
        // Remove a bonemeal if not in creative.
        if (e.getPlayer() != null && !creative) {
            PlayerInventory inventory = e.getPlayer().getInventory();
            if (!inventory.contains(Material.BONE_MEAL)) return; // Would be weird
            inventory.removeItem(new ItemStack(Material.BONE_MEAL));
        }
        // Generate an event for all the fertilised crops.
        for (var fertilisedBlock : fertilisedBlocks) {
            var upgradedEvent = new BlockFertilizeEvent(fertilisedBlock, e.getPlayer(), Lists.newArrayList(fertilisedBlock.getState()));
            callingFertiliseEvent = true;
            getServer().getPluginManager().callEvent(upgradedEvent);
            callingFertiliseEvent = false;
            // If the event is allowed, apply fertiliser.
            if (upgradedEvent.isCancelled()) fertilisedBlocks.remove(fertilisedBlock);
        }
        var targetGrowthStages = bonemealUpgrade.targetGrowthStages;
        Runnable fertiliseTask = () -> {
            var remainingGrowthStages = targetGrowthStages;
            for (var fertilisedBlock : fertilisedBlocks) {
                if (!bonemealUpgrade.isFertilisable(fertilisedBlock.getType())) continue;
                var fertilisedState = fertilisedBlock.getState();
                // Apply fertiliser to the crop state. Decrease the remaining growth stages by the returned amount.
                var growth = bonemealUpgrade.fertilisableGrowth(fertilisedState.getType());
                var currentGrowthStages = remainingGrowthStages;
                remainingGrowthStages -= trialGrow(random, trials, growth, fertilisedState);
                if (currentGrowthStages == remainingGrowthStages) continue;
                fertilisedState.update();
                FarmingUpgradePlugin.fertiliseEffect(fertilisedBlock, particleMultiplier);
                if (remainingGrowthStages <= 0) break;
            }
            if (remainingGrowthStages != targetGrowthStages) {
                block.getWorld().playSound(block.getLocation(), Sound.ITEM_BONE_MEAL_USE, 0.10f, 0.8f + random.nextFloat() * 0.4f);
            }
            // Drop a bonemeal if there was no change. Only if player is not in creative.
            if (remainingGrowthStages == targetGrowthStages && !creative) {
                dropItem(fertilisedBlocks.get(fertilisedBlocks.size() - 1), new ItemStack(Material.BONE_MEAL));
            }
        };
        var delayRange = bonemealUpgrade.maximumDelay - bonemealUpgrade.minimumDelay;
        var delay = delayRange == 0 ? bonemealUpgrade.minimumDelay : bonemealUpgrade.minimumDelay + random.nextInt(delayRange);
        if (delay == 0) {
            fertiliseTask.run();
        } else {
            getServer().getScheduler().runTaskLater(this, fertiliseTask, delay);
            for (var fertilisedBlock : fertilisedBlocks) {
                FarmingUpgradePlugin.fertiliseEffect(fertilisedBlock, 0.5 * particleMultiplier);
            }
        }
    }

    void dropItem(Block block, ItemStack itemStack) {
        var dropLocation = block.getLocation();
        var world = dropLocation.getWorld();
        if (world == null) return;
        world.dropItemNaturally(dropLocation, itemStack);
        world.playSound(dropLocation, Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f + random.nextFloat() * 0.4f);
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
     */
    @EventHandler(priority = EventPriority.LOWEST)
    void onFarmlandTrample(PlayerInteractEvent event) {
        if (trampleUpgrade == null) return;
        if (callingPlayerInteractEvent) return; // Do not handle events that are called by FarmingUpgrade.
        // If trample upgrade is enabled, handle trampling. Otherwise, let Minecraft
        // handle the trampling but set the trample block so it can be identified.
        // Check that this is a Farmland trample event.
        var farmland = event.getClickedBlock();
        var trample = farmland != null && farmland.getType() == Material.FARMLAND && event.getAction() == Action.PHYSICAL;
        if (!trample) return;
        // If there is no crop above and trample.revertEmpty is enabled, let the farmland be trampled like in Vanilla.
        var crop = farmland.getRelative(0, 1, 0);
        if (crop.isEmpty() && trampleUpgrade.dryEmptyOnTrample) {
            return;
        }
        // Cancel to handle manually.
        event.setUseInteractedBlock(Event.Result.DENY);
        // Trample the crop above the farmland.
        var player = event.getPlayer();
        attemptCropTrample(farmland, player);
    }

    /**
     * An event that is called when a player moves. If trampling upgrade is enabled, and trampleByWalking is enabled,
     * crops will be trampled when walked over.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    void onWalk(PlayerMoveEvent event) {
        if (event.isCancelled()) return;
        if (!(trampleUpgrade != null && trampleUpgrade.trampleByWalking)) return;
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
        assert trampleUpgrade != null;
        if (!trampleUpgrade.isCropTrampleable(crop.getType())) return;
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
                var particleScale = trampleUpgrade.trampleParticleMultiplier;
                breakBlockEffect(crop, oldState, Sound.BLOCK_CROP_BREAK, particleScale);
            }
        }
    }

    /*
     * Utility
     */

    /**
     * Use Bernoulli trials to determine how many growth stages to add to a crop.
     */
    static int trialGrow(Random random, int trials, double probability, BlockState state) {
        var stageDifference = 0;
        var data = state.getBlockData();
        if (data instanceof Ageable ageable) {
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
            var currentStage = ageable.getAge();
            var newStage = Math.min(ageable.getAge() + stages, ageable.getMaximumAge());
            stageDifference = newStage - currentStage;
            ageable.setAge(newStage);
        }
        state.setBlockData(data);
        return stageDifference;
    }

    static void breakBlockEffect(Block block, BlockState state, Sound sound, double particleScale) {
        breakBlockEffect(block, state, sound, 0, particleScale);
    }

    static void breakBlockEffect(Block block, BlockState state, Sound sound, int radius, double particleScale) {
        var particles = (10 * (2 * radius + 1)) / (4 * radius * radius + 4 * radius + 1);
        var location = block.getLocation().add(0.5, 0.5, 0.5);
        location.getWorld().spawnParticle(Particle.BLOCK_CRACK, location, (int) (particles * particleScale), 0.5, 0.5, 0.5, state.getBlockData());
        location.getWorld().playSound(location, sound, 1, 1);
    }

    static void fertiliseEffect(Block block, double particleScale) {
        var location = block.getLocation().add(0.5, 0.5, 0.375);
        location.getWorld().spawnParticle(Particle.COMPOSTER, location, (int) (15 * particleScale), 0.4, 0.4, 0.35);
    }

    /**
     * Apply damage to a player's item. Does nothing if the item is not Damageable.
     * Also checks that the Player is in Survival or Adventure mode before applying damage.
     *
     * @param player The player whose tool might take damage.
     * @param tool The item to take damage.
     * @param damage The amount of damage to apply.
     */
    boolean damageTool(Random random, Player player, ItemStack tool, int damage, boolean applyUnbreaking) {
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

    boolean damageTool(Player player, ItemStack tool, int damage) {
        boolean destroyed = false;
        if (tool.getItemMeta() instanceof Damageable) {
            var damageEvent = new PlayerItemDamageEvent(player, tool, damage);
            this.getServer().getPluginManager().callEvent(damageEvent);
            if (!damageEvent.isCancelled()) {
                var eventDamage = damageEvent.getDamage();
                var meta = tool.getItemMeta();
                if (meta instanceof Damageable damageable) {
                    damageable.setDamage(damageable.getDamage() + eventDamage);
                    tool.setItemMeta(meta);
                    destroyed = damageable.getDamage() >= tool.getType().getMaxDurability();
                }
            }
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
     * @return If the block is fully grown or not. Empty if the block is not
     * {@link org.bukkit.block.data.Ageable Ageable}.
     */
    static Optional<Boolean> isMature(Block block) {
        var data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            return Optional.of(ageable.getAge() == ageable.getMaximumAge());
        } else {
            return Optional.empty();
        }
    }

    Collection<Block> findAdjacentMaterials(Collection<Material> materials, Block centre, int radius, boolean addCentre) {
        return findAdjacentMaterials(materials, ImmutableList.of(), centre, radius, addCentre);
    }

    /**
     * Find all horizontally adjacent blocks.
     */
    Collection<Block> findAdjacentMaterials(Collection<Material> materials, Collection<Material> ignore, Block centre, int radius, boolean addCentre) {
        var diameter = radius + radius + 1;
        var centreIndex = gridIndex(diameter, radius, 0, 0);
        var adjacent = new Block[diameter * diameter]; // Nullable block array representing a square.
        // In some cases where packets
        if (!materials.contains(centre.getType())) {
            getLogger().warning("Centre material was removed too quickly! Could not find adjacent materials.");
            return Collections.emptyList();
        }
        if (addCentre) adjacent[centreIndex] = centre;
        // +x axis
        {
            var previous = centre;
            var i = 1;
            while (i <= radius) {
                @Nullable var foundAdjacent = locateAdjacentInColumn(materials, ignore, centre, i, 0, previous).orElse(null);
                if (foundAdjacent == null) break;
                adjacent[gridIndex(diameter, radius, i, 0)] = foundAdjacent;
                previous = foundAdjacent;
                i++;
            }
        }
        // -x axis
        {
            var previous = centre;
            var i = -1;
            while (i >= -radius) {
                @Nullable var foundAdjacent = locateAdjacentInColumn(materials, ignore, centre, i, 0, previous).orElse(null);
                if (foundAdjacent == null) break;
                adjacent[gridIndex(diameter, radius, i, 0)] = foundAdjacent;
                previous = foundAdjacent;
                i--;
            }
        }
        // +z axis
        {
            var previous = centre;
            var k = 1;
            while (k <= radius) {
                @Nullable var foundAdjacent = locateAdjacentInColumn(materials, ignore, centre, 0, k, previous).orElse(null);
                if (foundAdjacent == null) break;
                adjacent[gridIndex(diameter, radius, 0, k)] = foundAdjacent;
                previous = foundAdjacent;
                k++;
            }
        }
        // -z axis
        {
            var previous = centre;
            var k = -1;
            while (k >= -radius) {
                @Nullable var foundAdjacent = locateAdjacentInColumn(materials, ignore, centre, 0, k, previous).orElse(null);
                if (foundAdjacent == null) break;
                adjacent[gridIndex(diameter, radius, 0, k)] = foundAdjacent;
                previous = foundAdjacent;
                k--;
            }
        }
        // +x, +z corners
        {
            int i = 1;
            while (i <= radius) {
                int k = 1;
                while (k <= radius) {
                    @Nullable var p1 = adjacent[gridIndex(diameter, radius, i - 1, k)];
                    @Nullable var p2 = adjacent[gridIndex(diameter, radius, i, k - 1)];
                    if (p1 == null && p2 == null) {
                        k++;
                        continue;
                    }
                    @Nullable var foundAdjacent = locateAdjacentInColumn(materials, ignore, centre, i, k, p1, p2).orElse(null);
                    if (foundAdjacent == null) {
                        k++;
                        continue;
                    }
                    adjacent[gridIndex(diameter, radius, i, k)] = foundAdjacent;
                    k++;
                }
                i++;
            }
        }
        // -x, +z corners
        {
            int i = -1;
            while (i >= -radius) {
                int k = 1;
                while (k <= radius) {
                    @Nullable var p1 = adjacent[gridIndex(diameter, radius, i + 1, k)];
                    @Nullable var p2 = adjacent[gridIndex(diameter, radius, i, k - 1)];
                    if (p1 == null && p2 == null) {
                        k++;
                        continue;
                    }
                    @Nullable var foundAdjacent = locateAdjacentInColumn(materials, ignore, centre, i, k, p1, p2).orElse(null);
                    if (foundAdjacent == null) {
                        k++;
                        continue;
                    }
                    adjacent[gridIndex(diameter, radius, i, k)] = foundAdjacent;
                    k++;
                }
                i--;
            }
        }
        // -x, -z corners
        {
            int i = -1;
            while (i >= -radius) {
                int k = -1;
                while (k >= -radius) {
                    @Nullable var p1 = adjacent[gridIndex(diameter, radius, i + 1, k)];
                    @Nullable var p2 = adjacent[gridIndex(diameter, radius, i, k + 1)];
                    if (p1 == null && p2 == null) {
                        k--;
                        continue;
                    }
                    @Nullable var foundAdjacent = locateAdjacentInColumn(materials, ignore, centre, i, k, p1, p2).orElse(null);
                    if (foundAdjacent == null) {
                        k--;
                        continue;
                    }
                    adjacent[gridIndex(diameter, radius, i, k)] = foundAdjacent;
                    k--;
                }
                i--;
            }
        }
        // +x, -z corners
        {
            int i = 1;
            while (i <= radius) {
                int k = -1;
                while (k >= -radius) {
                    @Nullable var p1 = adjacent[gridIndex(diameter, radius, i - 1, k)];
                    @Nullable var p2 = adjacent[gridIndex(diameter, radius, i, k + 1)];
                    if (p1 == null && p2 == null) {
                        k--;
                        continue;
                    }
                    @Nullable var foundAdjacent = locateAdjacentInColumn(materials, ignore, centre, i, k, p1, p2).orElse(null);
                    if (foundAdjacent == null) {
                        k--;
                        continue;
                    }
                    adjacent[gridIndex(diameter, radius, i, k)] = foundAdjacent;
                    k--;
                }
                i++;
            }
        }
        var adjacentSet = new ArrayList<Block>();
        for (@Nullable var block : adjacent) {
            if (block != null) adjacentSet.add(block);
        }
        return adjacentSet;
    }

    /**
     * Convert relative coordinates to grid index.
     */
    static int gridIndex(int diameter, int radius, int i, int k) {
        return ((i + radius) * diameter) + (k + radius);
    }

    static Optional<Block> locateAdjacentInColumn(Collection<Material> materials, Block centre, int i, int k, Block... adjacents) {
        return locateAdjacentInColumn(materials, ImmutableList.of(), centre, i, k, adjacents);
    }

    /**
     * Locate a block with a material adjacent to a centre block, in a specific column.
     *
     * @param materials Materials to locate.
     * @param ignore Materials to treat as air.
     * @param centre Centre block which the search is relative to. Uses same height as
     * @param i Column x-coordinate relative to centre block.
     * @param k Column z-coordinate relative to centre block.
     * @param adjacents Nullable adjacent blocks.
     * @return The adjecent block, if found.
     */
    static Optional<Block> locateAdjacentInColumn(Collection<Material> materials, Collection<Material> ignore, Block centre, int i, int k, Block... adjacents) {
        for (@Nullable var adjacent : adjacents) {
            if (adjacent == null) continue;
            var j = adjacent.getY() - centre.getY();
            var above = centre.getRelative(i, j + 1, k);
            var aboveType = above.getType();
            if (materials.contains(aboveType)) return Optional.of(above);
            var beside = centre.getRelative(i, j, k);
            var besideType = beside.getType();
            if (materials.contains(besideType)) return Optional.of(beside);
            if (!beside.isPassable()) continue;
            var below = centre.getRelative(i, j - 1, k);
            var belowType = below.getType();
            if (materials.contains(belowType)) return Optional.of(below);
        }
        return Optional.empty();
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
    static Optional<Block> findHighestMaterial(Collection<Material> materials, World world, int x, int z, int highest, int lowest) {
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

}
