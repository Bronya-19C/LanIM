package com.alpha.lanim.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

/**
 * Opens/closes a temporary Windows Firewall inbound rule for the server port.
 * Requires administrator privileges; fails gracefully with a console hint otherwise.
 */
public final class WindowsFirewallHelper {

    private static final String RULE_PREFIX = "LanIM Server TCP ";

    private WindowsFirewallHelper() {}

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    public static String ruleName(int port) {
        return RULE_PREFIX + port;
    }

    public static boolean openInboundTcp(int port) {
        if (!isWindows()) {
            return false;
        }
        if (ruleExists(port)) {
            System.out.println("Firewall rule already present: " + ruleName(port));
            return false;
        }
        int exit = runNetsh(
                "advfirewall", "firewall", "add", "rule",
                "name=" + ruleName(port),
                "dir=in",
                "action=allow",
                "protocol=TCP",
                "localport=" + port
        );
        if (exit == 0) {
            System.out.println("Firewall: opened inbound TCP " + port + " (" + ruleName(port) + ")");
            return true;
        }
        printAdminHint(port);
        return false;
    }

    public static void removeInboundTcp(int port) {
        if (!isWindows()) {
            return;
        }
        if (!ruleExists(port)) {
            return;
        }
        int exit = runNetsh(
                "advfirewall", "firewall", "delete", "rule",
                "name=" + ruleName(port)
        );
        if (exit == 0) {
            System.out.println("Firewall: removed inbound TCP " + port + " (" + ruleName(port) + ")");
        }
    }

    private static boolean ruleExists(int port) {
        return runNetsh(
                "advfirewall", "firewall", "show", "rule",
                "name=" + ruleName(port)
        ) == 0;
    }

    private static void printAdminHint(int port) {
        System.err.println("Firewall: could not open inbound TCP " + port
                + ". Run this terminal as Administrator, or add the rule manually:");
        System.err.println("  netsh advfirewall firewall add rule name=\"" + ruleName(port)
                + "\" dir=in action=allow protocol=TCP localport=" + port);
    }

    private static int runNetsh(String... args) {
        try {
            String[] command = new String[args.length + 1];
            command[0] = "netsh";
            System.arraycopy(args, 0, command, 1, args.length);

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            Charset cs = Charset.defaultCharset();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), cs))) {
                while (reader.readLine() != null) {
                    // drain output so the process does not block
                }
            }

            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return -1;
            }
            return process.exitValue();
        } catch (Exception e) {
            return -1;
        }
    }
}
