package ru.stepan1411.kits_browser;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.stepan1411.kits_browser.command.KitWebCommand;

public class Kits_browser implements ModInitializer {
    public static final String MOD_ID = "kits_browser";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Kits Browser");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            KitWebCommand.register(dispatcher);
        });

        LOGGER.info("Kits Browser initialized");
    }
}
