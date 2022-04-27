package com.coocoofroggy;

import com.coocoofroggy.utils.Listeners;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;

import java.io.File;

public class Main {
    public static JDA jda;
    static String token;

    public static void startBot() throws InterruptedException {
        token = System.getenv("TSSBOT_TOKEN");
        JDABuilder jdaBuilder = JDABuilder.createDefault(token);
        jdaBuilder.setActivity(Activity.watching("blobs."));
        try {
            jda = jdaBuilder.build();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        jda.addEventListener(new Listeners());
        jda.awaitReady();
    }

    public static void registerSlashCommands() {
        // Deletes all slash commands
        /*List<Command> commands = jda.retrieveCommands().complete();
        for (Command command : commands) {
            command.delete().complete();
        }*/

        // Coocoo Test guild
        /*Guild guild = jda.getGuildById("685606700929384489");
        assert guild != null;*/

        jda.upsertCommand("verifyblob", "Verify a blob with img4tool.")
                .addOption(OptionType.ATTACHMENT, "blob", "Blob file usually .shsh2 or .shsh", true)
                .complete();
        jda.upsertCommand("bm", "Get a BuildManifest from an iPSW or OTA URL.")
                .addOption(OptionType.STRING, "url", "URL of iPSW or OTA firmware.", true)
                .complete();
        jda.upsertCommand("tss", "Check signing status of an iOS version.")
                .addOption(OptionType.STRING, "device", "Identifier of device (EG iPhone11,8)", true)
                .complete();

    }

    public static void main(String[] args) {
        if (!new File("files").mkdir()) {
            throw new RuntimeException("Couldn't make files dir.");
        }
        try {
            startBot();
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.exit(1);
            return;
        }
        if (args.length > 0 && args[0].equals("slash")) {
            registerSlashCommands();
            System.exit(0);
        }
    }
}
