package me.keano.azurite.modules.masks;

import me.keano.azurite.modules.framework.Module;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Copyright (c) 2025. Keano
 * Use or redistribution of source or file is
 * only permitted if given explicit permission.
 */
public class MaskEquipListener extends Module<MaskManager> {

    private static String CB_PACKAGE;
    private static String NMS_PACKAGE;

    private static Class<?> craftItemStackClass;
    private static Class<?> nmsItemStackClass;
    private static Class<?> packetClass;
    private static Class<?> craftPlayerClass;
    private static Class<?> entityPlayerClass;
    private static Class<?> playerConnectionClass;

    private static Method asNMSCopy;
    private static Method sendPacket;
    private static Method getHandle;

    private static Constructor<?> packetConstructor;

    static {
        try {
            CB_PACKAGE = Bukkit.getServer().getClass().getPackage().getName();
            NMS_PACKAGE = CB_PACKAGE.replace("org.bukkit.craftbukkit", "net.minecraft.server");

            craftItemStackClass = Class.forName(CB_PACKAGE + ".inventory.CraftItemStack");
            nmsItemStackClass = Class.forName(NMS_PACKAGE + ".ItemStack");
            packetClass = Class.forName(NMS_PACKAGE + ".PacketPlayOutEntityEquipment");
            craftPlayerClass = Class.forName(CB_PACKAGE + ".entity.CraftPlayer");
            entityPlayerClass = Class.forName(NMS_PACKAGE + ".EntityPlayer");
            playerConnectionClass = Class.forName(NMS_PACKAGE + ".PlayerConnection");

            asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            sendPacket = playerConnectionClass.getMethod("sendPacket", Class.forName(NMS_PACKAGE + ".Packet"));
            getHandle = craftPlayerClass.getMethod("getHandle");

            packetConstructor = packetClass.getConstructor(int.class, int.class, nmsItemStackClass);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final Map<UUID, String> activeVisualMasks = new HashMap<>();

    public MaskEquipListener(MaskManager manager) {
        super(manager);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();

        Bukkit.getScheduler().runTaskLater(getInstance(), () -> {
            checkAndUpdateVisual(player);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();

        Bukkit.getScheduler().runTaskLater(getInstance(), () -> {
            String savedMask = getManager().getEquippedMask(player.getUniqueId());
            ItemStack helmet = player.getInventory().getHelmet();

            if (savedMask != null && helmet != null && helmet.getType() != Material.AIR) {
                String helmetMaskId = getManager().getMaskIdFromItem(helmet);
                if (helmetMaskId != null && helmetMaskId.equalsIgnoreCase(savedMask)) {
                    sendMaskVisual(player, savedMask);
                }
            } else if (savedMask != null) {
                if (helmet == null || helmet.getType() == Material.AIR ||
                        getManager().getMaskIdFromItem(helmet) == null) {
                    getManager().clearEquippedMask(player.getUniqueId());
                }
            }

            checkAndUpdateVisual(player);
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        activeVisualMasks.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent e) {
        Player player = e.getPlayer();
        if (!hasActiveMask(player)) return;

        Bukkit.getScheduler().runTaskLater(getInstance(), () -> {
            checkAndUpdateVisual(player);
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemDamage(PlayerItemDamageEvent e) {
        Player player = e.getPlayer();
        if (!hasActiveMask(player)) return;

        Bukkit.getScheduler().runTaskLater(getInstance(), () -> {
            checkAndUpdateVisual(player);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        activeVisualMasks.remove(player.getUniqueId());

        Bukkit.getScheduler().runTaskLater(getInstance(), () -> {
            checkAndUpdateVisual(player);
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        activeVisualMasks.remove(e.getEntity().getUniqueId());
    }

    private boolean hasActiveMask(Player player) {
        String active = activeVisualMasks.get(player.getUniqueId());
        if (active == null) return false;
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null || helmet.getType() == Material.AIR) return false;
        String maskId = getManager().getMaskIdFromItem(helmet);
        return maskId != null && maskId.equalsIgnoreCase(active);
    }

    public void checkAndUpdateVisual(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();

        if (helmet == null || helmet.getType() == Material.AIR) {
            String activeMask = activeVisualMasks.get(player.getUniqueId());
            if (activeMask != null) {
                activeVisualMasks.remove(player.getUniqueId());
                getManager().clearEquippedMask(player.getUniqueId());
            }
            return;
        }

        String maskId = getManager().getMaskIdFromItem(helmet);
        if (maskId == null) {
            String activeMask = activeVisualMasks.get(player.getUniqueId());
            if (activeMask != null) {
                sendRealHelmetVisual(player);
                activeVisualMasks.remove(player.getUniqueId());
                getManager().clearEquippedMask(player.getUniqueId());
            }
            return;
        }

        sendMaskVisual(player, maskId);
    }

    public void sendMaskVisual(Player player, String maskId) {
        MaskManager.Mask mask = getManager().getMask(maskId);
        if (mask == null) return;

        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null || helmet.getType() == Material.AIR) return;

        ItemStack fakeHelmet = buildVisualItem(mask, helmet);

        sendEquipmentPacket(player, 4, fakeHelmet);
        activeVisualMasks.put(player.getUniqueId(), maskId);
        getManager().setEquippedMask(player.getUniqueId(), maskId);
    }

    private ItemStack buildVisualItem(MaskManager.Mask mask, ItemStack helmet) {
        ItemStack visual = SkullUtils.createSkull(mask.getTexture());

        ItemMeta visualMeta = visual.getItemMeta();
        ItemMeta helmetMeta = helmet.getItemMeta();

        if (visualMeta != null && helmetMeta != null) {
            if (helmetMeta.hasDisplayName()) {
                visualMeta.setDisplayName(helmetMeta.getDisplayName());
            }
            if (helmetMeta.hasLore()) {
                List<String> lore = new ArrayList<>(helmetMeta.getLore());
                lore.add(me.keano.azurite.utils.CC.t("&8" + mask.getDisplayName()));
                visualMeta.setLore(lore);
            } else {
                List<String> lore = new ArrayList<>();
                lore.add(me.keano.azurite.utils.CC.t("&8" + mask.getDisplayName()));
                visualMeta.setLore(lore);
            }

            // Aplicamos primero el ItemMeta al nuevo ItemStack visual
            visual.setItemMeta(visualMeta);

            // Luego copiamos los encantamientos directamente al ItemStack
            if (helmet.getEnchantments() != null && !helmet.getEnchantments().isEmpty()) {
                visual.addUnsafeEnchantments(helmet.getEnchantments());
            }
        }

        return visual;
    }

    public void sendRealHelmetVisual(Player player) {
        ItemStack realHelmet = player.getInventory().getHelmet();
        if (realHelmet == null || realHelmet.getType() == Material.AIR) {
            sendEquipmentPacket(player, 4, null);
        } else {
            sendEquipmentPacket(player, 4, realHelmet);
        }
    }

    private void sendEquipmentPacket(Player player, int slot, ItemStack item) {
        try {
            Object entityPlayer = getHandle.invoke(player);
            int entityId = player.getEntityId();

            Object nmsItem = null;
            if (item != null) {
                nmsItem = asNMSCopy.invoke(null, item);
            }

            Object packet = packetConstructor.newInstance(entityId, slot, nmsItem);

            Object connection = entityPlayerClass.getField("playerConnection").get(entityPlayer);
            sendPacket.invoke(connection, packet);

            for (Player nearby : Bukkit.getOnlinePlayers()) {
                if (nearby.equals(player)) continue;
                if (!nearby.getWorld().equals(player.getWorld())) continue;
                if (!nearby.canSee(player)) continue;

                Object nearbyHandle = getHandle.invoke(nearby);
                Object nearbyConn = entityPlayerClass.getField("playerConnection").get(nearbyHandle);
                sendPacket.invoke(nearbyConn, packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}