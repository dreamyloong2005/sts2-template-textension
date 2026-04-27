package com.dreamyloong.template.sts2.windows;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public final class Sts2WindowsConsoleLaunchHelper {
    private Sts2WindowsConsoleLaunchHelper() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 6 || !"--working-dir".equals(args[0])) {
            throw new IllegalArgumentException("Missing --working-dir for STS2 Windows console launch helper.");
        }

        String workingDirectory = args[1];
        String pidFilePath = null;
        for (int index = 2; index < args.length - 1; index += 1) {
            if ("--pid-file".equals(args[index])) {
                pidFilePath = args[index + 1];
                break;
            }
        }
        if (pidFilePath == null || pidFilePath.isBlank()) {
            throw new IllegalArgumentException("Missing --pid-file for STS2 Windows console launch helper.");
        }

        int separatorIndex = -1;
        for (int index = 4; index < args.length; index += 1) {
            if ("--".equals(args[index])) {
                separatorIndex = index;
                break;
            }
        }
        if (separatorIndex < 0 || separatorIndex >= args.length - 1) {
            throw new IllegalArgumentException("Missing command payload for STS2 Windows console launch helper.");
        }

        List<String> command = Arrays.asList(Arrays.copyOfRange(args, separatorIndex + 1, args.length));
        Process process = new ProcessBuilder(command)
            .directory(new File(workingDirectory))
            .inheritIO()
            .start();
        Files.writeString(new File(pidFilePath).toPath(), Long.toString(process.pid()), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        System.exit(exitCode);
    }
}
