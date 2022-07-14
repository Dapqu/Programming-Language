package plc.homework;

import java.util.regex.Pattern;

public class CEN3031 {
    public String removeVowels(String input) {
        return input.replaceAll("[aeiouAEIOU]", "");
    }

    public int multiple(int a, int b) {
        return a * b;
    }
}
