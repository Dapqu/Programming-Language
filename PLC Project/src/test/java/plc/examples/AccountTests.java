package plc.examples;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Contains JUnit tests for {@link Account}. Test structure for steps 1 & 2 are
 * provided, you must create this yourself for step 3.
 *
 * To run tests, either click the run icon on the left margin, which can be used
 * to run all tests or only a specific test. You should make sure your tests are
 * run through IntelliJ (File > Settings > Build, Execution, Deployment > Build
 * Tools > Gradle > Run tests using <em>IntelliJ IDEA</em>). This ensures the
 * name and inputs for the tests are displayed correctly in the run window.
 */
public class AccountTests {

    /**
     * This is a test of {@link Account#isOverdrawn}.
     * The {@link Test} annotation defines this as a JUnit test.
     * Notice how execution configuration to the left of the method.
     */
    @Test
    void testPassed() {
        Account account = new Account();
        Assertions.assertEquals(false, account.isOverdrawn());
    }

    @Test
    void testFailed() {
        Account account = new Account();
        Assertions.assertEquals(true, account.isOverdrawn());
    }

    @Test
    void testOverdrawn() {
        Account account = new Account(-10);
        Assertions.assertEquals(true, account.isOverdrawn());
    }

    /**
     * This is a parameterized test for {@link Account#deposit}.
     * The {@link ParameterizedTest} annotation defines this method as a
     * parameterized test, and {@link MethodSource} tells JUnit to look for the
     * static method {@link #testDeposit()} following below.
     *
     * For personal preference, I include a test name as the first parameter
     * which describes what that test should be testing - this is visible in
     * IntelliJ when running the tests (see above note if not working).
     */
    @ParameterizedTest
    @MethodSource
    void testDeposit(String test, int balance, int amount, Account expected) {
        Account account = new Account(balance);
        account.deposit(amount);

        Assertions.assertEquals(expected, account);
    }

    /**
     * This is the factory method providing test cases for the parameterized
     * test above - note that it is static, takes no arguments, and has the same
     * name as the test. The {@link Arguments} object contains the arguments for
     * each test to be passed to the function above.
     */
    public static Stream<Arguments> testDeposit() {
        return Stream.of(
                Arguments.of("Positive Deposit", 100, 50, new Account(150)),
                Arguments.of("Negative Deposit", 100, -50, new Account(50))
        );
    }

    @ParameterizedTest
    @MethodSource
    void testWithdraw(String test, int balance, int amount, Account expected) {
        Account account = new Account(balance);
        if (expected != null) {
            account.withdraw(amount);
            Assertions.assertEquals(expected, account);
        } else {
            Assertions.assertThrows(NumberFormatException.class, () -> account.withdraw(amount));
        }

    }

    public static Stream<Arguments> testWithdraw() {
        return Stream.of(
                Arguments.of("Positive Withdraw", 100, 50, new Account(50)),
                Arguments.of("Negative Withdraw", 100, -50, null)
        );
    }

}
