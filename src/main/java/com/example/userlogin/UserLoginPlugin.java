package com.example.userlogin;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Plugin(id = "userlogin", name = "UserLogin", version = "1.0.0")
public class UserLoginPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final CommandManager commandManager;

    // Speichert den eingeloggten Benutzernamen pro Spieler-UUID
    private final Map<UUID, String> loggedInUsers = new HashMap<>();

    @Inject
    public UserLoginPlugin(ProxyServer server, Logger logger, CommandManager commandManager) {
        this.server = server;
        this.logger = logger;
        this.commandManager = commandManager;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        CommandMeta meta = commandManager.metaBuilder("user")
                .plugin(this)
                .build();

        commandManager.register(meta, new UserCommand());
        logger.info("UserLogin-Plugin wurde aktiviert!");
    }

    private class UserCommand implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            var source = invocation.source();
            String[] args = invocation.arguments();

            if (!(source instanceof Player player)) {
                source.sendMessage(Component.text("Dieser Befehl ist nur für Spieler.", NamedTextColor.RED));
                return;
            }

            if (args.length == 0) {
                player.sendMessage(Component.text("Verwendung: /user <Benutzername> [info|logout|menu]", NamedTextColor.YELLOW));
                return;
            }

            String username = args[0];
            UUID uuid = player.getUniqueId();

            if (args.length == 1) {
                loggedInUsers.put(uuid, username);
                player.sendMessage(Component.text("Du bist jetzt als '" + username + "' eingeloggt.", NamedTextColor.GREEN));
                return;
            }

            String sub = args[1].toLowerCase();

            if (!loggedInUsers.containsKey(uuid) || !loggedInUsers.get(uuid).equals(username)) {
                player.sendMessage(Component.text("Du bist nicht als '" + username + "' eingeloggt. Nutze zuerst /user " + username, NamedTextColor.RED));
                return;
            }

            switch (sub) {
                case "info" -> {
                    player.sendMessage(Component.text("=== Account-Info ===", NamedTextColor.GOLD));
                    player.sendMessage(Component.text("Benutzername: " + username, NamedTextColor.AQUA));
                    player.sendMessage(Component.text("UUID: " + uuid, NamedTextColor.GRAY));
                }
                case "logout" -> {
                    loggedInUsers.remove(uuid);
                    player.sendMessage(Component.text("Du wurdest als '" + username + "' ausgeloggt.", NamedTextColor.YELLOW));
                }
                case "menu" -> {
                    player.sendMessage(Component.text("=== Benutzer-Menü ===", NamedTextColor.GOLD));
                    player.sendMessage(Component.text("/user " + username + " info   – Account-Details", NamedTextColor.GRAY));
                    player.sendMessage(Component.text("/user " + username + " logout – Ausloggen", NamedTextColor.GRAY));
                }
                default -> player.sendMessage(Component.text("Unbekannter Unterbefehl. Nutze info, logout oder menu.", NamedTextColor.RED));
            }
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return true;
        }
    }
                                                  }
