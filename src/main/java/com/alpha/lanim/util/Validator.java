package com.alpha.lanim.util;

public final class Validator {

    private Validator() {}

    public static String validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "Nickname cannot be empty";
        }
        String trimmed = nickname.trim();
        if (trimmed.length() > Constants.MAX_NICKNAME_LENGTH) {
            return "Nickname must be at most " + Constants.MAX_NICKNAME_LENGTH + " characters";
        }
        return null;
    }

    public static String validateRoomSecret(String roomSecret) {
        if (roomSecret == null || roomSecret.isEmpty()) {
            return "Room secret cannot be empty";
        }
        if (roomSecret.length() < 3) {
            return "Room secret must be at least 3 characters";
        }
        return null;
    }

    public static String validateChatText(String text) {
        if (text == null || text.isEmpty()) {
            return "Message cannot be empty";
        }
        if (text.length() > Constants.MAX_TEXT_LENGTH) {
            return "Message must be at most " + Constants.MAX_TEXT_LENGTH + " characters";
        }
        return null;
    }

    public static boolean isValidPort(int port) {
        return port >= 0 && port <= 65535;
    }
}
