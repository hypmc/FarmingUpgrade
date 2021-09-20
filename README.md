# FarmingUpgrade

Upgraded farming mechanics.

### Compatibility

Tested for Spigot 1.17.1.

### Features

All features can be configured, enabled or disabled in the configuration file.

#### Upgraded harvesting

- **Harvest tools** Configurable list of harvest tools. Primarily intended for hoes.
- **Area harvest tools** Tools may harvest crops within a radius. Radius depends on quality of tool, such as material, enchantments or item lore.
- **Automatic replanting** Tools may replant the crop when it harvests it.
- **Only break mature crops** Tools only break mature crops.
- **Unbreaking applies** Tools with higher enchantment levels of Unbreaking have a higher chance to not take damage when harvesting crops.
- **Collection mode** The yield from harvesting may be collected directly in the player's inventory.

#### Upgraded farmland hydration

- **Customised water search radiï** In Vanilla, water hydrates Farmland within a horizontal radius of 4 blocks.
Upgraded hydration replaces the Vanilla range with a new horizontal range, height and depth of hydration.
- **Farmland drought** Enable or disable farmland turning back to dirt when it is not hydrated.

#### Upgraded fertilisation

- **Area fertilisation** Bonemeal is weaker but fertilises all adjacent crops too, which makes it slightly stronger than Vanilla bonemeal.

#### Upgraded Farmland Trampling

- **Crop trampling** When a player tramples farmland, the crop that is planted on it is reset to its first growth stage.
The farmland itself is not reset to dirt, like in Vanilla.
- **Trample by walking** A player may trample crops simply by walking or running over them.

### Commands

`farmingupgrade reload` - Reload the configuration file. Requires the permission `farmingupgrade.administrator`.

### Configuration

[Default configuration](https://github.com/Torm/FarmingUpgrade/blob/master/src/main/resources/config.yml)

### Installation

Download and place the FarmingUpgrade jar file in the Bukkit server's `plugins` directory.
