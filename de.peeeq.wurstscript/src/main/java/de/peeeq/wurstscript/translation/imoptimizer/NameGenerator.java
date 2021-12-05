package de.peeeq.wurstscript.translation.imoptimizer;

import de.peeeq.wurstscript.utils.Debug;

import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This will be used to generate unique Strings which aren't named like the ones
 * in the restricted list.
 * Main use for compressing names.
 *
 * @author Frotty
 */
public class NameGenerator {
    public static boolean USE_CONFUSE = false;
    public static String CUSTOM_CHARMAP = "";
    /**
     * The given charmap
     */
    private String charmapFirst = "lLoOiI";
    private String charmap = charmapFirst + "01";
    private String charmapMid = charmap + "_";

    static String shuffle(String string){
        List<Character> list = string.chars().mapToObj(c -> (char) c)
            .collect(Collectors.toList());
        Collections.shuffle(list);
        StringBuilder sb = new StringBuilder();
        list.forEach(sb::append);

        return sb.toString();
    }

    public void setConfusingCharmap() {
        String first = shuffle("lLoOiI");
        setCharmap(first, first + "01_", first  + "01");
    }

    public void setMaxCharmap() {
        String first = shuffle("wprojDfsWAqYcCUyMdLeGiPuxSanEtJmHBOKhQFbgvRNIXTZklV");
        String charmap = first + shuffle("3901724568");
        setCharmap(first, charmap + "_", charmap);
    }

    private void setCharmap(String first, String mid, String other) {
        charmapFirst = first;
        charmap = other;
        charmapMid = mid;
        checkCharmap(charmapFirst);
        checkCharmap(charmap);
        checkCharmap(charmapMid);
        length = charmap.length();
        lengthMid = charmapMid.length();
        lengthFirst = charmapFirst.length();
    }

    private String TEcharmap = "wurstqeiopadfghjklyxcvbnm";
    /**
     * A counter
     */
    private int currentId = 0;
    private int TEId = 0;
    /**
     * length of charmap
     */
    private int lengthFirst;
    private int length;
    private int lengthMid;
    private int TElength = 25;


    /**
     * Creates a new NameGenerator
     *
     * @throws FileNotFoundException
     */
    public NameGenerator() {
        if (CUSTOM_CHARMAP.length() > 0) {
            String normalized = CUSTOM_CHARMAP.replaceAll("_", "");
            setCharmap(normalized.replaceAll("\\d+",""), normalized + "_", normalized);
        } else if (USE_CONFUSE) {
            setConfusingCharmap();
        } else {
            setMaxCharmap();
        }
    }

    private void checkCharmap(String c) {
        for (int i = 0; i < c.length(); i++) {
            for (int j = i + 1; j < c.length(); j++) {
                if (c.charAt(i) == c.charAt(j)) {
                    throw new Error("Charmap contains letter " + c.charAt(i) + " twice. -- " + c);
                }
            }
        }

    }

    /**
     * Get a token
     *
     * @return A (for this Namegenrator) unique token
     */
    public String getToken() {
        int id = currentId++;
        StringBuilder b = new StringBuilder();
        b.append(charmapFirst.charAt(id % lengthFirst));
        if ((id /= lengthFirst) != 0) {
            do {
                if (id > lengthMid) {
                    b.append(charmapMid.charAt((id - 1) % lengthMid));
                } else {
                    b.append(charmap.charAt((id - 1) % length));
                }
            } while ((id /= length) != 0);
        }

        return b.toString();
    }

    /**
     * Get a token that is cross checked with the Restricted names
     *
     * @return A checked, unique token
     */
    public String getUniqueToken() {
        String s = getToken();
        while (RestrictedCompressedNames.contains(s)) {
            Debug.println(s + "is restricted");
            // Wishful thinking, but normally this should work
            // there are only a handful of restricted names anyway.
            s = getToken();
        }

        return s;
    }

    /**
     * Get a token that can be used for TE and TRVE since
     * its only lowercase.
     * To be unique these start with "z"
     *
     * @return A checked, unique token, lowercase, starting with z
     */
    public String getTEToken() {
        int id = TEId++;
        StringBuilder b = new StringBuilder("z");
        do {
            b.append(TEcharmap.charAt(id % TElength));
        } while ((id /= TElength) != 0);

        return b.toString();
    }


}
