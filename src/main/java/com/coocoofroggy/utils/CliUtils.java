package com.coocoofroggy.utils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class CliUtils {
    public static String tssChecker(ArrayList<String> args) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(args);

        // Merge stderr with stdout
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        InputStream inputStream = process.getInputStream();
        return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
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

    public static String img4toolInfo(File blob) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("img4tool", "--shsh", blob.getAbsolutePath());
        // Merge stderr with stdout
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        InputStream inputStream = process.getInputStream();
        return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
    }
}
