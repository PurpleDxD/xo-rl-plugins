package net.runelite.client.plugins.xo.homeprayer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("homePrayerConfig")
public interface HomePrayerConfig extends Config {

    @ConfigSection(
            name = "<html><font color=#D4A4FF>Configuration</font>",
            description = "Bone and Altar configuration",
            position = 1

    )
    String userConfiguration = "userConfiguration";

    @ConfigItem(
            keyName = "boneName",
            name = "Bones to use",
            description = "What bones to use on the altar",
            position = 2,
            section = userConfiguration
    )
    default String nameOfBones() {
        return "Bones";
    }

    @ConfigItem(
            keyName = "altarName",
            name = "Altar name",
            description = "What altar to use",
            position = 3,
            section = userConfiguration
    )
    default String nameOfAltar() {
        return "Altar";
    }

    @ConfigItem(
            keyName = "oneTickAltar",
            name = "One Tick Altar",
            description = "Should the bones be used on the altar tick perfect",
            position = 4,
            section = userConfiguration
    )
    default boolean oneTickAltar() {
        return true;
    }

}