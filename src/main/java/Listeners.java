import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
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
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Listeners extends ListenerAdapter {

    HashMap<String, InteractionHook> messageAndHook = new HashMap<>();
    HashMap<String, String> messageAndOwner = new HashMap<>();
    HashMap<String, HashMap<String, File>> userAndFiles = new HashMap<>();
    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        switch (event.getName()) {
            case "minfw": {
                HashMap<String, Object> embedAndButtons = buildFrGuideEmbed("frg_start", event.getUser());
                ActionRow actionRow = ActionRow.of((Collection<Button>) embedAndButtons.get("buttons"));
                InteractionHook hook = event.replyEmbeds((MessageEmbed) embedAndButtons.get("embed"))
                        .addActionRows(actionRow)
                        .complete();
                Message sentMessage = hook.retrieveOriginal().complete();
                messageAndHook.put(sentMessage.getId(), hook);
                messageAndOwner.put(sentMessage.getId(), event.getUser().getId());
                break;
            }
            case "verifyblob": {
                if (event.getChannel() instanceof GuildChannel) {
                    // If we don't have perms to send message in this channel
                    if (!PermissionUtil.checkPermission(event.getGuildChannel(), event.getGuild().getSelfMember(), Permission.MESSAGE_WRITE)) {
                        event.reply("I don't have permission to send messages in this channel! Try again in another channel.").setEphemeral(true).queue();
                    }
                }
                InteractionHook hook = event.reply("Reply to this message with your blob file.").complete();
                Message sentMessage = hook.retrieveOriginal().complete();
                messageAndHook.put(sentMessage.getId(), hook);
                messageAndOwner.put(sentMessage.getId(), event.getUser().getId());
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
            }
        }
    }

    @Override
    public void onButtonClick(@NotNull ButtonClickEvent event) {
        User user = event.getUser();
        String buttonId = event.getButton().getId();
        if (buttonId.startsWith("frg_")) {
            // If the user who pressed the button isn't the same as the owner of this message, say no
            if (messageAndOwner.get(event.getMessageId()) == null) {
                event.reply("Something went wrong—I forgot who summoned this menu! Please run `/frguide` again.").setEphemeral(true).queue();
                return;
            }
            else if (!messageAndOwner.get(event.getMessageId()).equals(user.getId())) {
                event.reply("This is not your menu! Start your own with `/frguide`.").setEphemeral(true).queue();
                return;
            }

            HashMap<String, Object> embedAndButtons = buildFrGuideEmbed(buttonId, user);
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
        }
        else if (buttonId.startsWith("vb_")) {
            // If the user who pressed the button isn't the same as the owner of this message, say no
            if (messageAndOwner.get(event.getMessageId()) == null) {
                event.reply("Something went wrong—I forgot who summoned this menu! Please run `/verifyblob` again.").setEphemeral(true).queue();
                return;
            }
            else if (!messageAndOwner.get(event.getMessageId()).equals(user.getId())) {
                event.reply("This is not your menu! Start your own with `/verifyblob`.").setEphemeral(true).queue();
                return;
            }
            // Get rid of verify button
            event.getMessage().delete().queue();

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
        }
    }

    @Override
    public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot())
            return;

        Message message = event.getMessage();
        List<Message.Attachment> attachments = message.getAttachments();
        Message referencedMessage = message.getReferencedMessage();
        if (referencedMessage == null)
            return;
        if (!referencedMessage.getAuthor().getId().equals("872591654215893042"))
            return;
        String ownerId = messageAndOwner.get(referencedMessage.getId());
        if (ownerId == null) {
            message.reply("Something went wrong—I forgot who summoned me! Please run `/verifyblob` again.").queue();
            return;
        }
        if (!ownerId.equals(event.getAuthor().getId()))
            return;
        switch (referencedMessage.getContentRaw()) {
            case "Reply to this message with your blob file.": {
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
                messageAndOwner.put(sentMessage.getId(), event.getAuthor().getId());
                messageAndHook.put(sentMessage.getId(), hook);
                break;
            }
            case "Reply to this message with a BuildManifest or a firmware link to verify the blob against.": {
                InteractionHook hook = messageAndHook.get(referencedMessage.getId());

                String content = message.getContentRaw();
                Pattern linkPattern = Pattern.compile("https?:\\/\\/.*?(?=\\s|\\n|$)");
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
                    hook.sendMessage("No BuildManifest or valid link provided! Please try again.").queue();
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
                messageAndOwner.put(sentMessage.getId(), ownerId);
                messageAndHook.put(sentMessage.getId(), hook);
                break;
            }
        }
    }

    public static HashMap<String, Object> buildFrGuideEmbed(String stage, User user) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("FutureRestore Guide");
        ArrayList<Button> buttons = new ArrayList<>();
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
        }
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
        // Merge stderr  with stdout
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        InputStream inputStream = process.getInputStream();
        String result = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        return result;
    }
}