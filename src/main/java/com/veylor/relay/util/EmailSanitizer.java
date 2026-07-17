package com.veylor.relay.util;

import java.util.Locale;

public class EmailSanitizer {

    public static String sanitize(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        int atIndex = trimmed.indexOf('@');
        if (atIndex == -1 || atIndex == 0 || atIndex == trimmed.length() - 1) {
            return trimmed.toLowerCase(Locale.ROOT);
        }

        String localPart = trimmed.substring(0, atIndex);
        String domainPart = trimmed.substring(atIndex + 1).toLowerCase(Locale.ROOT);

        if (domainPart.equals("gmail.com") || domainPart.equals("googlemail.com")) {
            int plusIndex = localPart.indexOf('+');
            if (plusIndex != -1) {
                localPart = localPart.substring(0, plusIndex);
            }
            localPart = localPart.replace(".", "");
            localPart = localPart.toLowerCase(Locale.ROOT);
        }
        // For non-Gmail domains, preserve local-part casing

        return localPart + "@" + domainPart;
    }
}
