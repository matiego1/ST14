package me.matiego.st14.objects.heads;

import lombok.Getter;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.managers.EconomyManager;
import me.matiego.st14.objects.GUI;
import me.matiego.st14.utils.Utils;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;

public class HeadsGUI extends GUI {
    private HeadsGUI(@NotNull List<Head> heads, @NotNull HeadsCategory category) {
        super(6 * 9, Prefix.HEADS + "Sklep");
        this.heads = heads;
        this.category = category;

        numberOfPages = heads.size() / PAGE_SIZE;
        if (heads.size() % PAGE_SIZE != 0) {
            numberOfPages++;
        }

        generatePage();
    }

    private final int PAGE_SIZE = 5 * 9;
    @Getter
    private final List<Head> heads;
    @Getter
    private final HeadsCategory category;
    private int numberOfPages;
    private int currentPage = 1;

    private void generatePage() {
        Inventory inventory = getInventory();
        if (!(inventory.getHolder() instanceof HeadsGUI)) return;
        inventory.clear();

        int firstHeadIndex = (currentPage - 1) * PAGE_SIZE;
        for (int i = firstHeadIndex; i < PAGE_SIZE + firstHeadIndex; i++) {
            inventory.setItem(i, heads.get(i).getItem());
        }

        inventory.setItem(4, GUI.createGuiItem(Material.PLAYER_HEAD, "&5Kategoria: " + category,"&bStrona:" + currentPage + " / " + numberOfPages, "&bCena: " + getCost(), "&bKliknij na główkę, aby kupić!"));
        if (currentPage > 1) {
            inventory.setItem(2, GUI.createGuiItem(Material.RED_WOOL, "&b<<< Poprzednia strona"));
        }
        if (currentPage < numberOfPages) {
            inventory.setItem(6, GUI.createGuiItem(Material.LIME_WOOL, "&bNastępna strona >>>"));
        }
    }

    private @NotNull String getCost() {
        Main plugin = Main.getInstance();
        return plugin.getEconomyManager().format(plugin.getHeadsManager().getCost());
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

    public void processInventoryClick(@NotNull InventoryClickEvent event, @NotNull Main plugin) {
        if (!GUI.checkInventory(event, Prefix.HEADS + "Sklep")) return;

        Player player = (Player) event.getWhoClicked();
        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "spawn", '.')) {
            player.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cNie możesz użyć tej komendy w tym świecie."));
            return;
        }

        int slot = event.getSlot();
        if (slot == 2) {
            generatePreviousPage();
        } else if (slot == 6) {
            generateNextPage();
        } else if (slot < PAGE_SIZE) {
            ItemStack item = event.getCurrentItem();
            if (item == null) return;

            double cost = 0;
            if (!plugin.getConfig().getStringList("heads.free-worlds").contains(player.getWorld().getName())) {
                cost = plugin.getHeadsManager().getCost();
            }
            cost = Utils.round(cost, 2);

            EconomyManager economy = plugin.getEconomyManager();
            if (cost == 0) {
                giveHead(player, item.clone(), economy.format(0));
                return;
            }

            EconomyResponse response = economy.withdrawPlayer(player, cost);
            if (response.transactionSuccess()) {
                giveHead(player, item.clone(), economy.format(cost));
                return;
            }
            player.sendMessage(Utils.getComponentByString("&cAby kupić tą główkę potrzebujesz " + economy.format(cost) + ", a masz tylko " + economy.format(response.balance) + "."));
        }
    }

    private void giveHead(@NotNull Player player, @NotNull ItemStack head, @NotNull String costFormatted) {
        HashMap<Integer, ItemStack> drop = player.getInventory().addItem(head);
        for (ItemStack item : drop.values()) {
            player.getWorld().dropItem(player.getLocation().add(0, 0.5, 0), item);
        }

        Logs.info("Gracz " + player.getName() + " kupił główkę za " + costFormatted + ". (Na ziemi? " + (drop.isEmpty() ? "Nie" : "Tak") + ")");
    }

    public static @Nullable HeadsGUI createHeadsGUI(@NotNull HeadsCategory category) {
        List<Head> heads = category.getHeads();
        if (heads == null) return null;
        if (heads.isEmpty()) return null;
        return new HeadsGUI(heads, category);
    }
}
