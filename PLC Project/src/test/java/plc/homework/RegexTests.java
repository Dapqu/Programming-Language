package plc.homework;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Contains JUnit tests for {@link Regex}. A framework of the test structure 
 * is provided, you will fill in the remaining pieces.
 *
 * To run tests, either click the run icon on the left margin, which can be used
 * to run all tests or only a specific test. You should make sure your tests are
 * run through IntelliJ (File > Settings > Build, Execution, Deployment > Build
 * Tools > Gradle > Run tests using <em>IntelliJ IDEA</em>). This ensures the
 * name and inputs for the tests are displayed correctly in the run window.
 */
public class RegexTests {

    /**
     * This is a parameterized test for the {@link Regex#EMAIL} regex. The
     * {@link ParameterizedTest} annotation defines this method as a
     * parameterized test, and {@link MethodSource} tells JUnit to look for the
     * static method {@link #testEmailRegex()}.
     *
     * For personal preference, I include a test name as the first parameter
     * which describes what that test should be testing - this is visible in
     * IntelliJ when running the tests (see above note if not working).
     */
    @ParameterizedTest
    @MethodSource
    public void testEmailRegex(String test, String input, boolean success) {
        test(input, Regex.EMAIL, success);
    }
    /**
     * This is the factory method providing test cases for the parameterized
     * test above - note that it is static, takes no arguments, and has the same
     * name as the test. The {@link Arguments} object contains the arguments for
     * each test to be passed to the function above.
     */
    public static Stream<Arguments> testEmailRegex() {
        return Stream.of(
                Arguments.of("Alphanumeric", "thelegend27@gmail.com", true),
                Arguments.of("UF Domain", "otherdomain@ufl.edu", true),
                Arguments.of("Two Dots in Domain", "twodots@first.second.com", true),
                Arguments.of("Multiple Dots in Domain", "multidots@first.second.third.furth.org", true),
                Arguments.of("Dot in Username", "firstName.lastName@yahoo.com", true),
                Arguments.of("Missing Domain Dot", "missingdot@gmailcom", false),
                Arguments.of("Too Few Characters in Username", "1@tooFewCharacter.com", false),
                Arguments.of("Upper Case in TLD", "capTLD@apple.COM", false),
                Arguments.of("Missing Domain", "missingDomain@.edu", false),
                Arguments.of("Symbols", "symbols#$%@gmail.com", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testOddStringsRegex(String test, String input, boolean success) {
        test(input, Regex.ODD_STRINGS, success);
    }

    public static Stream<Arguments> testOddStringsRegex() {
        return Stream.of(
                // what have eleven letters and starts with gas?
                Arguments.of("11 Characters", "automobiles", true),
                Arguments.of("13 Characters", "i<3pancakes13", true),
                Arguments.of("15 Characters", "~!@#$%^&*()_+:?", true),
                Arguments.of("17 Characters", "UniversityOfTexas", true),
                Arguments.of("19 Characters", "dilimulati.diliyaer", true),
                Arguments.of("5 Characters", "5five", false),
                Arguments.of("14 Characters", "i<3pancakes14!", false),
                Arguments.of("20 Characters", "thisTwentyCharacters", false),
                Arguments.of("10 Characters", "Characters", false),
                Arguments.of("21 Characters", "twentyOneIsEqualTo21.", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testCharacterListRegex(String test, String input, boolean success) {
        test(input, Regex.CHARACTER_LIST, success);
    }

    public static Stream<Arguments> testCharacterListRegex() {
        return Stream.of(
                Arguments.of("Single Element", "['a']", true),
                Arguments.of("Multiple Elements", "['a','b','c']", true),
                Arguments.of("Empty List", "[]", true),
                Arguments.of("Multiple Elements with Single Space", "['a', 'b', 'c']", true),
                Arguments.of("Multiple Elements with Inconsistent Single Space", "['a','b', 'c']", true),
                Arguments.of("Multiple Escape Characters", "['\t','\b','\r']", true),
                Arguments.of("Missing Brackets", "'a','b','c'", false),
                Arguments.of("Trailing Comma", "['a', 'b', 'c',]", false),
                Arguments.of("String as Elements", "['abc', 'bcd', 'cde']", false),
                Arguments.of("Starts with a Comma", "[,'a']", false),
                Arguments.of("Missing Commas", "['a' 'b' 'c']", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testDecimalRegex(String test, String input, boolean success) {
        test(input, Regex.DECIMAL, success);
    }

    public static Stream<Arguments> testDecimalRegex() {
        return Stream.of(
                Arguments.of("Multiple Digit Decimal", "10100.001", true),
                Arguments.of("Negative Decimal", "-1.0", true),
                Arguments.of("Trailing Zeros", "837.0000000", true),
                Arguments.of("Less than 1", "0.28", true),
                Arguments.of("Pi", "3.1415926", true),
                Arguments.of("Integer Only", "1", false),
                Arguments.of("Leading Decimal", ".5", false),
                Arguments.of("Left Most Digit is Zero", "0134.749", false),
                Arguments.of("Ends with Decimal", "38.", false),
                Arguments.of("No Number", ".", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testStringRegex(String test, String input, boolean success) {
        test(input, Regex.STRING, success);
    }

    public static Stream<Arguments> testStringRegex() {
        return Stream.of(
                Arguments.of("Empty String", "\"\"", true),
                Arguments.of("Hello World", "\"Hello, World!\"", true),
                Arguments.of("Escape Character", "\"1\\t2\"", true),
                Arguments.of("While Spaces with new lines", "\"\\n\\n\\n\"", true),
                Arguments.of("Every escape", "\"\\b\\n\\r\\t\\'\\\"\\\\\"", true),
                Arguments.of("Unterminated", "\"unterminated", false),
                Arguments.of("Invalid Escape", "\"invalid\\escape\"", false),
                Arguments.of("No double quotes", "noDoubleQuotes", false),
                Arguments.of("Only Invalid Escapes", "\"\\e\\w\\s\\d\\D\\B\\f\\S\\v\"", false),
                Arguments.of("Nothing", "", false)
        );
    }

    /**
     * Asserts that the input matches the given pattern. This method doesn't do
     * much now, but you will see this concept in future assignments.
     */
    private static void test(String input, Pattern pattern, boolean success) {
        Assertions.assertEquals(success, pattern.matcher(input).matches());
    }

}
