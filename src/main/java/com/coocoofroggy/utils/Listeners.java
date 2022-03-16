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
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Listeners extends ListenerAdapter {

    final HashMap<String, InteractionHook> messageAndHook = new HashMap<>();
    final HashMap<String, String> messageAndOwner = new HashMap<>();
    final HashMap<String, HashMap<String, File>> userAndFiles = new HashMap<>();
    final HashMap<String, HashMap<String, String>> userAndTss = new HashMap<>();

    public static File getBuildManifestFromUrl(String urlString, String userId) throws Exception {
        URL url = new URL(urlString);
        String pathToSave = "../files/" + userId + "_BuildManifest.plist";

        // Thanks to airsquared for finding this com.coocoofroggy.utils.HttpChannel
        ZipFile ipsw = new ZipFile(new HttpChannel(url), "ipsw", "UTF8", true, true);
        ZipArchiveEntry bmEntry = ipsw.getEntry("BuildManifest.plist");
        if (bmEntry == null) {
            bmEntry = ipsw.getEntry("AssetData/boot/BuildManifest.plist");
            if (bmEntry == null) {
                return null;
            }
        }

        InputStream buildManifestInputStream = ipsw.getInputStream(bmEntry);
        File buildManifest = new File(pathToSave);
        FileUtils.copyInputStreamToFile(buildManifestInputStream, buildManifest);

        return new File(pathToSave);
    }

    public static String img4toolVerify(File blob, File bm) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("img4tool", "--shsh", blob.getAbsolutePath(), "--verify", bm.getAbsolutePath());
        // Merge stderr with stdout
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        InputStream inputStream = process.getInputStream();
        return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
    }

    public static String tssChecker(ArrayList<String> args) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(args);

        // Merge stderr with stdout
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        InputStream inputStream = process.getInputStream();
        return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
    }

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

                File blobFile = new File("../files/" + userId + ".shsh2");

                attachment.downloadToFile(blobFile)
                        .thenAccept(file -> System.out.println("Saved attachment to " + file.getPath()))
                        .exceptionally(t -> { // handle failure
                            event.reply("Unable to save blob file. Please try again.").queue();
                            t.printStackTrace();
                            return null;
                        });

                HashMap<String, File> files = new HashMap<>();
                files.put("blob", blobFile);
                userAndFiles.put(userId, files);

                InteractionHook hook = event.reply("Reply to this message with a BuildManifest or a firmware link to verify the blob against.").complete();
                Message sentMessage = hook.retrieveOriginal().complete();
                setMessageHook(sentMessage.getId(), hook);
                setMessageOwner(sentMessage.getId(), event.getUser().getId());
            }
            case "bm" -> {
                String url = Objects.requireNonNull(event.getOption("url")).getAsString();
                InteractionHook hook = event.deferReply().complete();
                File bm;
                try {
                    bm = getBuildManifestFromUrl(url, userId);
                    if (bm == null) {
                        hook.sendMessage("No BuildManifest found. Check your URL and try again.").queue();
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    hook.sendMessage("Unable to download BuildManifest from URL. Check your URL and try again.").queue();
                    return;
                }
                hook.sendFile(bm).queue();
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
            event.getInteraction().editComponents().complete();
            Message runningMessage = event.getMessage().editMessage("Running img4tool...").complete();

            File blob = userAndFiles.get(user.getId()).get("blob");
            File bm = userAndFiles.get(user.getId()).get("bm");
            try {
                String result = img4toolVerify(blob, bm);
                // Remove all conflicting markdown code block things
                result = result.replaceAll("`", "");
                // Keep it under Discord's character limits
                int amountToSubstring = 0;
                ArrayList<String> firstLines = new ArrayList<>();
                if (result.length() > 1500) {
                    String[] allLines = result.split("\n");
                    int i = 0;
                    for (String line : allLines) {
                        firstLines.add(line);
                        i++;
                        // Only first 5 lines
                        if (i >= 5)
                            break;
                    }
                    firstLines.add("...\n");
                    amountToSubstring = result.length() - 1500;
                }

                String log = "```" +
                        StringUtils.join(firstLines, "\n") +
                        result.substring(amountToSubstring) +
                        "```";

                EmbedBuilder eb = new EmbedBuilder();
                eb.setFooter(user.getName(), user.getAvatarUrl());
                eb.setDescription(log);
                if (result.contains("img4tool: failed with exception:")) {
                    eb.setColor(new Color(16753152));
                } else if (result.contains("[IMG4TOOL] APTicket is GOOD!")) {
                    eb
                            .setTitle("APTicket is GOOD")
                            .setDescription("")
                            .setColor(new Color(708352));
                } else if (result.contains("[IMG4TOOL] APTicket is BAD!")) {
                    eb
                            .setTitle("APTicket is BAD")
                            .setDescription("")
                            .setColor(new Color(16711680));
                }

                // Variant : Customer Erase Install (IPSW)
                // DeviceClass : n112ap
                // etc.
                System.out.println(result);
                Pattern pattern = Pattern.compile("(.*) : (.*)");
                Matcher matcher = pattern.matcher(result);
                while (matcher.find()) {
                    eb.addField(matcher.group(1) + ":", matcher.group(2), true);
                }
                pattern = Pattern.compile("(?<=\\[exception]:\\nwhat=).*");
                matcher = pattern.matcher(result);
                while (matcher.find()) {
                    eb.addField("Exception:", matcher.group(0), true);
                }

                runningMessage.editMessageEmbeds(eb.build()).queue();
                runningMessage.editMessage(" ").queue();
            } catch (IOException e) {
                e.printStackTrace();
                runningMessage.editMessage("Failed to run img4tool. Stack trace:\n" +
                        "```\n" +
                        Arrays.toString(e.getStackTrace()) +
                        "\n" +
                        "```").queue();
            }
        } else if (buttonId.equals("tss_check")) {
            // If the user who pressed the button isn't the same as the owner of this message, say no
            if (isNotMenuOwner(event, user))
                return;
            /*// Get rid of Check TSS button
            event.getMessage().delete().queue();*/

            event.getInteraction().editComponents().complete();
            // Start "thinking"
            Message runningMessage = event.getMessage().editMessage("Running tsschecker...").complete();

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
                String result = tssChecker(args);
                // Remove all conflicting markdown code block things
                result = result.replaceAll("`", "");
                // Keep it under Discord's character limits
                int amountToSubstring = 0;
                ArrayList<String> firstLines = new ArrayList<>();
                if (result.length() > 1500) {
                    String[] allLines = result.split("\n");
                    int i = 0;
                    for (String line : allLines) {
                        firstLines.add(line);
                        i++;
                        // Only first 5 lines
                        if (i >= 5)
                            break;
                    }
                    firstLines.add("...\n");
                    amountToSubstring = result.length() - 1500;
                }

                String log = "```" +
                        StringUtils.join(firstLines, "\n") +
                        result.substring(amountToSubstring) +
                        "```";

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
                        String vPrefix = "Version:";
                        if (versionMatcher.group(1).equals("Build"))
                            vPrefix = "Build:";
                        // Version: 14.7 OR Build: 18G68
                        eb.addField(vPrefix, versionMatcher.group(2), true);
                        eb.addField("Device: ", device, true);
                    }
                    eb.setColor(new Color(708352));
                } else if (result.contains("IS NOT being signed!")) {
                    // Fancy parsing, no terminal output
                    eb.setTitle("Unsigned");
                    eb.setDescription("");
                    Pattern versionPattern = Pattern.compile("(.*?) (.*?) for device (.*)(?= IS NOT being signed!)");
                    Matcher versionMatcher = versionPattern.matcher(result);
                    if (versionMatcher.find()) {
                        String vPrefix = "Version:";
                        if (versionMatcher.group(1).equals("Build"))
                            vPrefix = "Build:";
                        // Version: 14.7 OR Build: 18G68
                        eb.addField(vPrefix, versionMatcher.group(2), true);
                        eb.addField("Device: ", device, true);
                    }
                    eb.setColor(new Color(16711680));
                }


                runningMessage.editMessageEmbeds(eb.build()).complete();
                runningMessage.editMessage(" ").queue();
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

    private boolean isNotMenuOwner(@NotNull ButtonInteractionEvent event, User user) {
        if (messageAndOwner.get(event.getMessageId()) == null) {
            event.reply("Something went wrong—I forgot who summoned this menu! Please run `/verifyblob` again.").setEphemeral(true).queue();
            return true;
        } else if (!messageAndOwner.get(event.getMessageId()).equals(user.getId())) {
            event.reply("This is not your menu! Start your own with `/verifyblob`.").setEphemeral(true).queue();
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

                File bmFile;
                if (linkMatcher.find()) {
                    String link = linkMatcher.group(0);
                    Message downloadingBmMessage = hook.sendMessage("Downloading BuildManifest...").complete();
                    try {
                        bmFile = getBuildManifestFromUrl(link, ownerId);
                        if (bmFile == null) {
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
                    attachments.get(0).downloadToFile("../files/" + ownerId + "_BuildManifest.plist");
                    bmFile = new File("../files/" + ownerId + "_BuildManifest.plist");
                } else {
                    Message sentMessage = hook.sendMessage("No BuildManifest or valid link provided! Please try again.").complete();
                    message.delete().queueAfter(5, TimeUnit.SECONDS);
                    sentMessage.delete().queueAfter(5, TimeUnit.SECONDS);
                    return;
                }
                HashMap<String, File> files = userAndFiles.get(ownerId);
                files.put("bm", bmFile);
                userAndFiles.put(ownerId, files);

                event.getMessage().delete().queue();
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

                File bmFile = null;
                String version = null;
                String build = null;
                if (linkMatcher.find()) {
                    String link = linkMatcher.group(0);
                    Message downloadingBmMessage = hook.sendMessage("Downloading BuildManifest...").complete();
                    try {
                        bmFile = getBuildManifestFromUrl(link, ownerId);
                        if (bmFile == null) {
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
                    attachments.get(0).downloadToFile("../files/" + ownerId + "_BuildManifest.plist");
                    bmFile = new File("../files/" + ownerId + "_BuildManifest.plist");
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
                if (bmFile != null)
                    tss.put("bm", bmFile.getAbsolutePath());
                if (version != null)
                    tss.put("version", version);
                if (build != null)
                    tss.put("build", build);

                userAndTss.put(event.getAuthor().getId(), tss);

                event.getMessage().delete().queue();
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
            message.reply("Something went wrong—I forgot who summoned me! Please run `/verifyblob` again.").queue();
            return true;
        }
        // If they're not the owner, return true and ignore them
        return !ownerId.equals(author.getId());
    }
}
