package com.example.mobresistlock;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(MobResistLock.MODID)
public class MobResistLock {
    public static final String MODID = "mobresistlock";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MobResistLock(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(new ResistanceGuard());
        NeoForge.EVENT_BUS.addListener(EditCommand::onRegisterCommands);
    }
}
