package net.draycia.carbon.common.commands;

import com.intellectualsites.commands.CommandManager;
import com.intellectualsites.commands.context.CommandContext;
import net.draycia.carbon.api.CarbonChat;
import net.draycia.carbon.api.CarbonChatProvider;
import net.draycia.carbon.api.channels.ChatChannel;
import net.draycia.carbon.api.users.CarbonUser;
import net.draycia.carbon.api.commands.settings.CommandSettings;
import net.draycia.carbon.api.users.PlayerUser;
import net.draycia.carbon.api.users.UserChannelSettings;
import net.draycia.carbon.common.commands.arguments.ChannelArgument;
import net.draycia.carbon.common.utils.CommandUtils;
import org.checkerframework.checker.nullness.qual.NonNull;

public class ToggleCommand {

  private @NonNull final CarbonChat carbonChat;

  public ToggleCommand(@NonNull final CommandManager<CarbonUser> commandManager) {
    this.carbonChat = CarbonChatProvider.carbonChat();

    final CommandSettings commandSettings = this.carbonChat.commandSettings().get("toggle");

    if (!commandSettings.enabled()) {
      return;
    }

    commandManager.command(
      commandManager.commandBuilder(commandSettings.name(), commandSettings.aliases(),
        commandManager.createDefaultCommandMeta())
        .withSenderType(CarbonUser.class) // player & console
        .withPermission("carbonchat.toggle")
        .argument(ChannelArgument.requiredChannelArgument())
        .argument(CommandUtils.optionalChatUserArgument()) // carbonchat.toggle.other
        .handler(context -> {
          if (context.get("user").isPresent()) {
            this.toggleOther(context);
          } else if (context.getSender() instanceof PlayerUser) {
            this.toggleSelf(context);
          }
        })
        .build()
    );
  }

  private void toggleSelf(@NonNull final CommandContext<CarbonUser> context) {
    final PlayerUser user = (PlayerUser) context.getSender();
    final ChatChannel channel = context.getRequired("channel");

    final String message;

    final UserChannelSettings settings = user.channelSettings(channel);

    if (!channel.ignorable()) {
      message = channel.cannotIgnoreMessage();
    } else if (settings.ignored()) {
      settings.ignoring(false);
      message = channel.toggleOffMessage();
    } else {
      settings.ignoring(true);
      message = channel.toggleOnMessage();
    }

    user.sendMessage(this.carbonChat.messageProcessor().processMessage(message,
      "color", "<color:" + channel.channelColor(user).toString() + ">", "channel", channel.name()));
  }

  private void toggleOther(@NonNull final CommandContext<CarbonUser> context) {
    final CarbonUser sender = context.getSender();
    final PlayerUser user = context.getRequired("user");
    final ChatChannel channel = context.getRequired("channel");

    final String message;
    final String otherMessage;

    final UserChannelSettings settings = user.channelSettings(channel);

    if (settings.ignored()) {
      settings.ignoring(false);
      message = channel.toggleOffMessage();
      otherMessage = channel.toggleOtherOffMessage();
    } else {
      settings.ignoring(true);
      message = channel.toggleOnMessage();
      otherMessage = channel.toggleOtherOnMessage();
    }

    user.sendMessage(this.carbonChat.messageProcessor().processMessage(message,
      "color", "<color:" + channel.channelColor(user).toString() + ">", "channel", channel.name()));

    sender.sendMessage(
      this.carbonChat.messageProcessor().processMessage(otherMessage,
        "color", "<color:" + channel.channelColor(user).toString() + ">",
        "channel", channel.name(), "player", user.name()));
  }
}
