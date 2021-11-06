package su.plo.voice.server.mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import su.plo.voice.api.DurationUnit;
import su.plo.voice.server.VoiceServer;
import su.plo.voice.server.mod.VoiceServerMod;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VoiceMute {
    private static final Pattern pattern = Pattern.compile("([0-9]*)([mhdw])?");
    private static final Pattern integerPattern = Pattern.compile("^([0-9]*)$");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vmute")
                .requires(source ->
                        CommandManager.requiresPermission(source, "voice.mute")
                )
                .then(Commands.argument("targets", GameProfileArgument.gameProfile()).suggests((commandContext, suggestionsBuilder) -> {
                    PlayerList playerList = commandContext.getSource().getServer().getPlayerList();
                    return SharedSuggestionProvider.suggest(playerList.getPlayers().stream()
                            .map((serverPlayer) -> serverPlayer.getGameProfile().getName()), suggestionsBuilder);
                })
                        .executes(ctx -> {
                            GameProfileArgument.getGameProfiles(ctx, "targets").forEach(gameProfile -> {
                                mute(
                                        ctx,
                                        VoiceServerMod.getServer().getPlayerList().getPlayer(gameProfile.getId()),
                                        null,
                                        null
                                );
                            });

                            return 1;
                        })
                        .then(Commands.argument("duration", StringArgumentType.word()).suggests((ctx, builder) -> {
                            String arg = builder.getRemaining();
                            List<String> suggests = new ArrayList<>();
                            if (arg.isEmpty()) {
                                suggests.add("permanent");
                            } else {
                                Matcher matcher = integerPattern.matcher(arg);
                                if (matcher.find()) {
                                    suggests.add(arg + "m");
                                    suggests.add(arg + "h");
                                    suggests.add(arg + "d");
                                    suggests.add(arg + "w");
                                }
                            }

                            return SharedSuggestionProvider.suggest(suggests, builder);
                        })
                                .executes(ctx -> {
                                    GameProfileArgument.getGameProfiles(ctx, "targets").forEach(gameProfile -> {
                                        mute(
                                                ctx,
                                                VoiceServerMod.getServer().getPlayerList().getPlayer(gameProfile.getId()),
                                                StringArgumentType.getString(ctx, "duration"),
                                                null
                                        );
                                    });

                                    return 1;
                                })
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            GameProfileArgument.getGameProfiles(ctx, "targets").forEach(gameProfile -> {
                                                mute(
                                                        ctx,
                                                        VoiceServerMod.getServer().getPlayerList().getPlayer(gameProfile.getId()),
                                                        StringArgumentType.getString(ctx, "duration"),
                                                        StringArgumentType.getString(ctx, "reason")
                                                );
                                            });

                                            return 1;
                                        })))));
    }

    private static void mute(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String rawDuration, String reason) {
        if (player == null) {
            ctx.getSource().sendFailure(new TextComponent(VoiceServer.getInstance().getMessagePrefix("player_not_found")));
            return;
        }

        DurationUnit durationUnit = null;
        long duration = 0;
        if (rawDuration != null) {
            if (!rawDuration.startsWith("perm")) {
                Matcher matcher = pattern.matcher(rawDuration);
                if (matcher.find()) {
                    duration = Integer.parseInt(matcher.group(1));
                    if (duration > 0) {
                        String type = matcher.group(2);
                        if (type == null) {
                            type = "";
                        }

                        switch (type) {
                            case "m":
                                durationUnit = DurationUnit.MINUTES;
                                break;
                            case "h":
                                durationUnit = DurationUnit.HOURS;
                                break;
                            case "d":
                                durationUnit = DurationUnit.DAYS;
                                break;
                            case "w":
                                durationUnit = DurationUnit.WEEKS;
                                break;
                            case "u":
                                duration = duration - System.currentTimeMillis() / 1000L;
                                durationUnit = DurationUnit.TIMESTAMP;
                                break;
                            default:
                                durationUnit = DurationUnit.SECONDS;
                                break;
                        }
                    } else {
                        durationUnit = DurationUnit.SECONDS;
                    }
                }
            }
        }

        long finalDuration = duration;
        DurationUnit finalDurationUnit = durationUnit;

        VoiceServer.getInstance().runAsync(() -> {
            VoiceServer.getAPI().mute(player.getUUID(), finalDuration, finalDurationUnit, reason, false);
            ctx.getSource().sendSuccess(
                    new TextComponent(String.format(VoiceServer.getInstance().getMessagePrefix("muted"), player.getGameProfile().getName())),
                    false
            );
        });
    }
}
