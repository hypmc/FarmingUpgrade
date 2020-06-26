# FarmingUpgrade

Upgraded hoe and farming mechanics.

### Compatibility

Tested for Spigot 1.16.1.

### Features

All features can be configured and enabled or disabled in the configuration file.

#### Upgraded Harvesting

- Harvesting a grown crop with a hoe automatically replants the crop.
- Hoes do not break crops that are not fully grown.
- Hoes harvest grown crops within a range. Wood and Stone have a range of 0, Iron and Gold a range of 1 and Diamond and Netherite a range of 2.
- Hoes with higher enchantment levels of Unbreaking have a higher chance to not take damage when harvesting crops.
- Hoes with higher enchantment levels of Efficiency increases the range of the hoe. For every 2 levels of Efficiency, the hoe gains 1 range.

#### Upgraded Farmland Hydration

In Vanilla, water hydrates Farmland within a horizontal range of 4 blocks.
Upgraded hydration replaces the Vanilla range with a new horizontal range, height and depth of hydration.

#### Upgraded Farmland Trampling

When a player tramples farmland, the crop that is planted on it is reset to its first growth stage.
The farmland itself is not reset to dirt, like in Vanilla.

#### Upgraded Bonemeal

Bonemeal is weaker but fertilises all adjacent crops too, which makes it slightly stronger than Vanilla bonemeal.

### Configuration

[Default Configuration](https://github.com/Torm/FarmingUpgrade/blob/master/src/main/resources/config.yml)

### Installation

Download and place the FarmingUpgrade jar file in the Bukkit plugins directory.
