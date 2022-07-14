package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        List<Ast.Global> globalList = ast.getGlobals();
        List<Ast.Function> functionList = ast.getFunctions();

        globalList.forEach(this::visit);
        functionList.forEach(this::visit);

        Environment.Function function = scope.lookupFunction("main", 0);

        return function.invoke(new ArrayList<>());
    }

    @Override
    public Environment.PlcObject visit(Ast.Global ast) {
        if (ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(), ast.getMutable(), visit(ast.getValue().get()));
        }
        else {
            scope.defineVariable(ast.getName(), ast.getMutable(), Environment.NIL);
        }
        return Environment.NIL;

    }

    @Override
    public Environment.PlcObject visit(Ast.Function ast) {
        List<String> parameterList = ast.getParameters();

        Scope oldScope = scope;

        scope.defineFunction(ast.getName(), parameterList.size(), args -> {
            Scope newScope = scope;
            try {
                // Set up new scope.
                scope = new Scope(oldScope);
                // Define variables for the incoming arguments.
                for (int i = 0; i < parameterList.size(); i++) {
                    scope.defineVariable(parameterList.get(i), true, args.get(i));
                }
                // Returns the value contained in a Return exception if thrown, otherwise NIL.
                try {
                    ast.getStatements().forEach(this::visit);
                    return Environment.NIL;
                }
                catch (Return r) {
                    return r.value;
                }
            }
            finally {
                scope = newScope;
            }
        });

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast) {
        if (ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(), true, visit(ast.getValue().get()));
        }
        else {
            scope.defineVariable(ast.getName(), true, Environment.NIL);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {
        Ast.Expression.Access receiver = (Ast.Expression.Access) ast.getReceiver();
        Environment.Variable var = scope.lookupVariable(receiver.getName());
        if (!var.getMutable())
            throw new RuntimeException("Tried to mutate immutable variable");
        if (receiver.getOffset().isPresent())
        {
            List<Object> list = (List<Object>) var.getValue().getValue();
            Ast.Expression.Literal temp = (Ast.Expression.Literal) ast.getValue();
            list.set(((BigInteger)visit(receiver.getOffset().get()).getValue()).intValue(), temp.getLiteral());
            var.setValue(Environment.create(list));
        }
        else
        {
            var.setValue(visit(ast.getValue()));
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {
        if (requireType(Boolean.class, visit(ast.getCondition()))) {
            try {
                scope = new Scope(scope);
                ast.getThenStatements().forEach(this::visit);
            }
            finally {
                scope = scope.getParent();
            }
        }
        else {
            try {
                scope = new Scope(scope);
                ast.getElseStatements().forEach(this::visit);
            }
            finally {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    //TODO
    public Environment.PlcObject visit(Ast.Statement.Switch ast) {
        Environment.PlcObject condition = visit(ast.getCondition());
        List<Ast.Statement.Case> cases = ast.getCases();

        for (int i = 0; i < ast.getCases().size() - 1; i++) {
            if (condition.getValue().equals(visit(cases.get(i).getValue().get()).getValue())) {
                return visit(cases.get(i));
            }
        }
        return visit(cases.get(cases.size() - 1));
    }

    @Override
    //TODO
    public Environment.PlcObject visit(Ast.Statement.Case ast) {
        try {
            scope = new Scope(scope);
            ast.getStatements().forEach(this::visit);
        }
        finally {
            scope = scope.getParent();
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {
        while (requireType(Boolean.class, visit(ast.getCondition()))) {
            try {
                scope = new Scope(scope);
                ast.getStatements().forEach(this::visit);
            }
            finally {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Return ast) {
        throw new Return(visit(ast.getValue()));
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {
        if (ast.getLiteral() == null) {
            return Environment.NIL;
        }
        else {
            return Environment.create(ast.getLiteral());
        }
    }

    @Override
    //TODO
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        return Environment.create(visit(ast.getExpression()).getValue());
    }

    @Override
    //TODO
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        Ast.Expression leftExpression = ast.getLeft();
        Ast.Expression rightExpression = ast.getRight();
        String operator = ast.getOperator();

        if (Objects.equals(operator, "&&") || Objects.equals(operator, "||")) {
            if (operator.equals("&&")) {
                if (requireType(Boolean.class, visit(leftExpression)) && requireType(Boolean.class, visit(rightExpression))) {
                    return Environment.create(true);
                }
                else {
                    return Environment.create(false);
                }
            }
            else {
                if (requireType(Boolean.class, visit(leftExpression))) {
                    return Environment.create(true);
                }
                else {
                    if (requireType(Boolean.class, visit(rightExpression))) {
                        return Environment.create(true);
                    }
                    else {
                        return Environment.create(false);
                    }
                }
            }
        }
        else if (Objects.equals(operator, "<") || Objects.equals(operator, ">")) {
            if (requireType(Comparable.class, visit(leftExpression)) == (visit(leftExpression).getValue())) {
                if (visit(leftExpression).getValue().getClass().equals(visit(rightExpression).getValue().getClass())) {
                    int result = 0;
                    if (visit(leftExpression).getValue() instanceof BigInteger) {
                        BigInteger left = (BigInteger) visit(leftExpression).getValue();
                        BigInteger right = (BigInteger) visit(rightExpression).getValue();
                        result = left.compareTo(right);
                    }
                    else {
                        BigDecimal left = (BigDecimal) visit(leftExpression).getValue();
                        BigDecimal right = (BigDecimal) visit(rightExpression).getValue();
                        result = left.compareTo(right);
                    }
                    if (Objects.equals(operator, "<")) {
                        if (result == -1) {
                            return Environment.create(true);
                        }
                        else {
                            return Environment.create(false);
                        }
                    }
                    else {
                        if (result == 1) {
                            return Environment.create(true);
                        }
                        else {
                            return Environment.create(false);
                        }
                    }
                }
                else {
                    throw new RuntimeException("Not the same class");
                }
            }
        }
        else if (Objects.equals(operator, "==") || Objects.equals(operator, "!=")) {
             if (Objects.equals(operator, "==")) {
                 if (Objects.equals(leftExpression, rightExpression)) {
                     return Environment.create(true);
                 }
                 else {
                     return Environment.create(false);
                 }
             }
             else {
                 if (Objects.equals(leftExpression, rightExpression)) {
                     return Environment.create(false);
                 }
                 else {
                     return Environment.create(true);
                 }
             }
        }
        else if (Objects.equals(operator, "+")) {
            if (visit(leftExpression).getValue() instanceof String
                    ||
                    visit(rightExpression).getValue() instanceof String) {
                String left = (String) visit(leftExpression).getValue();
                String right = (String) visit(rightExpression).getValue();
                return Environment.create(left + right);
            }
            else if (visit(leftExpression).getValue() instanceof BigInteger
                    &&
                    visit(rightExpression).getValue() instanceof BigInteger) {
                BigInteger left = (BigInteger) visit(leftExpression).getValue();
                BigInteger right = (BigInteger) visit(rightExpression).getValue();
                int result = left.intValue() + right.intValue();
                return Environment.create(BigInteger.valueOf(result));
            }
            else if (visit(leftExpression).getValue() instanceof BigDecimal
                    &&
                    visit(rightExpression).getValue() instanceof BigDecimal) {
                BigDecimal left = (BigDecimal) visit(leftExpression).getValue();
                BigDecimal right = (BigDecimal) visit(rightExpression).getValue();
                double result = left.doubleValue() + right.doubleValue();
                return Environment.create(BigDecimal.valueOf(result));
            }
            else {
                throw new RuntimeException("Different Class Types.");
            }
        }
        else if (Objects.equals(operator, "-") || Objects.equals(operator, "*")) {
            if (visit(leftExpression).getValue() instanceof BigInteger
                    &&
                    visit(rightExpression).getValue() instanceof BigInteger) {
                BigInteger left = (BigInteger) visit(leftExpression).getValue();
                BigInteger right = (BigInteger) visit(rightExpression).getValue();
                int result = 0;
                if (Objects.equals(operator, "-")) {
                    result = left.intValue() - right.intValue();
                }
                else {
                    result = left.intValue() * right.intValue();
                }
                return Environment.create(BigInteger.valueOf(result));
            }
            else if (visit(leftExpression).getValue() instanceof BigDecimal
                    &&
                    visit(rightExpression).getValue() instanceof BigDecimal) {
                BigDecimal left = (BigDecimal) visit(leftExpression).getValue();
                BigDecimal right = (BigDecimal) visit(rightExpression).getValue();
                double result = 0.0;
                if (Objects.equals(operator, "-")) {
                    result = left.doubleValue() - right.doubleValue();
                }
                else {
                    result = left.doubleValue() * right.doubleValue();
                }
                return Environment.create(BigDecimal.valueOf(result));
            }
            else {
                throw new RuntimeException("Different Class Types.");
            }
        }
        else if (Objects.equals(operator, "/")) {
            if (visit(leftExpression).getValue() instanceof BigInteger
                    &&
                    visit(rightExpression).getValue() instanceof BigInteger) {
                BigInteger left = (BigInteger) visit(leftExpression).getValue();
                BigInteger right = (BigInteger) visit(rightExpression).getValue();
                if (right.intValue() == 0) {
                    throw new RuntimeException("Denominator is zero");
                }
                int result = left.intValue() / right.intValue();
                return Environment.create(BigInteger.valueOf(result));
            }
            else if (visit(leftExpression).getValue() instanceof BigDecimal
                    &&
                    visit(rightExpression).getValue() instanceof BigDecimal) {
                BigDecimal left = (BigDecimal) visit(leftExpression).getValue();
                BigDecimal right = (BigDecimal) visit(rightExpression).getValue();
                if (right.doubleValue() == 0.0) {
                    throw new RuntimeException("Denominator is zero");
                }
                double result = left.doubleValue() / right.doubleValue();
                BigDecimal resultRounded = BigDecimal.valueOf(result);
                resultRounded = resultRounded.setScale(1, RoundingMode.HALF_EVEN);
                return Environment.create(resultRounded);
            }
            else {
                throw new RuntimeException("Different Class Types.");
            }
        }
        else if (Objects.equals(operator, "^")) {
            if (visit(rightExpression).getValue() instanceof BigInteger) {
                BigInteger right = (BigInteger) visit(rightExpression).getValue();
                if (visit(leftExpression).getValue() instanceof BigInteger) {
                    BigInteger left = (BigInteger) visit(leftExpression).getValue();
                    return Environment.create(Math.pow(left.intValue(), right.intValue()));
                }
                else if (visit(leftExpression).getValue() instanceof BigDecimal) {
                    BigDecimal left = (BigDecimal) visit(leftExpression).getValue();
                    return Environment.create(Math.pow(left.doubleValue(), right.intValue()));
                }
                else {
                    throw new RuntimeException("Not a number.");
                }
            }
            else {
                throw new RuntimeException("Exponent is not BigInteger.");
            }
        }

        return Environment.NIL;
    }

    @Override
    //TODO
    public Environment.PlcObject visit(Ast.Expression.Access ast) {
        if (ast.getOffset().isPresent()) {
            if (!(visit(ast.getOffset().get()).getValue() instanceof BigInteger)) {
                throw new RuntimeException("Not BigInteger Class.");
            }
            else {
                List<Object> list = (List<Object>) scope.lookupVariable(ast.getName()).getValue().getValue();
                BigInteger offset = (BigInteger) visit(ast.getOffset().get()).getValue();
                if (offset.intValue() >= list.size() || offset.intValue() < 0) {
                    throw new RuntimeException("Out of Bound.");
                }
                else {
                    return Environment.create(list.get(offset.intValue()));
                }
            }
        }
        else {
            return scope.lookupVariable(ast.getName()).getValue();
        }
    }

    @Override
    //TODO
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
        Environment.Function function = scope.lookupFunction(ast.getName(), ast.getArguments().size());
        List<Environment.PlcObject> newList = new ArrayList<Environment.PlcObject>();

        for (int i = 0; i < ast.getArguments().size(); i++) {
            newList.add(i, visit(ast.getArguments().get(i)));
        }

        return function.invoke(newList);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.PlcList ast) {
        List<Ast.Expression> plcList = ast.getValues();
        List<Object> newList = new ArrayList<Object>();
        for (int i = 0; i < plcList.size(); i++) {
            Ast.Expression.Literal temp = (Ast.Expression.Literal) plcList.get(i);
            newList.add(i, temp.getLiteral());
        }
        return Environment.create(newList);
    }
    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
