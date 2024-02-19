package net.runelite.client.plugins.xo.blastfurnace;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.ethan.EthanApiPlugin.Collections.*;
import net.runelite.client.plugins.ethan.EthanApiPlugin.EthanApiPlugin;
import net.runelite.client.plugins.ethan.InteractionApi.BankInteraction;
import net.runelite.client.plugins.ethan.InteractionApi.BankInventoryInteraction;
import net.runelite.client.plugins.ethan.InteractionApi.InventoryInteraction;
import net.runelite.client.plugins.ethan.InteractionApi.TileObjectInteraction;
import net.runelite.client.plugins.ethan.Packets.MousePackets;
import net.runelite.client.plugins.ethan.Packets.MovementPackets;
import net.runelite.client.plugins.ethan.Packets.WidgetPackets;
import net.runelite.client.util.ColorUtil;

import javax.inject.Inject;
import java.awt.*;
import java.util.*;
import java.util.List;

@PluginDescriptor(
        name = "<html><font color=#D4A4FF>[XO]</font> Auto Blast Furnace</html>",
        enabledByDefault = false,
        description = "",
        tags = {"xo"}
)
@Slf4j
public class AutoBlastFurnacePlugin extends Plugin {

    private final List<Integer> STAMINAS_IDS = List.of(
            ItemID.STAMINA_POTION1,
            ItemID.STAMINA_POTION2,
            ItemID.STAMINA_POTION3,
            ItemID.STAMINA_POTION4
    );

    @Inject
    private Client client;

    private State state;
    private boolean useGauntlets;
    private boolean useGloves;
    private boolean useStaminas;

    private boolean running;
    private int timeout;

    private GameObject conveyorBelt;
    private GameObject furnace;

    @Override
    protected void startUp() {
        state = State.OPEN_BANK;
        running = false;
        useGauntlets = true;
        useGloves = true;
        useStaminas = true;
    }

    @Subscribe
    void onGameObjectSpawned(GameObjectSpawned event) {
        switch (event.getGameObject().getId()) {
            case ObjectID.CONVEYOR_BELT:
                conveyorBelt = event.getGameObject();
                running = true;
                break;
            case NullObjectID.NULL_9092:
                furnace = event.getGameObject();
                running = true;
                break;
        }
    }

    @Subscribe
    void onGameObjectDespawned(GameObjectDespawned event) {
        switch (event.getGameObject().getId()) {
            case ObjectID.CONVEYOR_BELT:
                conveyorBelt = null;
                running = false;
                break;
            case NullObjectID.NULL_9092:
                furnace = null;
                running = false;
                break;
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOADING)
        {
            conveyorBelt = null;
            furnace = null;
        }
    }

    @Subscribe
    void onGameTick(GameTick event) {
        if (!running) return;
        if (timeout-- > 0) return;

        getState();

        switch(state) {
            case DEPOSIT_STAMINA:
                depositStaminaState();
                break;
            case WITHDRAW_STAMINA:
                withdrawStaminaState();
                break;
            case CONSUME_STAMINA:
                consumeStaminaState();
                break;
            case COLLECT_BARS:
                collectBarsState();
                break;
            case DEPOSIT_INVENTORY:
                depositInventoryState();
                break;
            case WITHDRAW_GOLDSMITH_GAUNTLETS:
                withdrawGauntletsState();
                break;
            case EQUIP_GOLDSMITH_GAUNTLETS:
                equipGauntletsState();
                break;
            case WITHDRAW_ICE_GLOVES:
                withdrawGlovesState();
                break;
            case EQUIP_ICE_GLOVES:
                equipGlovesState();
                break;
            case WITHDRAW_ORE:
                withdrawOreState();
                break;
            case PUT_ON_CONVEYOR_BELT:
                putOnConveyorBeltState();
                break;
            case OPEN_BANK:
                openBankState();
                break;
            case WAIT_AT_FURNACE:
                waitAtFurnaceState();
                break;
            case NONE:
            default:
                chatMessage(String.format("Bad state: %s", state.name()), Color.red);
                running = false;
                break;
        }
    }

    private List<EquipmentItemWidget> getEquipmentById(int id) {
        return Equipment.search().withId(id).result();
    }

    private List<Widget> getInvById(int id) {
        return Inventory.search().withId(id).result();
    }

    private List<Widget> getBankInvById(int id) {
        return getBankInvById(Collections.singletonList(id));
    }

    private List<Widget> getBankInvById(List<Integer> ids) {
        return BankInventory.search().idInList(ids).result();
    }

    private State getState() {
        boolean furnaceHasBar = client.getVarbitValue(Varbits.BLAST_FURNACE_GOLD_BAR) > 0;

        if (Bank.isOpen()) {
            if (furnaceHasBar) {
                if (!getBankInvById(ItemID.GOLD_BAR).isEmpty() || !getBankInvById(ItemID.GOLD_ORE).isEmpty()) {
                    return updateState(State.DEPOSIT_INVENTORY, 1);
                }

                return updateState(State.COLLECT_BARS, 1);
            }

            if (!getBankInvById(ItemID.GOLD_BAR).isEmpty()) {
                return updateState(State.DEPOSIT_INVENTORY, 1);
            }

            if (useGauntlets && getEquipmentById(ItemID.GOLDSMITH_GAUNTLETS).isEmpty()) {
                if (getBankInvById(ItemID.GOLDSMITH_GAUNTLETS).isEmpty()) {
                    return updateState(State.WITHDRAW_GOLDSMITH_GAUNTLETS, 1);
                }

                return updateState(State.EQUIP_GOLDSMITH_GAUNTLETS, 1);
            }

            if (useGloves && getBankInvById(ItemID.ICE_GLOVES).isEmpty()) {
                return updateState(State.WITHDRAW_ICE_GLOVES, 1);
            }

            boolean needsStamina = client.getEnergy() < 75 * 100 && client.getVarbitValue(Varbits.RUN_SLOWED_DEPLETION_ACTIVE) == 0;

            if (useStaminas && needsStamina) {
                if (getBankInvById(STAMINAS_IDS).isEmpty()) {
                    return updateState(State.WITHDRAW_STAMINA, 1);
                }

                return updateState(State.CONSUME_STAMINA, 1);
            }

            if (State.CONSUME_STAMINA.equals(state)) {
                return updateState(State.DEPOSIT_STAMINA, 1);
            }

            if (getBankInvById(ItemID.GOLD_ORE).isEmpty()) {
                return updateState(State.WITHDRAW_ORE, 1);
            }
        }

        if (!getInvById(ItemID.GOLD_ORE).isEmpty() && !furnaceHasBar) {
            if (useGauntlets && getEquipmentById(ItemID.GOLDSMITH_GAUNTLETS).isEmpty()) {
                return updateState(State.EQUIP_GOLDSMITH_GAUNTLETS, 1);
            }

            return updateState(State.PUT_ON_CONVEYOR_BELT, 1);
        }

        if (State.PUT_ON_CONVEYOR_BELT.equals(state)) {
            return updateState(State.WAIT_AT_FURNACE, 1);
        }

        if (State.WAIT_AT_FURNACE.equals(state) || State.EQUIP_ICE_GLOVES.equals(state)) {
            if (!furnaceHasBar) {
                return updateState(State.WAIT_AT_FURNACE, 1);
            }

            if (useGloves && getEquipmentById(ItemID.ICE_GLOVES).isEmpty()) {
                return updateState(State.EQUIP_ICE_GLOVES, 1);
            }

            return updateState(State.COLLECT_BARS, 1);
        }

        if (State.COLLECT_BARS.equals(state)) {
            if (getInvById(ItemID.GOLD_BAR).isEmpty()) {
                return updateState(State.COLLECT_BARS, 1);
            }
        }

        return updateState(State.OPEN_BANK, 1);
    }

    private void depositStaminaState() {
        if (isMoving()) return;

        BankInventory
                .search()
                .idInList(STAMINAS_IDS)
                .first()
                .ifPresent(e -> BankInventoryInteraction.useItem(e, "Deposit-All"));
    }

    private void consumeStaminaState() {
        if (isMoving()) return;

        BankInventory.search()
                .idInList(STAMINAS_IDS)
                .first()
                .ifPresent(e -> BankInventoryInteraction.useItem(e, "Drink"));
    }

    private void withdrawStaminaState() {
        if (isMoving()) return;

        List<Widget> stams = Bank.search().idInList(STAMINAS_IDS).result();
        stams.sort(Comparator.comparing(Widget::getName));
        if (!stams.isEmpty()) {
            BankInteraction.useItem(stams.get(0), "Withdraw-1");
        } else {
            useStaminas = false;
        }
    }

    private void waitAtFurnaceState() {
        if (isMoving()) return;

        if (!EthanApiPlugin.playerPosition().equals(furnace.getWorldLocation().dy(-1))) {
            MousePackets.queueClickPacket();
            MovementPackets.queueMovement(furnace.getWorldLocation().dy(-1));
        }
    }

    private void openBankState() {
        if (isMoving()) return;

        TileObjects
                .search()
                .withId(26707)
                .first()
                .ifPresent(e -> TileObjectInteraction.interact(e, "Use"));
    }

    private void putOnConveyorBeltState() {
        if (isMoving()) return;

        TileObjectInteraction.interact(conveyorBelt, "Put-ore-on");
    }

    private void withdrawOreState() {
        if (isMoving()) return;

        Bank
                .search()
                .withId(ItemID.GOLD_ORE)
                .first()
                .ifPresentOrElse(
                        e -> BankInteraction.useItem(e, "Withdraw-All"),
                        () -> stopPlugin("No ore found in bank")
                );
    }

    private void equipGlovesState() {
        if (isMoving()) return;

        Inventory
                .search()
                .withId(ItemID.ICE_GLOVES)
                .first()
                .ifPresent(e -> InventoryInteraction.useItem(e, "Wear"));
    }

    private void withdrawGlovesState() {
        if (isMoving()) return;

        Bank
                .search()
                .withId(ItemID.ICE_GLOVES)
                .first()
                .ifPresentOrElse(
                        e -> BankInteraction.useItem(e, "Withdraw-1"),
                        () -> useGloves = false
                );
    }

    private void equipGauntletsState() {
        if (isMoving()) return;

        BankInventory
                .search()
                .withId(ItemID.GOLDSMITH_GAUNTLETS)
                .first()
                .ifPresent(e -> InventoryInteraction.useItem(e, "Wear"));
    }

    private void withdrawGauntletsState() {
        if (isMoving()) return;

        Bank
                .search()
                .withId(ItemID.GOLDSMITH_GAUNTLETS)
                .first()
                .ifPresentOrElse(
                        e -> BankInteraction.useItem(e, "Withdraw-1"),
                        () -> useGauntlets = false
                );
    }

    private void depositInventoryState() {
        if (isMoving()) return;

        BankInventory
                .search()
                .withId(ItemID.GOLD_ORE)
                .first()
                .ifPresent(e -> BankInventoryInteraction.useItem(e, "Deposit-All"));

        BankInventory
                .search()
                .withId(ItemID.GOLD_BAR)
                .first()
                .ifPresent(e -> BankInventoryInteraction.useItem(e, "Deposit-All"));
    }

    private void collectBarsState() {
        if (isMoving()) return;

        Optional<Widget> widget = Widgets
                .search()
                .withId(17694734)
                .withAction("Take")
                .first();

        if (widget.isPresent()) {
            int amount = client.getVarbitValue(Varbits.BLAST_FURNACE_GOLD_BAR);

            MousePackets.queueClickPacket();
            WidgetPackets.queueResumePause(17694734, amount);
            return;
        }

        TileObjectInteraction.interact(furnace, "Take");
    }

    private State updateState(State state, int timeout) {
        this.state = state;
        this.timeout = timeout;
        return state;
    }

    private void stopPlugin(String message) {
        chatMessage(message, Color.RED);
        EthanApiPlugin.stopPlugin(this);
    }

    private boolean isLoggedIn() {
        return client.getGameState() == GameState.LOGGED_IN;
    }

    private boolean isMoving() {
        return EthanApiPlugin.isMoving()
                || client.getLocalPlayer().getPoseAnimation() != client.getLocalPlayer().getIdlePoseAnimation();
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
