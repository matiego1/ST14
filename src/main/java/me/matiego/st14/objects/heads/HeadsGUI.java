package me.matiego.st14.objects.heads;

import lombok.Getter;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.managers.EconomyManager;
import me.matiego.st14.objects.GUI;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;

public class HeadsGUI extends GUI {
    public HeadsGUI(@NotNull List<Head> heads, @NotNull World world, @NotNull Main plugin) {
        this(heads, null, world, plugin);
    }
    private HeadsGUI(@NotNull List<Head> heads, @Nullable HeadsCategory category, @NotNull World world, @NotNull Main plugin) {
        super(6 * 9, Prefix.HEADS + "Sklep");
        this.plugin = plugin;
        this.heads = heads;
        this.category = category;
        this.world = world;

        numberOfPages = heads.size() / PAGE_SIZE;
        if (heads.size() % PAGE_SIZE != 0) {
            numberOfPages++;
        }

        generatePage();
    }

    private final Main plugin;
    private final int PAGE_SIZE = 5 * 9;
    @Getter
    private final List<Head> heads;
    private final HeadsCategory category;
    private final World world;
    private int numberOfPages;
    private int currentPage = 1;

    private void generatePage() {
        Inventory inventory = getInventory();
        if (!(inventory.getHolder() instanceof HeadsGUI)) return;
        inventory.clear();

        Utils.async(() -> {
            int firstHeadIndex = (currentPage - 1) * PAGE_SIZE;
            int headsSize;
            synchronized (heads) {
                for (int i = firstHeadIndex, slot = 0; i < PAGE_SIZE + firstHeadIndex && i < heads.size(); i++, slot++) {
                    inventory.setItem(slot, heads.get(i).getItem());
                }
                headsSize = heads.size();
            }
            inventory.setItem(49, GUI.createGuiItem(
                    Material.CREEPER_HEAD,
                    (category == null ? "Wynik wyszukiwania" : "&9Kategoria: &b" + category),
                    (category == null ? "(&bZnaleziono " + headsSize + " główek)" : "&b(" + headsSize + " główek)"),
                    "&9Strona: &b" + currentPage + " / " + numberOfPages,
                    "&9Cena: &b" + plugin.getEconomyManager().format(getCost()),
                    "&bKliknij na główkę, aby kupić!"
            ));
            if (currentPage > 1) {
                inventory.setItem(47, GUI.createGuiItem(Material.RED_WOOL, "&b<<< Poprzednia strona"));
            }
            if (currentPage < numberOfPages) {
                inventory.setItem(51, GUI.createGuiItem(Material.LIME_WOOL, "&bNastępna strona >>>"));
            }
        });
    }

    private double getCost() {
        return plugin.getHeadsManager().getCost(world);
    }

    public void generateNextPage() {
        if (currentPage >= numberOfPages) return;
        currentPage++;
        generatePage();
    }
    public void generatePreviousPage() {
        if (currentPage <= 1) return;
        currentPage--;
        generatePage();
    }

    public void processInventoryClick(@NotNull InventoryClickEvent event) {
        if (!GUI.checkInventory(event, Prefix.HEADS + "Sklep")) return;

        Inventory inventory = event.getInventory();
        Player player = (Player) event.getWhoClicked();
        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "heads", '.')) {
            player.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cNie możesz użyć tej komendy w tym świecie."));
            inventory.close();
            return;
        }
        if (player.getWorld() != world) {
            player.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cWygląda na to, że zmieniłeś swój świat. Użyj komendy jeszcze raz."));
            inventory.close();
            return;
        }

        int slot = event.getSlot();
        if (slot == 47) {
            inventory.clear();
            generatePreviousPage();
        } else if (slot == 51) {
            inventory.clear();
            generateNextPage();
        } else if (slot < PAGE_SIZE) {
            ItemStack item = event.getCurrentItem();
            if (item == null) return;

            Component nameComponent = item.getItemMeta().displayName();
            String name = (nameComponent == null ? "[?]" : Utils.getPlainTextByComponent(nameComponent));

            double cost = Utils.round(getCost(), 2);
            EconomyManager economy = plugin.getEconomyManager();
            if (cost == 0) {
                giveHead(player, item.clone(), economy.format(0), name);
                return;
            }

            EconomyResponse response = economy.withdrawPlayer(player, cost);
            if (response.transactionSuccess()) {
                giveHead(player, item.clone(), economy.format(cost), name);
                return;
            }
            player.sendMessage(Utils.getComponentByString("&cAby kupić tą główkę potrzebujesz " + economy.format(cost) + ", a masz tylko " + economy.format(response.balance) + "."));
            inventory.close();
        }
    }

    private void giveHead(@NotNull Player player, @NotNull ItemStack head, @NotNull String costFormatted, @NotNull String name) {
        HashMap<Integer, ItemStack> drop = player.getInventory().addItem(head);
        for (ItemStack item : drop.values()) {
            player.getWorld().dropItem(player.getLocation().add(0, 0.5, 0), item);
        }
        player.sendMessage(Utils.getComponentByString(Prefix.HEADS + "Kupiłeś główkę " + name + " za " + costFormatted));

        Logs.info("Gracz " + player.getName() + " kupił główkę " + name + " za " + costFormatted + ". (Na ziemi? " + (drop.isEmpty() ? "Nie" : "Tak") + ")");
    }

    public static @Nullable HeadsGUI createHeadsGUI(@NotNull HeadsCategory category, @NotNull World world, @NotNull Main plugin) {
        List<Head> heads = category.getHeads();
        if (heads == null) return null;
        return new HeadsGUI(heads, category, world, plugin);
    }
}
