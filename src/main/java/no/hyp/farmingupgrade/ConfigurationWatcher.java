package no.hyp.farmingupgrade;

import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.file.*;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * This runnable watches for changes to the configuration file. When the configuration is modified,
 * the plugin reads the configuration again. This allows a user to edit the configuration and see
 * the changes in game without reloading the server.
 */
class ConfigurationWatcher implements Runnable {

    private final FarmingUpgrade plugin;

    private final Path path;

    public ConfigurationWatcher(FarmingUpgrade plugin, Path path) {
        this.plugin = plugin;
        this.path = path;
    }

    @Override
    public void run() {
        // Create a WatchService that watches for changes to the configuration file.
        WatchService configurationWatcher;
        try {
            configurationWatcher = FileSystems.getDefault().newWatchService();
            path.register(configurationWatcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        // Keep polling the WatchService for changes.
        while (true) {
            WatchKey key;
            try {
                key = configurationWatcher.take();
                // Sleep a short time to collect duplicate events.
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            for (WatchEvent event : key.pollEvents()) {
                if (event.context() instanceof Path) {
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    if (pathEvent.context().toString().equals("config.yml")) {
                        // If the configuration is created, read it.
                        if (event.kind() == ENTRY_CREATE) {
                            Bukkit.getScheduler().runTask(plugin, (() -> {
                                plugin.getLogger().info("Configuration was created.");
                                plugin.reloadConfig();
                            }));
                            // If the configuration is deleted, the user is probably replacing it. Do nothing in the meanwhile.
                        } else if (event.kind() == ENTRY_DELETE) {
                            Bukkit.getScheduler().runTask(plugin, (() -> {
                                plugin.getLogger().info("Configuration was deleted.");
                            }));
                            // If the configuration is modified, reload it.
                        } else if (event.kind() == ENTRY_MODIFY) {
                            Bukkit.getScheduler().runTask(plugin, (() -> {
                                plugin.getLogger().info("Configuration was modified.");
                                plugin.reloadConfig();
                            }));
                        }
                    }
                }
            }
            key.reset();
        }
        // Finish by closing the WatchService.
        try {
            configurationWatcher.close();
        } catch (IOException e) {
            e.printStackTrace();
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().severe("Error closing configuration watcher.");
            });
        }
    }

}