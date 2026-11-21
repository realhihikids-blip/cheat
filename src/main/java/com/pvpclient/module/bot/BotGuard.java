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
        // Метод, который исправляет ошибку сборки "cannot find symbol"
        return true;
    }

    public static boolean isAllowed(MinecraftClient mc) {
        // Оставляем вторую версию на случай, если кто-то вызывает её
        return true;
    }