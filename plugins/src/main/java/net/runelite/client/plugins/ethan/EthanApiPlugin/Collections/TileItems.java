package net.runelite.client.plugins.ethan.EthanApiPlugin.Collections;

import net.runelite.client.plugins.ethan.EthanApiPlugin.Collections.query.TileItemQuery;

import java.util.ArrayList;
import java.util.List;

public class TileItems {
    public static List<ETileItem> tileItems = new ArrayList<>();

    public static TileItemQuery search() {
        return new TileItemQuery(tileItems);
    }
}
