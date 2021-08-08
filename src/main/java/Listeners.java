import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
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
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Listeners extends ListenerAdapter {

    HashMap<String, InteractionHook> messageAndHook = new HashMap<>();
    HashMap<String, String> messageAndOwner = new HashMap<>();
    HashMap<String, HashMap<String, File>> userAndFiles = new HashMap<>();
    HashMap<String, HashMap<String, String>> userAndTss = new HashMap<>();

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        // Disallow threads
        if (event.getChannel() == null || event.getChannel().getType() == ChannelType.UNKNOWN) {
            event.reply("Sorry, I don't work with threads yet! Try again in a regular channel.").setEphemeral(true).queue();
            return;
        }

        switch (event.getName()) {
            case "minfw": {
                HashMap<String, Object> embedAndButtons = buildMinFwMenu("frg_start", event.getUser());
                ActionRow actionRow = ActionRow.of((Collection<Button>) embedAndButtons.get("buttons"));
                InteractionHook hook = event.replyEmbeds((MessageEmbed) embedAndButtons.get("embed"))
                        .addActionRows(actionRow)
                        .complete();
                Message sentMessage = hook.retrieveOriginal().complete();
                setMessageHook(sentMessage.getId(), hook);
                setMessageOwner(sentMessage.getId(), event.getUser().getId());
                break;
            }
            case "verifyblob": {
                if (event.getChannel() instanceof GuildChannel) {
                    // If we don't have perms to send message in this channel
                    if (!PermissionUtil.checkPermission(
                            event.getGuildChannel(),
                            Objects.requireNonNull(event.getGuild()).getSelfMember(), // Guild is never null, we are in a GuildChannel
                            Permission.MESSAGE_WRITE)) {
                        event.reply("I don't have permission to send messages in this channel! Try again in another channel.").setEphemeral(true).queue();
                    }
                }
                InteractionHook hook = event.reply("Reply to this message with your blob file.").complete();
                Message sentMessage = hook.retrieveOriginal().complete();
                setMessageHook(sentMessage.getId(), hook);
                setMessageOwner(sentMessage.getId(), event.getUser().getId());
                break;
            }
            case "bm": {
                String url = Objects.requireNonNull(event.getOption("url")).getAsString();
                InteractionHook hook = event.deferReply().complete();
                File bm;
                try {
                    bm = getBuildManifestFromUrl(url, event.getUser().getId());
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
                break;
            }
            case "tss": {
                if (event.getChannel() instanceof GuildChannel) {
                    // If we don't have perms to send message in this channel
                    if (!PermissionUtil.checkPermission(
                            event.getGuildChannel(),
                            Objects.requireNonNull(event.getGuild()).getSelfMember(), // Guild is never null, we are in a GuildChannel
                            Permission.MESSAGE_WRITE)) {
                        event.reply("I don't have permission to send messages in this channel! Try again in another channel.").setEphemeral(true).queue();
                    }
                }

                // Required arg so always not null
                String deviceIdentifier = Objects.requireNonNull(event.getOption("device")).getAsString();

                HashMap<String, String> tssData = new HashMap<>();
                tssData.put("device", deviceIdentifier);
                userAndTss.put(event.getUser().getId(), tssData);

                InteractionHook hook = event.reply("Reply to this message with a BuildManifest, link to firmware, or iOS version/build.").complete();
                Message sentMessage = hook.retrieveOriginal().complete();
                setMessageHook(sentMessage.getId(), hook);
                setMessageOwner(sentMessage.getId(), event.getUser().getId());

                break;
            }
        }
    }

    @Override
    public void onButtonClick(@NotNull ButtonClickEvent event) {
        User user = event.getUser();
        String buttonId = Objects.requireNonNull(event.getButton()).getId(); // Button click event... button can never be null
        assert buttonId != null; // Again, button cannot be null
        if (buttonId.startsWith("frg_")) {
            // If the user who pressed the button isn't the same as the owner of this message, say no
            if (messageAndOwner.get(event.getMessageId()) == null) {
                event.reply("Something went wrong—I forgot who summoned this menu! Please run `/minfw` again.").setEphemeral(true).queue();
                return;
            } else if (!messageAndOwner.get(event.getMessageId()).equals(user.getId())) {
                event.reply("This is not your menu! Start your own with `/minfw`.").setEphemeral(true).queue();
                return;
            }

            HashMap<String, Object> embedAndButtons = buildMinFwMenu(buttonId, user);
            Collection<Button> buttonCollection = (Collection<Button>) embedAndButtons.get("buttons");
            if (!buttonCollection.isEmpty()) {
                // Maybe check to make sure there's not more than 5 buttons
                ActionRow actionRow = ActionRow.of(buttonCollection);
                event.editMessageEmbeds((MessageEmbed) embedAndButtons.get("embed"))
                        .setActionRows(actionRow)
                        .queue();
            } else {
                event.editMessageEmbeds((MessageEmbed) embedAndButtons.get("embed"))
                        .setActionRows()
                        .queue();
            }
        } else if (buttonId.equals("vb_verify")) {
            // If the user who pressed the button isn't the same as the owner of this message, say no
            if (isNotMenuOwner(event, user))
                return;
            // Get rid of verify button
            event.getMessage().delete().queue();
            // Start "thinking"
            InteractionHook hook = event.deferReply().complete();

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

                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder
                        .append("```")
                        .append(StringUtils.join(firstLines, "\n"))
                        .append(result.substring(amountToSubstring))
                        .append("```");

                EmbedBuilder eb = new EmbedBuilder();
                eb.setFooter(user.getName(), user.getAvatarUrl());
                eb.setDescription(stringBuilder.toString());
                if (result.contains("img4tool: failed with exception:")) {
                    eb.setColor(new Color(16753152));
                } else if (result.contains("[IMG4TOOL] APTicket is GOOD!")) {
                    eb.setColor(new Color(708352));
                } else if (result.contains("[IMG4TOOL] APTicket is BAD!")) {
                    eb.setColor(new Color(16711680));
                }

                hook.sendMessageEmbeds(eb.build()).queue();
            } catch (IOException e) {
                e.printStackTrace();
                hook.editOriginal("Failed to run img4tool. Stack trace:\n" +
                        "```\n" +
                        Arrays.toString(e.getStackTrace()) +
                        "\n" +
                        "```").queue();
            }
        } else if (buttonId.equals("tss_check")) {
            // If the user who pressed the button isn't the same as the owner of this message, say no
            if (isNotMenuOwner(event, user))
                return;
            // Get rid of Check TSS button
            event.getMessage().delete().queue();
            // Start "thinking"
            InteractionHook hook = event.deferReply().complete();

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

                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder
                        .append("```")
                        .append(StringUtils.join(firstLines, "\n"))
                        .append(result.substring(amountToSubstring))
                        .append("```");

                EmbedBuilder eb = new EmbedBuilder();
                eb.setFooter(user.getName(), user.getAvatarUrl());
                eb.setDescription(stringBuilder.toString());
                if (result.contains("checking tss status failed!")) {
                    eb.setColor(new Color(16753152));
                } else if (result.contains("IS being signed!")) {
                    // Fancy parsing, no terminal output
                    eb.setTitle("Signed");
                    eb.setDescription("");
                    Pattern versionPattern = Pattern.compile("(?<=Firmware version )(.*?) (.*)(?= IS)");
                    Matcher versionMatcher = versionPattern.matcher(result);
                    if (versionMatcher.find()) {
                        // Version: 14.7 (18G69)
                        eb.addField("Version: " + versionMatcher.group(1) + " (" + versionMatcher.group(2) + ")", versionMatcher.group(2), true);
                        eb.addField("Device: ", device, true);
                    }
                    eb.setColor(new Color(708352));
                } else if (result.contains("IS NOT being signed!")) {
                    // Fancy parsing, no terminal output
                    eb.setTitle("Unsigned");
                    eb.setDescription("");
                    Pattern versionPattern = Pattern.compile("(?<=Firmware version )(.*?) (.*)(?= IS)");
                    Matcher versionMatcher = versionPattern.matcher(result);
                    if (versionMatcher.find()) {
                        // Version: 14.7 (18G69)
                        eb.addField("Version: " + versionMatcher.group(1) + " (" + versionMatcher.group(2) + ")", versionMatcher.group(2), true);
                        eb.addField("Device: ", device, true);
                    }
                    eb.setColor(new Color(16711680));
                }

                hook.sendMessageEmbeds(eb.build()).queue();
            } catch (IOException e) {
                e.printStackTrace();
                hook.editOriginal("Failed to run tsschecker. Stack trace:\n" +
                        "```\n" +
                        Arrays.toString(e.getStackTrace()) +
                        "\n" +
                        "```").queue();
            }
        }
    }

    private boolean isNotMenuOwner(@NotNull ButtonClickEvent event, User user) {
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
    public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot())
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
            case "Reply to this message with your blob file.": {
                String ownerId = messageAndOwner.get(referencedMessage.getId());
                if (isUserNotOwner(ownerId, message, event.getAuthor()))
                    return;

                if (attachments.isEmpty())
                    break;
                InteractionHook hook = messageAndHook.get(referencedMessage.getId());
                File blobFile = new File("collected/" + ownerId + ".shsh2");

                attachments.get(0).downloadToFile(blobFile)
                        .thenAccept(file -> System.out.println("Saved attachment to " + file.getName()))
                        .exceptionally(t ->
                        { // handle failure
                            hook.sendMessage("Unable to save blob file. Please try again.").queue();
                            t.printStackTrace();
                            return null;
                        });

                HashMap<String, File> files = new HashMap<>();
                files.put("blob", blobFile);
                userAndFiles.put(ownerId, files);

                referencedMessage.delete().queue();
                event.getMessage().delete().queue();

                Message sentMessage = hook.sendMessage("Reply to this message with a BuildManifest or a firmware link to verify the blob against.").complete();
                setMessageOwner(sentMessage.getId(), event.getAuthor().getId());
                setMessageHook(sentMessage.getId(), hook);
                break;
            }
            case "Reply to this message with a BuildManifest or a firmware link to verify the blob against.": {
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
                            return;
                        }
                        downloadingBmMessage.delete().queue();
                    } catch (Exception e) {
                        downloadingBmMessage.editMessage("Unable to download BuildManifest from the URL provided.").queue();
                        return;
                    }
                } else if (!attachments.isEmpty()) {
                    attachments.get(0).downloadToFile("collected/" + ownerId + "_BuildManifest.plist");
                    bmFile = new File("collected/" + ownerId + "_BuildManifest.plist");
                } else {
                    Message sentMessage = hook.sendMessage("No BuildManifest or valid link provided! Please try again.").complete();
                    message.delete().queueAfter(5, TimeUnit.SECONDS);
                    sentMessage.delete().queueAfter(5, TimeUnit.SECONDS);
                    return;
                }
                HashMap<String, File> files = userAndFiles.get(ownerId);
                files.put("bm", bmFile);
                userAndFiles.put(ownerId, files);

                referencedMessage.delete().queue();
                event.getMessage().delete().queue();
                Message sentMessage = hook.sendMessage("All set—press the button to verify.").addActionRow(
                        Button.success("vb_verify", "Verify")
                ).complete();
                setMessageOwner(sentMessage.getId(), ownerId);
                setMessageHook(sentMessage.getId(), hook);
                break;
            }
            case "Reply to this message with a BuildManifest, link to firmware, or iOS version/build.": {
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
                            return;
                        }
                        downloadingBmMessage.delete().queue();
                    } catch (Exception e) {
                        downloadingBmMessage.editMessage("Unable to download BuildManifest from the URL provided.").queue();
                        return;
                    }
                } else if (!attachments.isEmpty()) {
                    attachments.get(0).downloadToFile("collected/" + ownerId + "_BuildManifest.plist");
                    bmFile = new File("collected/" + ownerId + "_BuildManifest.plist");
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

                referencedMessage.delete().queue();
                event.getMessage().delete().queue();
                Message sentMessage = hook.sendMessage("All set—press the button to check signing status.").addActionRow(
                        Button.success("tss_check", "Check TSS")
                ).complete();
                setMessageOwner(sentMessage.getId(), ownerId);
                setMessageHook(sentMessage.getId(), hook);

                break;
            }
        }
    }

    public static HashMap<String, Object> buildMinFwMenu(String stage, User user) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Minimum Firmware");
        eb.setFooter(user.getName(), user.getAvatarUrl());
        ArrayList<Button> buttons = new ArrayList<>();
        // @formatter:off
        switch (stage) {
            case "frg_start": {
                eb.setDescription("What type of device do you have?");
                buttons.add(Button.primary("frg_iphone", "iPhone"));
                buttons.add(Button.primary("frg_ipad", "iPad"));
                buttons.add(Button.primary("frg_ipod", "iPod"));
                buttons.add(Button.primary("frg_apple_tv", "Apple TV"));
                break;
            }
                case "frg_iphone": {
                    eb.setDescription("What chip does your iPhone have?");
                    buttons.add(Button.primary("frg_iphone_a10_or_earlier", "A10 or earlier"));
                    buttons.add(Button.primary("frg_iphone_a11", "A11"));
                    buttons.add(Button.primary("frg_iphone_a12_or_later", "A12 or later"));
                    break;
                }
                    case "frg_iphone_a10_or_earlier": {
                        eb.setTitle("iPhone — A10 or earlier");
                        eb.addField("Can restore to:", "iOS 14.0 or later", true);
                        eb.addField("FutureRestore:", "[v194](https://github.com/m1stadev/futurerestore/releases/tag/194) or later", true);
                        break;
                    }
                    case "frg_iphone_a11": {
                        eb.setTitle("iPhone — A11");
                        eb.addField("Can restore to:", "iOS 14.3 or later", true);
                        eb.addField("FutureRestore:", "[v2.0.0 beta](https://github.com/m1stadev/futurerestore/actions)", true);
                        break;
                    }
                    case "frg_iphone_a12_or_later": {
                        eb.setTitle("iPhone — A12 or later");
                        eb.addField("Can restore to:", "iOS 14.0 or later", true);
                        eb.addField("FutureRestore:", "[v2.0.0 beta](https://github.com/m1stadev/futurerestore/actions)", true);
                        break;
                    }
                case "frg_ipad": {
                    eb.setDescription("What chip does your iPad have?");
                    buttons.add(Button.primary("frg_ipad_a10_or_earlier", "A10 or earlier"));
                    buttons.add(Button.primary("frg_ipad_a12_or_later", "A12 or later"));
                    break;
                }
                    case "frg_ipad_a10_or_earlier": {
                        eb.setTitle("iPad — A10 or earlier");
                        eb.addField("Can restore to:", "iOS 14.0 or later", true);
                        eb.addField("FutureRestore:", "[v2.0.0 beta](https://github.com/m1stadev/futurerestore/actions)", true);
                        break;
                    }
                    case "frg_ipad_a12_or_later": {
                        eb.setTitle("iPad — A12 or later");
                        eb.addField("Can restore to:", "iOS 14.0 or later", true);
                        eb.addField("FutureRestore:", "[v2.0.0 beta](https://github.com/m1stadev/futurerestore/actions)", true);
                        break;
                    }
                case "frg_ipod": {
                    eb.setDescription("What chip does your iPod have?");
                    buttons.add(Button.primary("frg_ipod_a10_or_earlier", "A10 or earlier"));
                    break;
                }
                    case "frg_ipod_a10_or_earlier": {
                        eb.setTitle("iPod — A10 or earlier");
                        eb.addField("Can restore to:", "iOS 14.0 or later", true);
                        eb.addField("FutureRestore:", "[v2.0.0 beta](https://github.com/m1stadev/futurerestore/actions)", true);
                        break;
                    }
                    case "frg_ipod_a12_or_later": {
                        eb.addField("Can restore to:", "iOS 14.0 or later", true);
                        eb.addField("FutureRestore:", "[v2.0.0 beta](https://github.com/m1stadev/futurerestore/actions)", true);
                        break;
                    }
                case "frg_apple_tv": {
                    eb.setDescription("What Apple TV do you have?");
                    buttons.add(Button.primary("frg_apple_tv_early", "Apple TV 3 or Earlier"));
                    buttons.add(Button.primary("frg_apple_tv_hd", "Apple TV HD"));
                    buttons.add(Button.primary("frg_apple_tv_4k", "Apple TV 4K or later"));
                    break;
                }
                    case "frg_apple_tv_early": {
                        eb.setTitle("Apple TV — 3rd Gen or earlier");
                        eb.addField("Can restore to:", "No clue, this TV is older than JTV's mother", true);
                        eb.addField("FutureRestore:", "[tihmstar](https://github.com/tihmstar/futurerestore/) maybe?", true);
                        eb.addField("Hotel:", "Trivago", true);
                        break;
                    }
                    case "frg_apple_tv_hd": {
                        eb.setTitle("Apple TV HD");
                        eb.addField("Can restore to:", "tvOS 14.0 or later", true);
                        eb.addField("FutureRestore:", "[v194](https://github.com/m1stadev/futurerestore/releases/tag/194) or later", true);
                        break;
                    }
                    case "frg_apple_tv_4k": {
                        eb.setTitle("Apple TV — 4K or Later");
                        eb.addField("Restoring:", "Cannot restore, no public iPSWs available.", true);
                        eb.addField("FutureRestore:", "You'd need a special cable for the computer to even recognize the device. Also, 4K gen 2 has nonce entanglement. Good luck setting a generator and saving blobs lol", true);
                        break;
                    }
        }
        // @formatter:on
        HashMap<String, Object> embedAndButtons = new HashMap<>();
        embedAndButtons.put("embed", eb.build());
        embedAndButtons.put("buttons", buttons);
        return embedAndButtons;
    }

    public static File getBuildManifestFromUrl(String urlString, String userId) throws Exception {
        URL url = new URL(urlString);
        String pathToSave = "collected/" + userId + "_BuildManifest.plist";

        // Thanks to airsquared for finding this HttpChannel
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

    /* *** UTILITIES *** */
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