import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;

import java.util.List;

public class Main {
    static JDA jda;
    static String token;

    public static boolean startBot() throws InterruptedException {
        token = System.getenv("TSSBOT_TOKEN");
        JDABuilder jdaBuilder = JDABuilder.createDefault(token);
        jdaBuilder.setActivity(Activity.watching("blobs."));
        try {
            jda = jdaBuilder.build();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        jda.addEventListener(new Listeners());
        jda.awaitReady();
        return true;
    }

    public static void registerSlashCommands() {
//        Guild testGuild = jda.getGuildById("685606700929384489");
//        assert testGuild != null;

        /*
        List<Command> commands = jda.retrieveCommands().complete();
        for (Command command : commands) {
            command.delete().complete();
        }

        jda.upsertCommand("minfw", "Find limits to the versions you can FutureRestore to.").complete();
        jda.upsertCommand("verifyblob", "Verify a blob with img4tool.").complete();
        jda.upsertCommand("bm", "Get a BuildManifest from an iPSW or OTA URL.")
                .addOption(OptionType.STRING, "url", "URL of iPSW or OTA firmware.", true)
                .complete();

         */
        jda.upsertCommand("tss", "Check signing status of an iOS version.")
                .addOption(OptionType.STRING, "device", "Identifier of device (EG iPhone11,8)", true)
                .complete();

    }
    
    public static void main(String[] args) {
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
