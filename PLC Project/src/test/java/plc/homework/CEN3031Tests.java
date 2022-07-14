package plc.homework;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class CEN3031Tests {
    @ParameterizedTest
    @MethodSource
    void testRemoveVowels(String test, String input, String expected) {
        Assertions.assertEquals(expected, new CEN3031().removeVowels(input));
    }

    public static Stream<Arguments> testRemoveVowels() {
        return Stream.of(
                Arguments.of("Normal Sentence", "Remove the vowels", "Rmv th vwls"),
                Arguments.of("Just Vowels", "aoieAUUoie", ""),
                Arguments.of("No Vowels", "qwrtypsdfhjk", "qwrtypsdfhjk"),
                Arguments.of("No Alphabets", "123456", "123456"),
                Arguments.of("Empty String", "", "")
        );
    }

    @ParameterizedTest
    @MethodSource
    void testMultiple(String test, int a, int b, int expected) {
        Assertions.assertEquals(expected, new CEN3031().multiple(a, b));
    }

    public static Stream<Arguments> testMultiple() {
        return Stream.of(
                Arguments.of("Test 1", 2, 2, 4),
                Arguments.of("Test 2", 5, 22, 110),
                Arguments.of("Test 3", 2312, 12312, 28465344),
                Arguments.of("Test 4", 7, 91, 637),
                Arguments.of("Test 5", 12312312, 0, 0)
        );
    }
}
