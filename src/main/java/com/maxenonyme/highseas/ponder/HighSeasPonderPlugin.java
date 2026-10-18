package com.maxenonyme.highseas.ponder;

import com.maxenonyme.highseas.CreateHighSeas;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class HighSeasPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return CreateHighSeas.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        HighSeasPonderScenes.register(helper);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        ResourceLocation tag = ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "high_seas");
        helper.registerTag(tag)
                .item(CreateHighSeas.SAIL_ITEM.get(), true, false)
                .title("High Seas")
                .description("Sailing ships and how to steer them")
                .addToIndex()
                .register();
        helper.addToTag(tag)
                .add(HighSeasPonderScenes.SAIL)
                .add(HighSeasPonderScenes.RUDDER);
    }
}
