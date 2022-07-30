package com.coocoofroggy.utils;

import com.coocoofroggy.Main;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import xmlwise.Plist;
import xmlwise.XmlParseException;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Listeners extends ListenerAdapter {

    final HashMap<String, InteractionHook> messageAndHook = new HashMap<>();
    final HashMap<String, String> messageAndOwner = new HashMap<>();
    final HashMap<String, HashMap<String, InputStream>> userAndInputStreams = new HashMap<>();
    final HashMap<String, HashMap<String, String>> userAndTss = new HashMap<>();

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        switch (event.getName()) {
            case "verifyblob" -> {
                if (event.getChannel() instanceof GuildChannel) {
                    // If we don't have perms to send message in this channel
                    if (!PermissionUtil.checkPermission(
                            event.getGuildChannel().getPermissionContainer(),
                            Objects.requireNonNull(event.getGuild()).getSelfMember(), // Guild is never null, we are in a GuildChannel
                            Permission.MESSAGE_SEND)) {
                        event.reply("I don't have permission to send messages in this channel! Try again in another channel.").setEphemeral(true).queue();
                    }
                }

                Message.Attachment attachment = Objects.requireNonNull(event.getOption("blob")).getAsAttachment(); // Required arg

//                InteractionHook hook = event.reply("Reply to this message with your blob file.").complete();
//                Message sentMessage = hook.retrieveOriginal().complete();
//                setMessageHook(sentMessage.getId(), hook);
//                setMessageOwner(sentMessage.getId(), event.getUser().getId());

                InputStream blobInputStream;
                try {
                    blobInputStream = attachment.getProxy().download().get();
                } catch (InterruptedException | ExecutionException e) {
                    event.reply("Unable to download blob from your attachment. Please try again.").complete();
                    return;
                }

                HashMap<String, InputStream> inputStreams = new HashMap<>();
                inputStreams.put("blob", blobInputStream);
                userAndInputStreams.put(userId, inputStreams);

                InteractionHook hook = event.reply("Reply to this message with a BuildManifest or a firmware link to verify the blob against.").complete();
                Message sentMessage = hook.retrieveOriginal().complete();
                setMessageHook(sentMessage.getId(), hook);
                setMessageOwner(sentMessage.getId(), event.getUser().getId());
            }
            case "blobinfo" -> {
                Message.Attachment attachment = Objects.requireNonNull(event.getOption("blob")).getAsAttachment(); // Required arg
                InteractionHook hook = event.deferReply().complete();

                File blobFile = new File("files/" + userId + ".shsh2");
                try {
                    attachment.getProxy().downloadToFile(blobFile).get();
                } catch (InterruptedException | ExecutionException e) {
                    hook.editOriginal("Unable to save blob file. Please try again.").queue();
                    throw new RuntimeException(e);
                }

                String result;
                try {
                    result = CliUtils.img4toolInfo(blobFile);
                } catch (IOException e) {
                    e.printStackTrace();
                    hook.editOriginal("Failed to run img4tool. Stack trace:\n" +
                            "```\n" +
                            Arrays.toString(e.getStackTrace()) +
                            "\n" +
                            "```").queue();
                    throw new RuntimeException(e);
                }
                EmbedBuilder eb = new EmbedBuilder();
                eb.setFooter(event.getUser().getName(), event.getUser().getAvatarUrl());
                if (result.contains("img4tool: failed with exception:")) {
                    String log = makeLogDiscordFriendly(result);
                    eb.setColor(new Color(16753152))
                            .setDescription(log);
                } else {
                    eb.setTitle("SHSH Info")
                            .setColor(new Color(708352));

                }
                System.out.println(result);
                Pattern pattern = Pattern.compile("(?:.*: )?(.*): (.*)");
                Matcher matcher = pattern.matcher(result);
                int line = -1;
                while (matcher.find()) {
                    line++;
                    // Skip the first 2 lines (img4tool info)
                    if (line < 2)
                        continue;
                    // Group 1 = Name of field
                    String fieldName = matcher.group(1);
                    // Group 2 = Field value
                    String value = matcher.group(2);

                    switch (fieldName.toUpperCase()) {
                        case "BNCH" -> fieldName = "AP Nonce (BNCH)";
                        case "SNON" -> fieldName = "SEP Nonce (SNON)";
                        case "ECID" -> value = value.substring(0, 4) + "X".repeat(value.length() - 4); // Hide ECID
                    }
                    eb.addField(fieldName + ":", "`" + value + "`", true);
                }
                // Generator
                String generator;
                try {
                    String contents = FileUtils.readFileToString(blobFile, StandardCharsets.UTF_8);
                    Map<String, Object> plist = Plist.fromXml(contents);
                    generator = (String) plist.get("generator");
                } catch (IOException | XmlParseException e) {
                    hook.editOriginal("Unable to read blob file. Please try again.").queue();
                    throw new RuntimeException(e);
                }
                if (generator != null) {
                    eb.addField("Generator", "`" + generator + "`", true);
                }

                // Exceptions
                pattern = Pattern.compile("(?<=\\[exception]:\\nwhat=).*");
                matcher = pattern.matcher(result);
                while (matcher.find()) {
                    eb.addField("Exception:", matcher.group(0), true);
                }

                hook.editOriginalEmbeds(eb.build()).queue();
            }
            case "bm" -> {
                String url = Objects.requireNonNull(event.getOption("url")).getAsString();
                InteractionHook hook = event.deferReply().complete();
                InputStream bmInputStream;
                try {
                    bmInputStream = RemoteUtils.fetchBuildManifestFromUrl(url, userId);
                    if (bmInputStream == null) {
                        hook.sendMessage("No BuildManifest found. Check your URL and try again.").queue();
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    hook.sendMessage("Unable to download BuildManifest from URL. Check your URL and try again.").queue();
                    return;
                }
                hook.sendFile(bmInputStream, "BuildManifest.plist").queue();
            }
            case "tss" -> {
                if (event.getChannel() instanceof GuildChannel) {
                    // If we don't have perms to send message in this channel
                    if (!PermissionUtil.checkPermission(
                            event.getGuildChannel().getPermissionContainer(),
                            Objects.requireNonNull(event.getGuild()).getSelfMember(), // Guild is never null, we are in a GuildChannel
                            Permission.MESSAGE_SEND)) {
                        event.reply("I don't have permission to send messages in this channel! Try again in another channel.").setEphemeral(true).queue();
                    }
                }

                // Required arg so always not null
                String deviceIdentifier = Objects.requireNonNull(event.getOption("device")).getAsString();

                HashMap<String, String> tssData = new HashMap<>();
                tssData.put("device", deviceIdentifier);
                userAndTss.put(userId, tssData);

                InteractionHook hook = event
                        .reply("Reply to this message with a BuildManifest, link to firmware, or iOS version/build.")
                        .complete();
                Message sentMessage = hook.retrieveOriginal().complete();
                setMessageHook(sentMessage.getId(), hook);
                setMessageOwner(sentMessage.getId(), userId);

            }
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        User user = event.getUser();
        String buttonId = Objects.requireNonNull(event.getButton()).getId(); // Button click event... button can never be null
        assert buttonId != null; // Again, button cannot be null
        if (buttonId.equals("vb_verify")) {
            // If the user who pressed the button isn't the same as the owner of this message, say no
            if (isNotMenuOwner(event, user))
                return;
            // Get rid of verify button
            Message runningMessage = event.getMessage().editMessage("Running img4tool...").setActionRows().complete();

            InputStream blob = userAndInputStreams.get(user.getId()).get("blob");
            InputStream bm = userAndInputStreams.get(user.getId()).get("bm");

            String result;
            try {
                File blobFile = new File(user.getId() + "_blob");
                FileUtils.copyInputStreamToFile(blob, blobFile);
                File bmFile = new File(user.getId() + "_bm");
                FileUtils.copyInputStreamToFile(bm, bmFile);
                result = CliUtils.img4toolVerify(blobFile, bmFile);
            } catch (IOException e) {
                e.printStackTrace();
                runningMessage.editMessage("Failed to run img4tool. Stack trace:\n" +
                        "```\n" +
                        Arrays.toString(e.getStackTrace()) +
                        "\n" +
                        "```").queue();
                throw new RuntimeException(e);
            }
            EmbedBuilder eb = new EmbedBuilder();
            eb.setFooter(user.getName(), user.getAvatarUrl());
            if (result.contains("img4tool: failed with exception:")) {
                String log = makeLogDiscordFriendly(result);
                eb.setColor(new Color(16753152))
                        .setDescription(log);
            } else if (result.contains("[IMG4TOOL] APTicket is GOOD!")) {
                eb.setTitle("APTicket is GOOD")
                        .setColor(new Color(708352));
            } else if (result.contains("[IMG4TOOL] APTicket is BAD!")) {
                eb.setTitle("APTicket is BAD")
                        .setColor(new Color(16711680));
            }

            // Variant : Customer Erase Install (IPSW)
            // DeviceClass : n112ap
            // etc.
            System.out.println(result);
            Pattern pattern = Pattern.compile("(.*) : (.*)");
            Matcher matcher = pattern.matcher(result);
            while (matcher.find()) {
                eb.addField(matcher.group(1), matcher.group(2), true);
            }
            pattern = Pattern.compile("(?<=\\[exception]:\\nwhat=).*");
            matcher = pattern.matcher(result);
            while (matcher.find()) {
                eb.addField("Exception", matcher.group(0), true);
            }

            runningMessage.editMessageEmbeds(eb.build()).override(true).queue();
        } else if (buttonId.equals("tss_check")) {
            // If the user who pressed the button isn't the same as the owner of this message, say no
            if (isNotMenuOwner(event, user))
                return;
            /*// Get rid of Check TSS button
            event.getMessage().delete().queue();*/

            // Start "thinking"
            Message runningMessage = event.getMessage().editMessage("Running tsschecker...").setActionRows().complete();

            HashMap<String, String> tss = userAndTss.get(user.getId());

            ArrayList<String> args = new ArrayList<>();
            args.add("tsschecker");

            String device = tss.get("device");
            args.add("--device");
            args.add(device);

            String bm = tss.get("bm");
            if (bm != null) {
                args.add("--build-manifest");
                args.add(bm);
            }

            String version = tss.get("version");
            if (version != null) {
                args.add("--ios");
                args.add(version);
            }

            String build = tss.get("build");
            if (build != null) {
                args.add("--buildid");
                args.add(build);
            }

            try {
                String result = CliUtils.tssChecker(args);
                String log = makeLogDiscordFriendly(result);

                EmbedBuilder eb = new EmbedBuilder();
                eb.setFooter(user.getName(), user.getAvatarUrl());
                eb.setDescription(log);
                if (result.contains("checking tss status failed!")) {
                    eb.setColor(new Color(16753152));
                } else if (result.contains("IS being signed!")) {
                    // Fancy parsing, no terminal output
                    eb.setTitle("Signed");
                    eb.setDescription("");
                    Pattern versionPattern = Pattern.compile("(.*?) (.*?) for device (.*)(?= IS being signed!)");
                    Matcher versionMatcher = versionPattern.matcher(result);
                    if (versionMatcher.find()) {
                        String vPrefix = "Version";
                        if (versionMatcher.group(1).equals("Build"))
                            vPrefix = "Build";
                        // Version: 14.7 OR Build: 18G68
                        eb.addField(vPrefix, versionMatcher.group(2), true);
                        eb.addField("Device", device, true);
                    }
                    eb.setColor(new Color(708352));
                } else if (result.contains("IS NOT being signed!")) {
                    // Fancy parsing, no terminal output
                    eb.setTitle("Unsigned");
                    eb.setDescription("");
                    Pattern versionPattern = Pattern.compile("(.*?) (.*?) for device (.*)(?= IS NOT being signed!)");
                    Matcher versionMatcher = versionPattern.matcher(result);
                    if (versionMatcher.find()) {
                        String vPrefix = "Version";
                        if (versionMatcher.group(1).equals("Build"))
                            vPrefix = "Build";
                        // Version: 14.7 OR Build: 18G68
                        eb.addField(vPrefix, versionMatcher.group(2), true);
                        eb.addField("Device", device, true);
                    }
                    eb.setColor(new Color(16711680));
                }

                runningMessage.editMessageEmbeds(eb.build()).override(true).complete();
            } catch (IOException e) {
                e.printStackTrace();
                runningMessage.editMessage("Failed to run tsschecker. Stack trace:\n" +
                        "```\n" +
                        Arrays.toString(e.getStackTrace()) +
                        "\n" +
                        "```").queue();
            }
        }
    }

    @NotNull
    private String makeLogDiscordFriendly(String result) {
        // Remove all conflicting markdown code block things
        String foldedResult = result.replaceAll("`", "");
        // Keep it under Discord's character limits
        int amountToSubstring = 0;
        ArrayList<String> firstLines = new ArrayList<>();
        if (foldedResult.length() > 1500) {
            String[] allLines = foldedResult.split("\n");
            int i = 0;
            for (String line : allLines) {
                firstLines.add(line);
                i++;
                // Only first 5 lines
                if (i >= 5)
                    break;
            }
            firstLines.add("...\n");
            amountToSubstring = foldedResult.length() - 1500;
        }

        return "```" +
                StringUtils.join(firstLines, "\n") +
                foldedResult.substring(amountToSubstring) +
                "```";
    }

    private boolean isNotMenuOwner(@NotNull ButtonInteractionEvent event, User user) {
        if (messageAndOwner.get(event.getMessageId()) == null) {
            event.reply("Something went wrong—I forgot who summoned this menu! Please run the command again.").setEphemeral(true).queue();
            return true;
        } else if (!messageAndOwner.get(event.getMessageId()).equals(user.getId())) {
            event.reply("This is not your menu! Start your own with a slash command.").setEphemeral(true).queue();
            return true;
        }
        return false;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot())
            return;

        Message message = event.getMessage();
        List<Message.Attachment> attachments = message.getAttachments();
        Message referencedMessage = message.getReferencedMessage();

        // If it's not a reply, we don't care
        if (referencedMessage == null)
            return;

        // If their not replying to our bot, we don't care
        if (!referencedMessage.getAuthor().getId().equals(Main.jda.getSelfUser().getId()))
            return;

        switch (referencedMessage.getContentRaw()) {
            case "Reply to this message with a BuildManifest or a firmware link to verify the blob against." -> {
                String ownerId = messageAndOwner.get(referencedMessage.getId());
                if (isUserNotOwner(ownerId, message, event.getAuthor()))
                    return;

                InteractionHook hook = messageAndHook.get(referencedMessage.getId());

                String content = message.getContentRaw();
                Pattern linkPattern = Pattern.compile("https?://.*?(?=\\s|\\n|$)");
                Matcher linkMatcher = linkPattern.matcher(content);

                InputStream bmInputStream;
                if (linkMatcher.find()) {
                    String link = linkMatcher.group(0);
                    Message downloadingBmMessage = hook.sendMessage("Downloading BuildManifest...").complete();
                    try {
                        bmInputStream = RemoteUtils.fetchBuildManifestFromUrl(link, ownerId);
                        if (bmInputStream == null) {
                            downloadingBmMessage.editMessage("No BuildManifest found. Check your URL and try again.").queue();
                            downloadingBmMessage.delete().queueAfter(5, TimeUnit.SECONDS);
                            message.delete().queueAfter(5, TimeUnit.SECONDS);
                            return;
                        }
                        downloadingBmMessage.delete().queue();
                    } catch (Exception e) {
                        downloadingBmMessage.editMessage("Unable to download BuildManifest from the URL provided. Try again.").queue();
                        downloadingBmMessage.delete().queueAfter(5, TimeUnit.SECONDS);
                        message.delete().queueAfter(5, TimeUnit.SECONDS);
                        return;
                    }
                } else if (!attachments.isEmpty()) {
                    try {
                        bmInputStream = attachments.get(0).getProxy().download().get();
                    } catch (InterruptedException | ExecutionException e) {
                        Message sentMessage = hook.sendMessage("Unable to download BuildManifest from your attachment. Please try again.").complete();
                        message.delete().queueAfter(5, TimeUnit.SECONDS);
                        sentMessage.delete().queueAfter(5, TimeUnit.SECONDS);
                        return;
                    }
                } else {
                    Message sentMessage = hook.sendMessage("No BuildManifest or valid link provided! Please try again.").complete();
                    message.delete().queueAfter(5, TimeUnit.SECONDS);
                    sentMessage.delete().queueAfter(5, TimeUnit.SECONDS);
                    return;
                }
                HashMap<String, InputStream> inputStreams = userAndInputStreams.get(ownerId);
                inputStreams.put("bm", bmInputStream);
                userAndInputStreams.put(ownerId, inputStreams);

                // Delete if we can
                if (event.getGuild().getSelfMember().hasPermission(Permission.MESSAGE_MANAGE)) {
                    event.getMessage().delete().queue();
                }
                Message sentMessage = hook.editOriginal("All set—press the button to verify.").setActionRow(
                        Button.success("vb_verify", "Verify")
                ).complete();
                setMessageOwner(sentMessage.getId(), ownerId);
                setMessageHook(sentMessage.getId(), hook);
            }
            case "Reply to this message with a BuildManifest, link to firmware, or iOS version/build." -> {
                String ownerId = messageAndOwner.get(referencedMessage.getId());
                if (isUserNotOwner(ownerId, message, event.getAuthor()))
                    return;

                InteractionHook hook = messageAndHook.get(referencedMessage.getId());

                // Check for link, then attachment, then iOS version, then build
                String content = message.getContentRaw();
                Pattern linkPattern = Pattern.compile("https?://.*?(?=\\s|\\n|$)");
                Matcher linkMatcher = linkPattern.matcher(content);

                Pattern versionPattern = Pattern.compile("(?<=^)((\\d+\\.?)+)(?=\\s|\\n|$)");
                Matcher versionMatcher = versionPattern.matcher(content);

                Pattern buildPattern = Pattern.compile("(?<=^)((\\d+|[A-Za-z]+)+)(?=\\s|\\n|$)");
                Matcher buildMatcher = buildPattern.matcher(content);

                InputStream bmInputStream = null;
                String version = null;
                String build = null;
                if (linkMatcher.find()) {
                    String link = linkMatcher.group(0);
                    Message downloadingBmMessage = hook.sendMessage("Downloading BuildManifest...").complete();
                    try {
                        bmInputStream = RemoteUtils.fetchBuildManifestFromUrl(link, ownerId);
                        if (bmInputStream == null) {
                            downloadingBmMessage.editMessage("No BuildManifest found. Check your URL and try again.").queue();
                            downloadingBmMessage.delete().queueAfter(5, TimeUnit.SECONDS);
                            return;
                        }
                        downloadingBmMessage.delete().queue();
                    } catch (Exception e) {
                        downloadingBmMessage.editMessage("Unable to download BuildManifest from the URL provided.").queue();
                        downloadingBmMessage.delete().queueAfter(5, TimeUnit.SECONDS);
                        return;
                    }
                } else if (!attachments.isEmpty()) {
                    try {
                        bmInputStream = attachments.get(0).getProxy().download().get();
                    } catch (InterruptedException | ExecutionException e) {
                        hook.sendMessage("Unable to download BuildManifest from your attachment. Please try again.").complete();
                    }
                } else if (versionMatcher.find()) {
                    version = versionMatcher.group(1);
                } else if (buildMatcher.find()) {
                    build = buildMatcher.group(1);
                } else {
                    Message sentMessage = hook.sendMessage("No BuildManifest, valid link, iOS version, or iOS build provided! Please try again.").complete();
                    message.delete().queueAfter(5, TimeUnit.SECONDS);
                    sentMessage.delete().queueAfter(5, TimeUnit.SECONDS);
                    return;
                }

                HashMap<String, String> tss = userAndTss.get(event.getAuthor().getId());
                if (bmInputStream != null) {
                    File bmFile = new File(event.getAuthor().getId() + "_tss_bm");
                    try {
                        FileUtils.copyInputStreamToFile(bmInputStream, bmFile);
                    } catch (IOException e) {
                        hook.sendMessage("Unable to load your BuildManifest. Please try again.").queue();
                        return;
                    }
                    tss.put("bm", bmFile.getAbsolutePath());
                }
                if (version != null)
                    tss.put("version", version);
                if (build != null)
                    tss.put("build", build);

                userAndTss.put(event.getAuthor().getId(), tss);

                // Delete if we can
                if (event.getGuild().getSelfMember().hasPermission(Permission.MESSAGE_MANAGE)) {
                    event.getMessage().delete().queue();
                }
                Message sentMessage = hook.editOriginal("All set—press the button to check signing status.").setActionRow(
                        Button.success("tss_check", "Check TSS")
                ).complete();
                setMessageOwner(sentMessage.getId(), ownerId);
                setMessageHook(sentMessage.getId(), hook);

            }
        }
    }

    public void setMessageOwner(String messageId, String userId) {
        messageAndOwner.put(messageId, userId);
    }

    public void setMessageHook(String messageId, InteractionHook hook) {
        messageAndHook.put(messageId, hook);
    }

    public boolean isUserNotOwner(String ownerId, Message message, User author) {
        // No owner for this message where there should be an owner
        if (ownerId == null) {
            message.reply("Something went wrong—I forgot who summoned me! Please run the command again.").queue();
            return true;
        }
        // If they're not the owner, return true and ignore them
        return !ownerId.equals(author.getId());
    }
}
