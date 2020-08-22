package net.draycia.carbon.managers;

import co.aikar.commands.*;
import net.draycia.carbon.CarbonChat;
import net.draycia.carbon.channels.ChatChannel;
import net.draycia.carbon.commands.*;
import net.draycia.carbon.storage.ChatUser;

import java.util.ArrayList;
import java.util.List;

public class CommandManager {

    private final BukkitCommandManager commandManager;

    public CommandManager(CarbonChat carbonChat) {

        commandManager = new BukkitCommandManager(carbonChat);

        commandManager.enableUnstableAPI("help");

        commandManager.getCommandCompletions().registerCompletion("chatchannel", (context) -> {
            ChatUser user = carbonChat.getUserService().wrap(context.getPlayer());

            List<String> completions = new ArrayList<>();

            for (ChatChannel chatChannel : carbonChat.getChannelManager().getRegistry().values()) {
                if (chatChannel.canPlayerUse(user)) {
                    completions.add(chatChannel.getKey());
                }
            }

            return completions;
        });

        commandManager.getCommandCompletions().registerCompletion("channel", (context) -> {
            List<String> completions = new ArrayList<>();

            for (ChatChannel chatChannel : carbonChat.getChannelManager().getRegistry().values()) {
                    completions.add(chatChannel.getKey());
            }

            return completions;
        });

        commandManager.getCommandContexts().registerContext(ChatChannel.class, (context) -> {
            String name = context.popFirstArg();

            for (ChatChannel chatChannel : carbonChat.getChannelManager().getRegistry().values()) {
                if (chatChannel.getKey().equalsIgnoreCase(name)) {
                    return chatChannel;
                }
            }

            return null;
        });

        commandManager.getCommandContexts().registerContext(ChatUser.class, (context) -> {
            return carbonChat.getUserService().wrap(context.popFirstArg());
        });

        commandManager.getCommandConditions().addCondition(ChatChannel.class,"canuse", (context, execution, value) -> {
            if (value == null) {
                throw new ConditionFailedException(carbonChat.getLanguage().getString("cannot-use-channel"));
            }

            ChatUser user = carbonChat.getUserService().wrap(context.getIssuer().getPlayer());

            if (!value.canPlayerUse(user)) {
                throw new ConditionFailedException(value.getCannotUseMessage());
            }
        });

        commandManager.getCommandConditions().addCondition(ChatChannel.class,"exists", (context, execution, value) -> {
            if (value == null) {
                throw new ConditionFailedException(carbonChat.getLanguage().getString("cannot-use-channel"));
            }
        });

        reloadCommands();
    }

    public void reloadCommands() {
        this.commandManager.registerCommand(new ToggleCommand());
        this.commandManager.registerCommand(new ChannelCommand());
        this.commandManager.registerCommand(new IgnoreCommand());
        this.commandManager.registerCommand(new ChatReloadCommand());
        this.commandManager.registerCommand(new MeCommand());
        this.commandManager.registerCommand(new MessageCommand());
        this.commandManager.registerCommand(new NicknameCommand());
        this.commandManager.registerCommand(new ReplyCommand());
        this.commandManager.registerCommand(new SetColorCommand());
        this.commandManager.registerCommand(new SpyChannelCommand());

        this.commandManager.registerCommand(new ClearChatCommand());
        this.commandManager.registerCommand(new MuteCommand());
        this.commandManager.registerCommand(new ShadowMuteCommand());
    }

    public BukkitCommandManager getCommandManager() {
        return commandManager;
    }

}
