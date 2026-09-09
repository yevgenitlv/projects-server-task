package com.ivory.employees.util;

/**
 * Converts Hebrew stored in <em>visual</em> order - the order the characters appear on screen, as
 * produced by older reporting systems - into logical order, the order the letters are typed and the
 * order every modern reader expects.
 *
 * <p>A visual-order record holds the city Haifa as {@code הפיח}; read back to front it is
 * {@code חיפה}. Reversing the text restores that, but it would also turn the house number 35 into
 * 53, so digit runs are flipped back.
 *
 * <p>Values with no Hebrew in them - codes, Latin names, amounts - are returned untouched.
 */
public final class HebrewText {

    private HebrewText() {
    }

    /** Returns the text in logical order, or the text itself when it holds no Hebrew. */
    public static String toLogical(String text) {
        if (text == null || text.isEmpty() || !containsHebrew(text)) {
            return text;
        }
        String reversed = new StringBuilder(text).reverse().toString();

        StringBuilder logical = new StringBuilder(reversed.length());
        int i = 0;
        while (i < reversed.length()) {
            if (Character.isDigit(reversed.charAt(i))) {
                int end = i;
                while (end < reversed.length() && Character.isDigit(reversed.charAt(end))) {
                    end++;
                }
                logical.append(new StringBuilder(reversed.substring(i, end)).reverse());
                i = end;
            } else {
                logical.append(reversed.charAt(i));
                i++;
            }
        }
        return logical.toString().trim();
    }

    /** True when the text holds at least one character from the Hebrew block. */
    public static boolean containsHebrew(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '֐' && c <= '׿') {
                return true;
            }
        }
        return false;
    }
}
