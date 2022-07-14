package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Function function;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        ast.getGlobals().forEach(this::visit);
        ast.getFunctions().forEach(this::visit);
        Environment.Function mainFunction = getScope().lookupFunction("main", 0);
        requireAssignable(Environment.Type.INTEGER, mainFunction.getReturnType());
        return null;
    }

    @Override
    public Void visit(Ast.Global ast) {
        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
        }
        ast.setVariable(getScope().defineVariable(ast.getName(), ast.getName(), Environment.getType(ast.getTypeName()), ast.getMutable(), Environment.NIL));
        if (ast.getValue().isPresent()) {
            requireAssignable(Environment.getType(ast.getTypeName()), ast.getValue().get().getType());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Function ast) {
        List<Environment.Type> parameterTypeNames = new ArrayList<>();
        Environment.Type returnType;
        for (int i = 0; i < ast.getParameterTypeNames().size(); i++) {
            parameterTypeNames.add(i, Environment.getType(ast.getParameterTypeNames().get(i)));
        }
        if (!ast.getReturnTypeName().isPresent()) {
            returnType = Environment.Type.NIL;
        }
        else {
            returnType = Environment.getType(ast.getReturnTypeName().get());
        }

        ast.setFunction(getScope().defineFunction(ast.getName(), ast.getName(), parameterTypeNames, returnType, args -> Environment.NIL));

        try {
            scope = new Scope(scope);
            for (int i = 0; i < ast.getParameters().size(); i++) {
                scope.defineVariable(ast.getParameters().get(i), ast.getParameters().get(i), parameterTypeNames.get(i), true, Environment.NIL);
            }
            function = ast;
            ast.getStatements().forEach(this::visit);
        }
        finally {
            scope = scope.getParent();
            function = null;
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        if (!(ast.getExpression() instanceof Ast.Expression.Function)) {
            throw new RuntimeException("Not an Ast.Expression.Function");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        Environment.Type type;
        if (ast.getTypeName().isPresent()) {
            type = Environment.getType(ast.getTypeName().get());
        }
        else if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            type = ast.getValue().get().getType();
        }
        else {
            throw new RuntimeException("No type");
        }

        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
        }
        ast.setVariable(getScope().defineVariable(ast.getName(), ast.getName(), type, true, Environment.NIL));
        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            requireAssignable(ast.getVariable().getType(), ast.getValue().get().getType());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        visit(ast.getReceiver());
        if (!(ast.getReceiver() instanceof Ast.Expression.Access)) {
            throw new RuntimeException("Not an access expression");
        }
        visit(ast.getValue());
        requireAssignable(ast.getReceiver().getType(), ast.getValue().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
        if (ast.getThenStatements().isEmpty()) {
            throw new RuntimeException("thenStatements is empty");
        }
        try {
            scope = new Scope(scope);
            ast.getThenStatements().forEach(this::visit);
            ast.getElseStatements().forEach(this::visit);
        }
        finally {
            scope = scope.getParent();
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        visit(ast.getCondition());
        List<Ast.Statement.Case> cases = ast.getCases();
        for (int i = 0; i < cases.size() - 1; i++)
        {
            visit(cases.get(i));
            requireAssignable(ast.getCondition().getType(), cases.get(i).getValue().get().getType());
        }
        if (cases.get(cases.size() - 1).getValue().isPresent())
            throw new RuntimeException("Default case should have no value specified");
        cases.forEach(this::visit);
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        try {
            scope = new Scope(scope);
            if (ast.getValue().isPresent()) {
                visit(ast.getValue().get());
            }
            ast.getStatements().forEach(this::visit);
        }
        finally {
            scope = scope.getParent();
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
        try {
            scope = new Scope(scope);
            ast.getStatements().forEach(this::visit);
        }
        finally {
            scope = scope.getParent();
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        visit(ast.getValue());
        requireAssignable(function.getFunction().getReturnType(), ast.getValue().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        if (ast.getLiteral() == null) {
            ast.setType(Environment.Type.NIL);
        }
        else if (ast.getLiteral() instanceof Boolean) {
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if (ast.getLiteral() instanceof Character) {
            ast.setType(Environment.Type.CHARACTER);
        }
        else if (ast.getLiteral() instanceof String) {
            ast.setType(Environment.Type.STRING);
        }
        else if (ast.getLiteral() instanceof BigInteger) {
            try {
                ((BigInteger) ast.getLiteral()).intValueExact();
                ast.setType(Environment.Type.INTEGER);
            }
            catch (ArithmeticException e) {
                throw new RuntimeException("Out of range integer");
            }
        }
        else if (ast.getLiteral() instanceof BigDecimal) {
            if (Double.isFinite(((BigDecimal) ast.getLiteral()).doubleValue())) {
                ast.setType(Environment.Type.DECIMAL);
            }
            else {
                throw new RuntimeException("Out of range decimal");
            }
        }
        else {
            throw new RuntimeException("Invalid literal type");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        visit(ast.getExpression());
        ast.setType(ast.getExpression().getType());
        if (!(ast.getExpression() instanceof Ast.Expression.Binary)) {
            throw new RuntimeException("Not a binary expression");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        visit(ast.getLeft());
        visit(ast.getRight());
        if (ast.getOperator().equals("&&") || ast.getOperator().equals("||")) {
            requireAssignable(Environment.Type.BOOLEAN, ast.getLeft().getType());
            requireAssignable(Environment.Type.BOOLEAN, ast.getRight().getType());
            ast.setType(Environment.Type.BOOLEAN);
        }
        if (ast.getOperator().equals(">") || ast.getOperator().equals("<") || ast.getOperator().equals("!=") || ast.getOperator().equals("==")) {
            if (ast.getRight().getType().equals(ast.getLeft().getType())) {
                requireAssignable(Environment.Type.COMPARABLE, ast.getLeft().getType());
                ast.setType(Environment.Type.BOOLEAN);
            }
            else {
                throw new RuntimeException("Not the same type on both sides");
            }
        }
        if (ast.getOperator().equals("+")) {
            if (ast.getLeft().getType().equals(Environment.Type.STRING) || ast.getRight().getType().equals(Environment.Type.STRING)) {
                ast.setType(Environment.Type.STRING);
            } else if (ast.getLeft().getType().equals(ast.getRight().getType())) {
                if (ast.getLeft().getType().equals(Environment.Type.INTEGER))
                    ast.setType(Environment.Type.INTEGER);
                else if (ast.getLeft().getType().equals(Environment.Type.DECIMAL))
                    ast.setType(Environment.Type.DECIMAL);
                else {
                    throw new RuntimeException("Not a valid + operation");
                }
            }
            else {
                throw new RuntimeException("Left and Right not the same type");
            }
        }
        if (ast.getOperator().equals("-") || ast.getOperator().equals("/") || ast.getOperator().equals("*")) {
            if (ast.getLeft().getType().equals(ast.getRight().getType())) {
                if (ast.getLeft().getType().equals(Environment.Type.INTEGER))
                    ast.setType(Environment.Type.INTEGER);
                else if (ast.getLeft().getType().equals(Environment.Type.DECIMAL))
                    ast.setType(Environment.Type.DECIMAL);
                else
                {
                    throw new RuntimeException("Not a valid -/* operation");
                }
            }
            else {
                throw new RuntimeException("Left and Right not the same type");
            }
        }
        if (ast.getOperator().equals("^")) {
            requireAssignable(Environment.Type.INTEGER, ast.getRight().getType());
            if (ast.getLeft().getType().equals(Environment.Type.INTEGER)) {
                ast.setType(Environment.Type.INTEGER);
            }
            else if (ast.getLeft().getType().equals(Environment.Type.DECIMAL)) {
                ast.setType(Environment.Type.DECIMAL);
            }
            else {
                throw new RuntimeException("Not a valid ^ operation");
            }
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        ast.setVariable(getScope().lookupVariable(ast.getName()));
        if (ast.getOffset().isPresent()) {
            visit(ast.getOffset().get());
            requireAssignable(Environment.Type.INTEGER, ast.getOffset().get().getType());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        ast.setFunction(getScope().lookupFunction(ast.getName(), ast.getArguments().size()));
        for (int i = 0; i < ast.getArguments().size(); i++) {
            visit(ast.getArguments().get(i));
            requireAssignable(ast.getFunction().getParameterTypes().get(i), ast.getArguments().get(i).getType());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        ast.getValues().forEach(this::visit);
        ast.setType(ast.getValues().get(0).getType());
        for (int i = 0; i < ast.getValues().size(); i++) {
            requireAssignable(ast.getType(), ast.getValues().get(i).getType());
        }
        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        if (type.equals(Environment.Type.ANY) || type.equals(Environment.Type.NIL) || type.equals(Environment.Type.COMPARABLE) || type.equals(Environment.Type.BOOLEAN)
                || type.equals(Environment.Type.INTEGER) || type.equals(Environment.Type.DECIMAL) || type.equals(Environment.Type.CHARACTER) || type.equals(Environment.Type.STRING)) {
            if (target.equals(type)) {
                // Do nothing, valid
            }
            else if (target.equals(Environment.Type.ANY)) {
                // Do nothing valid
            }
            else if (target.equals(Environment.Type.COMPARABLE) && !type.equals(Environment.Type.ANY) && !type.equals(Environment.Type.NIL) && !type.equals(Environment.Type.BOOLEAN)) {
                // Do nothing valid
            }
            else {
                throw new RuntimeException("Invalid target type");
            }
        }
        else {
            throw new RuntimeException("Invalid type name");
        }
    }

}
