package plc.project;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        print("public class Main {");
        newline(indent);

        if (!ast.getGlobals().isEmpty()) {
            newline(++indent);
            for (int i = 0; i < ast.getGlobals().size(); i++) {
                if (i != 0) {
                    newline(indent);
                }
                print(ast.getGlobals().get(i));
            }
            newline(--indent);
        }

        newline(++indent);

        print("public static void main(String[] args) {");
        newline(++indent);
        print("System.exit(new Main().main());");
        newline(--indent);
        print("}");
        newline(--indent);

        if (!ast.getFunctions().isEmpty()) {
            newline(++indent);
            for (int i = 0; i < ast.getFunctions().size(); i++) {
                if (i != 0) {
                    newline(--indent);
                    newline(++indent);
                }
                print(ast.getFunctions().get(i));
            }
            newline(--indent);
        }

        newline(indent);

        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Global ast) {
        if (!ast.getMutable()) {
            print("final ");
        }

        if (ast.getValue().isPresent() && ast.getValue().get() instanceof Ast.Expression.PlcList) {
            print(ast.getVariable().getType().getJvmName(), "[] ", ast.getVariable().getJvmName());
        }
        else {
            print(ast.getVariable().getType().getJvmName(), " ", ast.getVariable().getJvmName());
        }

        if (ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }

        print(";");

        return null;
    }

    @Override
    public Void visit(Ast.Function ast) {
        print(ast.getFunction().getReturnType().getJvmName(), " ", ast.getFunction().getJvmName(), "(");
        for (int i = 0; i < ast.getFunction().getArity(); i++) {
            if (i != 0) {
                print(", ");
            }
            print(ast.getFunction().getParameterTypes().get(i).getJvmName(), " " ,ast.getParameters().get(i));
        }
        print(") {");

        if (!ast.getStatements().isEmpty()) {
            block(ast.getStatements());
        }

        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        print(ast.getExpression(), ";");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        print(ast.getVariable().getType().getJvmName(), " ", ast.getVariable().getJvmName());

        if (ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }

        print(";");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        print(ast.getReceiver(), " = ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        print("if (", ast.getCondition(), ") {");

        if (!ast.getThenStatements().isEmpty()) {
            block(ast.getThenStatements());
        }

        print("}");

        if (!ast.getElseStatements().isEmpty()) {
            print(" else {");
            block(ast.getElseStatements());
            print("}");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        print("switch (", ast.getCondition(), ") {");

        if (!ast.getCases().isEmpty()) {
            newline(++indent);
            for (int i = 0; i < ast.getCases().size(); i++) {
                print(ast.getCases().get(i));
            }
            indent = indent - 2;
            newline(indent);
        }

        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        if (ast.getValue().isPresent()) {
            print("case ", ast.getValue().get(), ":");
            if (!ast.getStatements().isEmpty()) {
                block(ast.getStatements());
            }
        }
        else {
            print("default:");
            if (!ast.getStatements().isEmpty()) {
                newline(++indent);
                for (int i = 0; i < ast.getStatements().size(); i++) {
                    if (i != 0) {
                        newline(indent);
                    }
                    print(ast.getStatements().get(i));
                }
            }
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        print("while (", ast.getCondition(), ") {");

        if (!ast.getStatements().isEmpty()) {
            block(ast.getStatements());
        }

        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        print("return ", ast.getValue(), ";");

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        if (ast.getType().equals(Environment.Type.STRING)) {
            print("\"", ast.getLiteral(), "\"");
        } else if (ast.getType().equals(Environment.Type.CHARACTER)) {
            print("'", ast.getLiteral(), "'");
        }
        else if (ast.getType().equals(Environment.Type.NIL)){
            print("null");
        } else {
            print(ast.getLiteral());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        print("(", ast.getExpression(), ")");

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        if (ast.getOperator().equals("^")) {
            print("Math.pow(", ast.getLeft(), ", ", ast.getRight(), ")");
        }
        else {
            print(ast.getLeft(), " ", ast.getOperator(), " ", ast.getRight());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        if (ast.getOffset().isPresent()) {
            print(ast.getVariable().getJvmName(), "[", ast.getOffset().get(), "]");
        }
        else {
            print(ast.getVariable().getJvmName());
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        print(ast.getFunction().getJvmName(), "(");

        for (int i = 0; i < ast.getArguments().size(); i++) {
            if (i != 0) {
                print(", ");
            }
            print(ast.getArguments().get(i));
        }

        print(")");

        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        print("{");

        for (int i = 0; i < ast.getValues().size(); i++) {
            if (i != 0) {
                print(", ");
            }
            print(ast.getValues().get(i));
        }

        print("}");

        return null;
    }

    public void block(List<Ast.Statement> statements) {
        newline(++indent);
        for (int i = 0; i < statements.size(); i++) {
            if (i != 0) {
                newline(indent);
            }
            print(statements.get(i));
        }
        newline(--indent);
    }
}
