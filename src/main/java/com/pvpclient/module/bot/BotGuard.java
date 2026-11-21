package com.pvpclient.module.bot;

import com.pvpclient.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.Locale;

/**
 * Собственная проверка окружения для автономного бота.
 * Бот работает только в одиночной игре / LAN-мире или на сервере, чей хост указан
 * в "authorizedServers" (config/pvpclient.json). Не зависит от PrivateWorldGuard.
 */
public final class BotGuard {
    private BotGuard() {}

    public static boolean allowed(MinecraftClient mc) {
        if (mc.isIntegratedServerRunning()) return true; // singleplayer или LAN-хост
        ServerInfo info = mc.getCurrentServerEntry();
        if (info == null || info.address == null) return false;
        String host = info.address.toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) host = host.substring(0, colon); // убираем порт
        for (String a : ConfigManager.authorizedServers()) {
            if (a.equalsIgnoreCase(host)) return true;
        }
        return false;
    }
}
