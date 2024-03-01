package net.runelite.client.plugins.xo.homeprayer;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.ethan.EthanApiPlugin.Collections.Inventory;
import net.runelite.client.plugins.ethan.EthanApiPlugin.Collections.NPCs;
import net.runelite.client.plugins.ethan.EthanApiPlugin.Collections.TileObjects;
import net.runelite.client.plugins.ethan.EthanApiPlugin.Collections.Widgets;
import net.runelite.client.plugins.ethan.EthanApiPlugin.EthanApiPlugin;
import net.runelite.client.plugins.ethan.InteractionApi.TileObjectInteraction;
import net.runelite.client.plugins.ethan.Packets.MousePackets;
import net.runelite.client.plugins.ethan.Packets.NPCPackets;
import net.runelite.client.plugins.ethan.Packets.ObjectPackets;
import net.runelite.client.plugins.ethan.Packets.WidgetPackets;
import net.runelite.client.util.ColorUtil;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = "<html><font color=#D4A4FF>[XO]</font> Home Prayer</html>",
        enabledByDefault = false,
        description = "",
        tags = {"xo"}
)
@Slf4j
public class HomePrayerPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private HomePrayerConfig config;

    private State state;

    private boolean running;
    private int timeout;

    @Provides
    private HomePrayerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HomePrayerConfig.class);
    }

    @Override
    protected void startUp() {
        state = State.NONE;
        running = true;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        running = GameState.LOGGED_IN.equals(event.getGameState());
    }

    @Subscribe
    private void onChatMessage(ChatMessage event) {
        if (ChatMessageType.GAMEMESSAGE.equals(event.getType())) {
            String message = event.getMessage();
            if (message.contains("You haven't visited anyone this session.")) {
                stopPlugin("You need to enter a house with an altar first. Stopping plugin.");
            }
        }
    }

    @Subscribe
    void onGameTick(GameTick event) {
        if (!running) return;

        if (timeout > 0) {
            timeout--;
            return;
        }

        getState();

        switch (state) {
            case LEAVING_HOUSE:
                leaveHouse();
                break;
            case UNNOTING_BONES:
                useNotedBonesOnPhials();
                break;
            case ENTERING_HOUSE:
                enterHouse();
                break;
            case HANDLING_DIALOG:
                handleDialog();
                break;
            case RESTORING_STAMINA:
                drinkFromPool();
                break;
            case USING_BONES_ON_ALTAR:
                useBoneOnAltar();
                break;
            case NONE:
            default:
                stopPlugin(String.format("Bad state: %s", state.name()));
                break;
        }
    }

    private void enterHouse() {
        if (shouldSkip()) return;

        TileObjects
                .search()
                .withId(29091)
                .nearestToPlayer()
                .ifPresent(house -> TileObjectInteraction.interact(house, "Visit-Last"));

    }

    private void leaveHouse() {
        if (shouldSkip()) return;

        TileObjects
                .search()
                .nameContains("Portal")
                .nearestToPlayer()
                .ifPresent(portal -> TileObjectInteraction.interact(portal, "Enter"));

    }

    private void drinkFromPool() {
        if (shouldSkip()) return;

        TileObjects
                .search()
                .nameContains("Pool")
                .withAction("Drink")
                .nearestToPlayer()
                .ifPresent(pool -> TileObjectInteraction.interact(pool, "Drink"));
    }

    private void useNotedBonesOnPhials() {
        if (shouldSkip()) return;

        NPCs
                .search()
                .withName("Phials")
                .nearestToPlayer()
                .ifPresent(
                        phials -> Inventory
                                .search()
                                .withName(config.nameOfBones())
                                .first()
                                .ifPresent(bones -> {
                                    MousePackets.queueClickPacket();
                                    NPCPackets.queueWidgetOnNPC(phials, bones);
                                }));
    }

    private void handleDialog() {
        if (shouldSkip()) return;

        Widgets.search().withTextContains("Exchange All").first().ifPresent(
                widget -> {
                    if (!isDialogOpen() && !widget.isHidden()) {
                        MousePackets.queueClickPacket();
                    }

                    WidgetPackets.queueResumePause(14352385, 3);
                }
        );
    }

    private void useBoneOnAltar() {
        if (!config.oneTickAltar() && shouldSkip()) return;

        Inventory
                .search()
                .onlyUnnoted()
                .nameContains(config.nameOfBones())
                .first()
                .ifPresent(bone -> TileObjects
                        .search()
                        .withName(config.nameOfAltar())
                        .first()
                        .ifPresent(altar -> {
                            MousePackets.queueClickPacket();
                            ObjectPackets.queueWidgetOnTileObject(bone, altar);
                        }));
    }

    private State getState() {
        validatePrerequisites();

        if (isOutsideHouse() && isDialogOpen()) {
            return updateState(State.HANDLING_DIALOG, 0);
        }

        if (isOutsideHouse() && inventoryNotFull() && noBonesLeft()) {
            return updateState(State.UNNOTING_BONES, 1);
        }

        if (isOutsideHouse() && hasUnnotedBonesInInventory()) {
            return updateState(State.ENTERING_HOUSE, 1);
        }

        if (isInInstance() && poolIsPresent() && lowRunEnergy()) {
            return updateState(State.RESTORING_STAMINA, 1);
        }

        if (isInInstance() && hasUnnotedBonesInInventory() && altarInVicinity()) {
            return updateState(State.USING_BONES_ON_ALTAR, config.oneTickAltar() ? 0 : 1);
        }

        if (isInInstance() && inventoryNotFull() && noBonesLeft()) {
            return updateState(State.LEAVING_HOUSE, 1);
        }

        return updateState(State.NONE, 1);
    }

    public void validatePrerequisites() {
        if (Inventory.search().onlyNoted().withName(config.nameOfBones()).first().isEmpty()) {
            stopPlugin("You are out of noted bones. Stopping plugin.");
        }

        if (Inventory.search().withName("Coins").first().isEmpty()) {
            stopPlugin("You are out of coins. Stopping plugin.");
        }
    }

    public boolean hasUnnotedBonesInInventory() {
        return Inventory.search().onlyUnnoted().nameContains(config.nameOfBones()).first().isPresent();
    }

    public boolean lowRunEnergy() {
        return client.getEnergy() < 25 * 100;
    }

    public boolean inventoryNotFull() {
        return !Inventory.full();
    }

    public boolean noBonesLeft() {
        return Inventory.search().onlyUnnoted().nameContains(config.nameOfBones()).empty();
    }

    public boolean isDialogOpen() {
        return (!Widgets.search().withId(14352384).empty());
    }

    public boolean poolIsPresent() {
        return TileObjects.search().nameContains("Pool").withAction("Drink").nearestToPlayer().isPresent();
    }

    public boolean isInInstance() {
        return client.getLocalPlayer().getWorldLocation().isInScene(client);
    }

    public boolean altarInVicinity() {
        return TileObjects.search().withinDistance(10).withName(config.nameOfAltar()).nearestToPlayer().isPresent();
    }

    public boolean isOutsideHouse() {
        return TileObjects.search().withId(29091).nearestToPlayer().isPresent();
    }

    private State updateState(State state, int timeout) {
        this.state = state;
        this.timeout = timeout;
        return state;
    }

    private void stopPlugin(String message) {
        running = false;
        chatMessage(message, Color.RED);
        EthanApiPlugin.stopPlugin(this);
    }

    private boolean shouldSkip() {
        boolean isMoving = EthanApiPlugin.isMoving() || client.getLocalPlayer().getPoseAnimation() != client.getLocalPlayer().getIdlePoseAnimation();
        boolean isInteracting = client.getLocalPlayer().isInteracting();

        return isMoving || isInteracting;
    }

    private void chatMessage(String text, Color color) {
        client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                ColorUtil.wrapWithColorTag(text, color),
                ""
        );
    }

}
