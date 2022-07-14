package plc.examples;

public class Account {

    private int balance;

    public Account() {
        balance = 25;
    }

    public Account(int balance) { this.balance = balance; }

    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) { this.balance = balance; }

    public void deposit(int amount) { balance += amount; }

    public boolean isOverdrawn() { return balance < 0; }

    public int withdraw(int amount) {
        if (amount < 0) {
            throw new NumberFormatException("Cannot withdraw a negative amount.");
        }
        balance -= amount;
        return amount;
    }

    /**
     * Comment out {@link #equals} to and observe how account object references
     * in memory are compared instead of the account object contents.
     */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof Account &&
                balance == ((Account) obj).getBalance();
    }

    @Override
    public String toString() {
        return "Account{" + "balance = " + balance + '}' +
               ", Object{" + super.toString() + "}";
    }

}
