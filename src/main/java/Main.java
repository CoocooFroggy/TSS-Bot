import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;

public class Main {
    static JDA jda;
    static String token;

    public static boolean startBot() throws InterruptedException {
        token = System.getenv("FDR_TOKEN");
        JDABuilder jdaBuilder = JDABuilder.createDefault(token);
//        jdaBuilder.setActivity(Activity.playing(""));
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
        // DEBUG
        Guild testGuild = jda.getGuildById("685606700929384489");
        assert testGuild != null;

        testGuild.upsertCommand("fdrlimit", "Find limits to the versions you can FutureRestore to.").queue();
        testGuild.upsertCommand("verifyblob", "Verify a blob with img4tool.").queue();
        testGuild.upsertCommand("bmfromurl", "Get a BuildManifest from an iPSW or OTA URL.")
                .addOption(OptionType.STRING, "url", "URL of iPSW or OTA firmware.", true)
                .queue();
    }
    
    public static void main(String[] args) {
        try {
            startBot();
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.exit(1);
            return;
        }
        registerSlashCommands();
    }
}
