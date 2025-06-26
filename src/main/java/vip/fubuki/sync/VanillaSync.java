package vip.fubuki.sync;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.MaterialData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import vip.fubuki.playersync;
import vip.fubuki.util.JDBCsetUp;
import vip.fubuki.util.LocalJsonUtil;
import vip.fubuki.util.PSThreadPoolFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VanillaSync implements Listener {

    static ExecutorService executorService = Executors.newCachedThreadPool(new PSThreadPoolFactory("PlayerSync"));

    public void doPlayerJoin(PlayerJoinEvent event) throws SQLException, IOException {
        String player_uuid = event.getPlayer().getUniqueId().toString();

        // First query: check basic player data
        JDBCsetUp.QueryResult qr1 = JDBCsetUp.executeQuery("SELECT online, last_server FROM player_data WHERE uuid='" + player_uuid + "'");
        ResultSet rs1 = qr1.resultSet();
        Player serverPlayer = event.getPlayer();
        if (!rs1.next()){
            store(serverPlayer, true);
            return;
        }

        // Second query: retrieve full player data
        JDBCsetUp.QueryResult qr2 = JDBCsetUp.executeQuery("SELECT * FROM player_data WHERE uuid='" + player_uuid + "'");
        ResultSet rs2 = qr2.resultSet();

        JDBCsetUp.executeUpdate("UPDATE player_data SET online= '1',last_server=" + playersync.JdbcConfig.SERVER_ID + " WHERE uuid='" + player_uuid + "'");

        if (rs2.next()) {
            // Restore basic attributes
            serverPlayer.setHealth(rs2.getInt("health"));
            serverPlayer.setFoodLevel(rs2.getInt("food_level"));

            setXpForPlayer(serverPlayer, rs2.getInt("xp"));

            // Restore left-hand item
            String leftHandEncoded = rs2.getString("left_hand");
            serverPlayer.getInventory().setItemInOffHand(deserializeAndCreatePlaceholderIfNeeded(leftHandEncoded));

            // Restore cursor item
            String cursorsEncoded = rs2.getString("cursors");
            serverPlayer.setItemOnCursor(deserializeAndCreatePlaceholderIfNeeded(cursorsEncoded));

            // Restore armor
            String armor_data = rs2.getString("armor");
            if (armor_data.length() > 2) {
                Map<Integer, String> equipment = LocalJsonUtil.StringToEntryMap(armor_data);
                ItemStack[] armorList = new ItemStack[4];
                for (Map.Entry<Integer, String> entry : equipment.entrySet()) {
                    armorList[entry.getKey()] = deserializeAndCreatePlaceholderIfNeeded(entry.getValue());
                }
                serverPlayer.getInventory().setArmorContents(armorList);
            }

            // Restore inventory
            Map<Integer, String> inventory = LocalJsonUtil.StringToEntryMap(rs2.getString("inventory"));
            for (Map.Entry<Integer, String> entry : inventory.entrySet()) {
                serverPlayer.getInventory().setItem(entry.getKey(), deserializeAndCreatePlaceholderIfNeeded(entry.getValue()));
            }

            // Restore Ender Chest
            Map<Integer, String> ender_chest = LocalJsonUtil.StringToEntryMap(rs2.getString("enderchest"));
            for (Map.Entry<Integer, String> entry : ender_chest.entrySet()) {
                serverPlayer.getEnderChest().setItem(entry.getKey(), deserializeAndCreatePlaceholderIfNeeded(entry.getValue()));
            }

            // Restore Effects
            String effectData = rs2.getString("effects");
            if (effectData.length() > 2) {

                for (PotionEffect effect : serverPlayer.getActivePotionEffects()) {
                    serverPlayer.removePotionEffect(effect.getType());
                }

                Map<Integer, String> effects = LocalJsonUtil.StringToEntryMap(effectData);
                for (Map.Entry<Integer, String> entry : effects.entrySet()) {
                    PotionEffectType type = PotionEffectType.getById(entry.getKey());
                    String str = entry.getValue();
                    String[] parts = str.split("\\|");
                    int duration = Integer.parseInt(parts[0]);
                    int amplifier = Integer.parseInt(parts[1]);
                    if (type != null) {
                        serverPlayer.addPotionEffect(new PotionEffect(type,duration,amplifier));
                    }
                }
            }
        }

        rs2.close();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) throws SQLException, IOException {
        doPlayerJoin(event);
    }

    // deserialize item and potentially create placeholders
    private ItemStack deserializeAndCreatePlaceholderIfNeeded(String serializedNbt) {
        String nbtString = deserializeString(serializedNbt);
        Map <String, Object> nbtMap = LocalJsonUtil.mapDeserialize(nbtString);
        if (nbtMap.isEmpty()) {
            return new ItemStack(Material.AIR); // Return empty item if no data
        }
        return deserialize_modify(nbtMap);
    }

    @NotNull
    public static ItemStack deserialize_modify(@NotNull Map<String, Object> args) {
        int version = args.containsKey("v") ? Integer.parseInt((String) args.get("v")) : -1;
        short damage = 0;
        int amount = 1;
        if (args.containsKey("damage")) {
            damage = Short.parseShort((String) args.get("damage"));
        }

        Material type;
        if (version < 0) {
            type = Material.getMaterial("LEGACY_" + args.get("type"));
            byte dataVal = type != null && type.getMaxDurability() == 0 ? (byte)damage : 0;
            type = Bukkit.getUnsafe().fromLegacy(new MaterialData(type, dataVal), true);
            if (dataVal != 0) {
                damage = 0;
            }
        } else {
            type = Bukkit.getUnsafe().getMaterial((String)args.get("type"), version);
        }

        if (args.containsKey("amount")) {
            amount = Integer.parseInt((String) args.get("amount"));
        }

        ItemStack result = new ItemStack(type, amount, damage);
        Object raw;
        if (args.containsKey("enchantments")) {
            raw = args.get("enchantments");
            if (raw instanceof Map) {
                Map<?, ?> map = (Map)raw;
                Iterator var8 = map.entrySet().iterator();

                while(var8.hasNext()) {
                    Map.Entry<?, ?> entry = (Map.Entry)var8.next();
                    String stringKey = entry.getKey().toString();
                    stringKey = Bukkit.getUnsafe().get(Enchantment.class, stringKey);
                    NamespacedKey key = NamespacedKey.fromString(stringKey.toLowerCase(Locale.ROOT));
                    Enchantment enchantment = Bukkit.getUnsafe().get(Registry.ENCHANTMENT, key);
                    if (enchantment != null && entry.getValue() instanceof Integer) {
                        result.addUnsafeEnchantment(enchantment, (Integer)entry.getValue());
                    }
                }
            }
        } else if (args.containsKey("meta")) {
            raw = args.get("meta");
            if (raw instanceof ItemMeta) {
                ((ItemMeta)raw).setVersion(version);
                result.setItemMeta((ItemMeta)raw);
            }
        }

        if (version < 0 && args.containsKey("damage")) {
            result.setDurability(damage);
        }

        return result;
    }

    /**
     * Deserializes a string from the database back into an NBT string.
     * Handles both the new Base64 format (prefixed with "B64:") and the old custom format.
     * @param encoded The string retrieved from the database.
     * @return The deserialized NBT string.
     */
    public String deserializeString(String encoded) {
        if (encoded.startsWith("B64:")) {
            String base64 = encoded.substring(4);
            try {
                return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                // fallback to legacy decoding below
            }
        }
        // Legacy fallback using custom replacement
        return encoded.replace("|", ",")
                .replace("^", "\"")
                .replace("<", "{")
                .replace(">", "}")
                .replace("~", "'");
    }

    /**
     * Serializes an NBT string for database storage.
     * Uses Base64 encoding by default (prefixed with "B64:").
     * If USE_LEGACY_SERIALIZATION config is true, uses the old custom replacement format.
     * @param object The NBT string to serialize.
     * @return The serialized string.
     */
    public String serialize(String object) {
        // Base64 encode with a "B64:" marker for new data
        return "B64:" + Base64.getEncoder().encodeToString(object.getBytes(StandardCharsets.UTF_8));
    }

    public void doPlayerLogout(PlayerQuitEvent event) throws SQLException, IOException {
        String player_uuid = event.getPlayer().getUniqueId().toString();
        JDBCsetUp.executeUpdate("UPDATE player_data SET online= '0' WHERE uuid='" + player_uuid + "'");
        store(event.getPlayer(), false);
    }

    @EventHandler
    public void onPlayerLogout(PlayerQuitEvent event) {
        executorService.submit(() -> {
            try {
                doPlayerLogout(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // Helper function to get the NBT string to be saved
    // If item is a placeholder, get original NBT; otherwise, get current NBT
    private String getNbtForStorage(ItemStack itemStack) {
        // It's a normal item or empty, serialize its current NBT
        return serialize(serializeNBT(itemStack).toString());
    }

    public Map<String, Object> serializeNBT(ItemStack itemStack) {
        if(itemStack==null){
            return new HashMap<>();
        }
        return serialize(itemStack);
    }

    @NotNull
    public Map<String, Object> serialize(ItemStack stack) {
        Map<String, Object> result = new LinkedHashMap();
        result.put("v", Bukkit.getUnsafe().getDataVersion());
        result.put("type", stack.getType().name());
        if (stack.getAmount() != 1) {
            result.put("amount", stack.getAmount());
        }

        ItemMeta meta = stack.getItemMeta();
        if (!Bukkit.getItemFactory().equals(meta, null)) {
            result.put("meta", meta);
        }

        return result;
    }

    public void store(Player player, boolean init) throws SQLException, IOException {
        String player_uuid = player.getUniqueId().toString();

        // Basic Attributes
        float XP = player.getTotalExperience();
        int food_level = player.getFoodLevel();
        int health = (int) player.getHealth();
        // Left Hand
        String left_hand = getNbtForStorage(player.getInventory().getItemInOffHand());

        // Cursor
        String cursors = getNbtForStorage(player.getItemOnCursor());

        // Equipment (Armor)
        Map<Integer, String> equipment = new HashMap<>();
        for (int i = 0; i < player.getInventory().getArmorContents().length; i++) {
            ItemStack itemStack = player.getInventory().getArmorContents()[i];
            equipment.put(i, getNbtForStorage(itemStack));
        }
        // Inventory
        Inventory inventory = player.getInventory();
        Map<Integer, String> inventoryMap = new HashMap<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventoryMap.put(i, getNbtForStorage(inventory.getItem(i)));
        }
        // Ender Chest
        Map<Integer, String> ender_chest = new HashMap<>();
        for (int i = 0; i < player.getEnderChest().getSize(); i++) {
            ender_chest.put(i, getNbtForStorage(player.getEnderChest().getItem(i)));
        }

        // Effects
        Collection<PotionEffect> effects = player.getActivePotionEffects();
        Map<Integer, String> effectMap = new HashMap<>();
        for (PotionEffect entry : effects) {
            effectMap.put(entry.getType().getId(), entry.getDuration() +"|"+entry.getAmplifier());
        }

        // Advancements
        File advancements = null;
        byte[] advancementBytes = new byte[0];
        String json = new String(advancementBytes, StandardCharsets.UTF_8);

        int score = 0;
        // SQL Operation for player data
        if (init) {
            JDBCsetUp.executeUpdate("INSERT INTO player_data (uuid,armor,inventory,enderchest,advancements,effects,xp,food_level,health,score,left_hand,cursors,online) VALUES ('" + player_uuid + "','" + equipment + "','" + inventoryMap + "','" + ender_chest + "','" + advancements + "','" + effectMap + "','" + XP + "','" + food_level + "','" + health + "','" + score + "','" + left_hand + "','" + cursors + "',online=true)");
        } else {
            JDBCsetUp.executeUpdate("UPDATE player_data SET inventory = '" + inventoryMap + "',armor='" + equipment + "' ,xp='" + XP + "',effects='" + effectMap + "',enderchest='" + ender_chest + "',score='" + score + "',food_level='" + food_level + "',health='" + health + "',advancements='" + json + "',left_hand='" + left_hand + "',cursors='" + cursors + "' WHERE uuid = '" + player_uuid + "'");
        }
    }


    static int tick = 0;



    // New fields for auto-save

    //AutoSave
    public void onServerTick() {
            // Retrieve the current server instance
            Server server = playersync.instance.getServer();
            // Iterate through all online players
            for (Player player : server.getOnlinePlayers()) {
                executorService.submit(() -> {
                        // Call the same store method used in logout and file save events.
                    try {
                        store(player, false);
                    } catch (SQLException | IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
    }

    private static void setXpForPlayer(Player serverPlayer, int databaseXp) {
        serverPlayer.setTotalExperience(0);
        serverPlayer.setExp(0);
        serverPlayer.setLevel(0);
        serverPlayer.giveExp(databaseXp);
    }
}
