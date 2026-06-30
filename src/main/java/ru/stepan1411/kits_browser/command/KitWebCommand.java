package ru.stepan1411.kits_browser.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import ru.stepan1411.kits_browser.web.WebClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class KitWebCommand {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final SuggestionProvider<ServerCommandSource> KIT_NAMES_SUGGEST = (ctx, builder) -> {
        Map<String, Map<String, Object>> kitsRaw = getKitsRaw();
        if (kitsRaw != null) {
            for (String name : kitsRaw.keySet()) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> KIT_IDS_SUGGEST = (ctx, builder) -> {
        try {
            String json = WebClient.httpGet(WebClient.getBackendUrl() + "/api/kits");
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> kits = GSON.fromJson(json, java.util.List.class);
            if (kits != null) {
                for (Map<String, Object> k : kits) {
                    Object id = k.get("id");
                    if (id instanceof Number) {
                        String name = String.valueOf(k.getOrDefault("name", ""));
                        builder.suggest(String.valueOf(((Number) id).intValue()),
                            Text.literal(" #" + id + " " + name));
                    }
                }
            }
        } catch (Exception ignored) {}
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = CommandManager.literal("pvpbot");
        var kitNode = CommandManager.literal("kit")
            .then(CommandManager.literal("publish-kit")
                .executes(ctx -> {
                    ctx.getSource().sendError(Text.literal("§cUsage: /pvpbot kit publish-kit <name>"));
                    return 0;
                })
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .suggests(KIT_NAMES_SUGGEST)
                    .executes(KitWebCommand::publishKit)))
            .then(CommandManager.literal("login")
                .executes(KitWebCommand::kitLogin))
            .then(CommandManager.literal("log-out")
                .executes(KitWebCommand::kitLogout))
            .then(CommandManager.literal("stats")
                .executes(KitWebCommand::kitStats))
            .then(CommandManager.literal("download-kit")
                .executes(ctx -> {
                    ctx.getSource().sendError(Text.literal("§cUsage: /pvpbot kit download-kit <ID>"));
                    return 0;
                })
                .then(CommandManager.argument("ID", IntegerArgumentType.integer(1))
                    .executes(KitWebCommand::downloadKit)));
        root.then(kitNode);
        dispatcher.register(root);

        // web subcommand tree removed
    }

    @SuppressWarnings("unchecked")
    private static Class<?> botKitsClass() {
        try {
            return Class.forName("org.stepan1411.pvp_bot.bot.BotKits");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> getKitsRaw() {
        try {
            Class<?> cls = botKitsClass();
            if (cls == null) return null;
            Field field = cls.getDeclaredField("kitsRaw");
            field.setAccessible(true);
            return (Map<String, Map<String, Object>>) field.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void setKitsRaw(Map<String, Map<String, Object>> kitsRaw) {
        try {
            Class<?> cls = botKitsClass();
            if (cls == null) return;
            Field field = cls.getDeclaredField("kitsRaw");
            field.setAccessible(true);
            field.set(null, kitsRaw);
        } catch (Exception ignored) {}
    }

    private static void callSave() {
        try {
            Class<?> cls = botKitsClass();
            if (cls == null) return;
            Method method = cls.getDeclaredMethod("save");
            method.setAccessible(true);
            method.invoke(null);
        } catch (Exception ignored) {}
    }

    private static int publishKit(CommandContext<ServerCommandSource> ctx) {
        var source = ctx.getSource();
        String kitName = StringArgumentType.getString(ctx, "name");

        try {
            Map<String, Map<String, Object>> kitsRaw = getKitsRaw();
            if (kitsRaw == null || !kitsRaw.containsKey(kitName.toLowerCase())) {
                source.sendError(Text.literal("§c[Kits browser] Kit '" + kitName + "' not found locally"));
                return 0;
            }

            Map<String, Object> kitData = kitsRaw.get(kitName.toLowerCase());
            String json = GSON.toJson(kitData);
            String username = source.getPlayer().getName().getString();
            String server = source.getServer().getServerMotd();
            String owner = username + "|" + server;
            String result = WebClient.importKitRaw(kitName, json, owner);
            source.sendFeedback(() -> Text.literal("§a[Kits browser] Kit '" + kitName + "' published!"), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§e[Kits browser] Visit " + WebClient.getBackendUrl() + " to manage kits."), false);
        }
        return 1;
    }

    private static int kitLogin(CommandContext<ServerCommandSource> ctx) {
        var source = ctx.getSource();
        source.sendFeedback(() -> Text.literal("§6[Kits browser] §eGenerating login link..."), false);
        try {
            String username = source.getPlayer().getName().getString();
            String server = source.getServer().getServerMotd();
            String result = WebClient.createLoginLink(username, server);
            Map<String, Object> data = GSON.fromJson(result, Map.class);
            if (Boolean.TRUE.equals(data.get("ok"))) {
                String url = (String) data.get("url");
                String token = (String) data.get("token");
                if (token == null && url != null) {
                    int idx = url.indexOf("token=");
                    if (idx >= 0) token = url.substring(idx + 6);
                }
                if (token != null) WebClient.setAuthToken(token);
                Text msg = Text.literal("")
                    .append(Text.literal("§a[Kits browser] §eClick to login: "))
                    .append(Text.literal("§b§n" + url)
                        .setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url)))
                            .withHoverEvent(new HoverEvent.ShowText(Text.literal("Open in browser")))));
                source.sendFeedback(() -> msg, false);
            } else {
                String error = (String) data.getOrDefault("error", "Unknown error");
                source.sendError(Text.literal("§c[Kits browser] " + error));
            }
        } catch (Exception e) {
            source.sendError(Text.literal("§c[Kits browser] Failed to create login link: " + e.getMessage()));
        }
        return 1;
    }

    private static int kitLogout(CommandContext<ServerCommandSource> ctx) {
        var source = ctx.getSource();
        source.sendFeedback(() -> Text.literal("§6[Kits browser] §eLogging out..."), false);
        try {
            String username = source.getPlayer().getName().getString();
            String server = source.getServer().getServerMotd();
            String result = WebClient.logOut(username, server);
            source.sendFeedback(() -> Text.literal("§a[Kits browser] §fYou have been logged out of the web interface!"), false);
        } catch (Exception e) {
            source.sendError(Text.literal("§c[Kits browser] Failed to log out: " + e.getMessage()));
        }
        return 1;
    }

    private static int kitStats(CommandContext<ServerCommandSource> ctx) {
        var source = ctx.getSource();
        try {
            String username = source.getPlayer().getName().getString();
            String result = WebClient.fetchStats();
            Map<String, Object> data = GSON.fromJson(result, Map.class);
            int totalKits = ((Number) data.getOrDefault("totalKits", 0)).intValue();
            source.sendFeedback(() -> Text.literal("§6[Kits browser] §eYour username: §f" + username), false);
        } catch (Exception e) {
            source.sendError(Text.literal("§c[Kits browser] Failed to fetch stats: " + e.getMessage()));
        }
        return 1;
    }

    @SuppressWarnings("unchecked")
    private static String toNbtString(Object value) {
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var e : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                String key = e.getKey();
                if (key.contains(":")) {
                    sb.append('"').append(key).append("\":");
                } else {
                    sb.append(key).append(':');
                }
                sb.append(toNbtString(e.getValue()));
            }
            sb.append('}');
            return sb.toString();
        } else if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toNbtString(list.get(i)));
            }
            sb.append(']');
            return sb.toString();
        } else if (value instanceof Number) {
            Number n = (Number) value;
            double d = n.doubleValue();
            if (d == n.longValue()) return String.valueOf(n.longValue());
            return String.valueOf(d);
        } else if (value instanceof Boolean) {
            return value.toString();
        } else {
            return "\"" + value + "\"";
        }
    }

    private static int downloadKit(CommandContext<ServerCommandSource> ctx) {
        var source = ctx.getSource();
        MinecraftServer server = source.getServer();
        int id = IntegerArgumentType.getInteger(ctx, "ID");
        source.sendFeedback(() -> Text.literal("§6[Kits browser] §eDownloading kit #" + id + "..."), false);
        CompletableFuture.supplyAsync(() -> {
            try {
                return WebClient.downloadKitById(id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((json, ex) -> {
            server.execute(() -> {
                if (ex != null) {
                    source.sendError(Text.literal("§c[Kits browser] Failed to download kit #" + id + ": " + ex.getCause().getMessage()));
                    return;
                }
                try {
                    Map<String, Object> response = GSON.fromJson(json, Map.class);
                    String kitName = (String) response.get("name");
                    Object rawData = response.get("data");
                    if (kitName == null || rawData == null) {
                        source.sendError(Text.literal("§c[Kits browser] Invalid kit data received"));
                        return;
                    }
                    Map<String, Map<String, Object>> kitsRaw = getKitsRaw();
                    if (kitsRaw != null && rawData instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> slotMap = (Map<String, Object>) rawData;
                        Map<String, Object> nbtMap = new java.util.LinkedHashMap<>();
                        for (var e : slotMap.entrySet()) {
                            nbtMap.put(e.getKey(), toNbtString(e.getValue()));
                        }
                        kitsRaw.put(kitName.toLowerCase(), nbtMap);
                        setKitsRaw(kitsRaw);
                        callSave();
                        source.sendFeedback(() -> Text.literal("§a[Kits browser] Kit '" + kitName + "' (#" + id + ") downloaded and saved!"), false);
                    } else {
                        source.sendFeedback(() -> Text.literal("§e[Kits browser] Kit data:\n" + GSON.toJson(rawData)), false);
                    }
                } catch (Exception e) {
                    source.sendError(Text.literal("§c[Kits browser] Failed to process kit #" + id + ": " + e.getMessage()));
                }
            });
        });
        return 1;
    }

    private static int login(CommandContext<ServerCommandSource> ctx) {
        return kitLogin(ctx);
    }
}
