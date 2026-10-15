package com.example.userlogin;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.sql.*;
import java.util.*;

@Plugin(id = "userlogin", name = "UserLogin", version = "1.0.0")
public class UserLoginPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final CommandManager commandManager;

    // In-Memory: Spieler-UUID → gewünschter Zielname (bis zum Reconnect)
    private final Map<UUID, String> pendingSwitch = new HashMap<>();

    // SQLite-Datenbank (gemeinsam mit Paper-Plugin)
    private static final String DB_URL = "jdbc:sqlite:plugins/userlogin/accounts.db";

    @Inject
    public UserLoginPlugin(ProxyServer server, Logger logger, CommandManager commandManager) {
        this.server = server;
        this.logger = logger;
        this.commandManager = commandManager;
        initDatabase();
    }

    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS account_data (
                    username TEXT PRIMARY KEY,
                    inventory BLOB,
                    position_world TEXT,
                    position_x REAL,
                    position_y REAL,
                    position_z REAL,
                    position_yaw REAL,
                    position_pitch REAL,
                    xp INTEGER DEFAULT 0,
                    health REAL DEFAULT 20.0
                )
            """);
        } catch (SQLException e) {
            logger.error("Datenbank-Fehler", e);
        }
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        CommandMeta meta = commandManager.metaBuilder("user")
                .plugin(this)
                .build();
        commandManager.register(meta, new UserCommand());
        logger.info("UserLogin-Plugin aktiviert!");
    }

    // ═══════════════════════════════════════════════════════════
    // WICHTIG: GameProfile wird beim Login geändert!
    // ═══════════════════════════════════════════════════════════
    @Subscribe
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        // Prüfen ob dieser Spieler gerade wechseln will
        // Wir identifizieren ihn über die ursprüngliche UUID
        String originalName = event.getOriginalProfile().getName();
        UUID originalUuid = event.getOriginalProfile().getId();

        String targetName = pendingSwitch.get(originalUuid);
        if (targetName == null) return;

        // Neues GameProfile mit dem Zielnamen erstellen
        // Wichtig: neue UUID für den Zielnamen generieren (offline-mode)
        UUID targetUuid = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + targetName).getBytes()
        );

        GameProfile newProfile = new GameProfile(
                targetUuid,
                targetName,
                event.getOriginalProfile().getProperties() // Skin etc. behalten
        );

        event.setGameProfile(newProfile);
        logger.info("Spieler {} wird als {} eingeloggt", originalName, targetName);
    }

    private class UserCommand implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            if (!(invocation.source() instanceof Player player)) {
                invocation.source().sendMessage(
                    Component.text("Nur für Spieler.", NamedTextColor.RED));
                return;
            }

            String[] args = invocation.arguments();
            if (args.length == 0) {
                player.sendMessage(Component.text(
                    "Verwendung: /user <Benutzername>", NamedTextColor.YELLOW));
                return;
            }

            String targetName = args[0];

            // Berechtigung prüfen: OP oder LuckPerms-Permission
            if (!player.hasPermission("userlogin.command")
                    && !player.hasPermission("userlogin.admin")) {
                player.sendMessage(Component.text(
                    "Keine Berechtigung!", NamedTextColor.RED));
                return;
            }

            // Wechsel vormerken
            pendingSwitch.put(player.getUniqueId(), targetName);

            player.sendMessage(Component.text(
                "Du wirst als '" + targetName + "' eingeloggt. Verbinde dich neu...",
                NamedTextColor.GREEN));

            // Spieler trennen → beim Reconnect greift GameProfileRequestEvent
            player.disconnect(Component.text(
                "Bitte verbinde dich neu, um als '" + targetName + "' zu spielen.",
                NamedTextColor.YELLOW));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("userlogin.command")
                    || invocation.source().hasPermission("userlogin.admin");
        }
    }
        }
