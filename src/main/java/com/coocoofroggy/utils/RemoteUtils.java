package com.coocoofroggy.utils;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class RemoteUtils {
    public static InputStream fetchBuildManifestFromUrl(String urlString, String userId) throws Exception {
        URL url = new URL(urlString);

        // Make a new InputStream so that we can reset() it
        InputStream urlStream = new BufferedInputStream(url.openStream());
        // Get the true Content-Type, since Apple lies about it
        String contentType = fetchContentTypeFromUrl(urlStream);
        // reset() back to beginning to not mess with anything else later
        urlStream.reset();
        switch (contentType) {
            // Link to an actual BM
            case "application/x-plist" -> {
                return urlStream;
            }
            // Likely an ipsw. Do partial-zip stuff here
            case "application/zip" -> {
                // Thanks to airsquared for finding this HttpChannel
                ZipFile ipsw = new ZipFile(new HttpChannel(url), userId + " iPSW", StandardCharsets.UTF_8.name(), true, true);
                ZipArchiveEntry bmEntry = ipsw.getEntry("BuildManifest.plist");
                if (bmEntry == null) {
                    bmEntry = ipsw.getEntry("AssetData/boot/BuildManifest.plist");
                    if (bmEntry == null) {
                        return null;
                    }
                }
                InputStream buildManifestInputStream = ipsw.getInputStream(bmEntry);
                ipsw.close();

                return buildManifestInputStream;
            }
            default -> {
                return null;
            }
        }
    }

    private static String fetchContentTypeFromUrl(InputStream inputStream) throws IOException, SAXException, TikaException {
        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        parser.parse(inputStream, new BodyContentHandler(), metadata, new ParseContext());
        String contentType = metadata.get("Content-Type");
        return contentType;
    }
}
