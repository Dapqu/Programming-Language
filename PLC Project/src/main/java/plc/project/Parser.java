package plc.project;

import javax.swing.plaf.nimbus.State;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.StampedLock;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        List<Ast.Global> globals = new ArrayList<Ast.Global>();
        List<Ast.Function> functions = new ArrayList<Ast.Function>();

        boolean parsedFunction = false;

        int i = 0, j = 0;

        while (peek("LIST") || peek("VAR") || peek("VAL") || peek("FUN")) {
            if (!match("FUN")) {
                if (!parsedFunction) {
                    globals.add(i++, parseGlobal());
                }
                else {
                    if (tokens.has(0)) {
                        throw new ParseException("Parsed Function before Global at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                    }
                    else {
                        throw new ParseException("Parsed Function before Global at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    }
                }
            }
            else {
                functions.add(j++, parseFunction());
                parsedFunction = true;
            }
            if (peek("\n")) {
                tokens.advance();
            }
        }

        return new Ast.Source(globals, functions);
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Global parseGlobal() throws ParseException {
        Ast.Global global = null;

        if (match("LIST")) {
            global = parseList();
        }
        else if (match("VAR")) {
            global = parseMutable();
        }
        else if (match("VAL")) {
            global = parseImmutable();
        }

        if (match(";")) {
            return global;
        }
        else {
            if (tokens.has(0)) {
                throw new ParseException("Missing ; at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
            }
            else {
                throw new ParseException("Missing ; at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        }
    }

    /**
     * Parses the {@code list} rule. This method should only be called if the
     * next token declares a list, aka {@code LIST}.
     */
    public Ast.Global parseList() throws ParseException {
        if (match(Token.Type.IDENTIFIER)) {
            String name = tokens.get(-1).getLiteral();
            String type = "";
            if (match (":", Token.Type.IDENTIFIER)) {
                type = tokens.get(-1).getLiteral();
            }
            else {
                throw new ParseException("No identifier specified", tokens.get(0).getIndex());
            }
            if (match("=")) {
                if (match("[")) {
                    List<Ast.Expression> exprList = new ArrayList<Ast.Expression>();
                    int index = 0;
                    while (!peek("]")) {
                        exprList.add(index++, parseExpression());
                        while (match(",")) {
                            if (tokens.has(0) && !peek("]")) {
                                exprList.add(index++, parseExpression());
                            }
                            else {
                                if (tokens.has(0)) {
                                    throw new ParseException("Tailing Comma at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                                }
                                else {
                                    throw new ParseException("Tailing Comma at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                                }
                            }
                        }
                    }
                    if (match("]")) {
                        Ast.Expression.PlcList list = new Ast.Expression.PlcList(exprList);
                        return new Ast.Global(name, type,true, Optional.of(list));
                    }
                    else {
                        if (tokens.has(0)) {
                            throw new ParseException("Missing Closing ] at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                        }
                        else {
                            throw new ParseException("Missing Closing ] at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                        }
                    }
                }
                else {
                    if (tokens.has(0)) {
                        throw new ParseException("Missing [ at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                    }
                    else {
                        throw new ParseException("Missing [ at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    }
                }
            }
            else {
                if (tokens.has(0)) {
                    throw new ParseException("Missing Equal Sign at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                }
                else {
                    throw new ParseException("Missing Equal Sign at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
            }
        }
        else {
            if (tokens.has(0)) {
                throw new ParseException("Missing name at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
            }
            else {
                throw new ParseException("Missing name at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        }
    }

    /**
     * Parses the {@code mutable} rule. This method should only be called if the
     * next token declares a mutable global variable, aka {@code VAR}.
     */
    public Ast.Global parseMutable() throws ParseException {
        if (match(Token.Type.IDENTIFIER)) {
            String name = tokens.get(-1).getLiteral();
            String type = "";
            if (match (":", Token.Type.IDENTIFIER)) {
                type = tokens.get(-1).getLiteral();
            }
            else {
                throw new ParseException("No identifier specified", tokens.get(0).getIndex());
            }
            if (match("=")){
                Ast.Expression expr = parseExpression();
                return new Ast.Global(name, type, true, Optional.of(expr));
            }
            else {
                return new Ast.Global(name, type, true, Optional.empty());
            }
        }
        else {
            if (tokens.has(0)) {
                throw new ParseException("Missing name at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
            }
            else {
                throw new ParseException("Missing name at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        }
    }

    /**
     * Parses the {@code immutable} rule. This method should only be called if the
     * next token declares an immutable global variable, aka {@code VAL}.
     */
    public Ast.Global parseImmutable() throws ParseException {
        if (match(Token.Type.IDENTIFIER)) {
            String name = tokens.get(-1).getLiteral();
            String type = "";
            if (match (":", Token.Type.IDENTIFIER)) {
                type = tokens.get(-1).getLiteral();
            }
            else {
                throw new ParseException("No identifier specified", tokens.get(0).getIndex());
            }
            if (match("=")){
                Ast.Expression expr = parseExpression();
                return new Ast.Global(name, type, false, Optional.of(expr));
            }
            else {
                if (tokens.has(0)) {
                    throw new ParseException("Missing = at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                }
                else {
                    throw new ParseException("Missing = at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
            }
        }
        else {
            if (tokens.has(0)) {
                throw new ParseException("Missing name at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
            }
            else {
                throw new ParseException("Missing name at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        }
    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Function parseFunction() throws ParseException {
        if (match(Token.Type.IDENTIFIER)) {
            String name = tokens.get(-1).getLiteral();
            if (match("(")) {
                List<String> strings = new ArrayList<String>();
                List<String> types = new ArrayList<String>();
                int index = 0;
                int identifierIndex = 0;
                while (!peek(")") && tokens.has(0) && !peek("DO")) {
                    strings.add(index++, tokens.get(0).getLiteral());
                    tokens.advance();
                    if (match (":", Token.Type.IDENTIFIER)) {
                        types.add(identifierIndex++, tokens.get(-1).getLiteral());
                    }
                    else {
                        throw new ParseException("No identifier specified", tokens.get(0).getIndex());
                    }
                    while (match(",")) {
                        if (tokens.has(0) && !peek(")")) {
                            strings.add(index++, tokens.get(0).getLiteral());
                            tokens.advance();
                            if (match (":", Token.Type.IDENTIFIER)) {
                                types.add(identifierIndex++, tokens.get(-1).getLiteral());
                            }
                            else {
                                throw new ParseException("No identifier specified", tokens.get(0).getIndex());
                            }
                        }
                        else {
                            if (tokens.has(0)) {
                                throw new ParseException("Tailing Comma at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                            }
                            else {
                                throw new ParseException("Tailing Comma at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                            }
                        }
                    }
                }
                if (match(")")) {
                    String type = "";
                    Boolean ident = false;
                    if (match (":", Token.Type.IDENTIFIER)) {
                        ident = true;
                        type = tokens.get(-1).getLiteral();
                    }
                    if (match("DO")) {
                        List<Ast.Statement> statements = parseBlock();
                        if (match("END")) {
                            return new Ast.Function(name, strings, types, Optional.of(type), statements);
                        }
                        else {
                            if (tokens.has(0)) {
                                throw new ParseException("Missing END at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                            }
                            else {
                                throw new ParseException("Missing END at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                            }
                        }
                    }
                    else {
                        if (tokens.has(0)) {
                            throw new ParseException("Missing Do at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                        }
                        else {
                            throw new ParseException("Missing DO at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                        }
                    }
                }
                else {
                    if (tokens.has(0)) {
                        throw new ParseException("Missing ) at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                    }
                    else {
                        throw new ParseException("Missing ) at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    }
                }
            }
            else {
                if (tokens.has(0)) {
                    throw new ParseException("Missing ( at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                }
                else {
                    throw new ParseException("Missing ( at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
            }
        }
        else {
            if (tokens.has(0)) {
                throw new ParseException("Missing Name at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
            }
            else {
                throw new ParseException("Missing Name at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        }
    }

    /**
     * Parses the {@code block} rule. This method should only be called if the
     * preceding token indicates the opening a block.
     */
    public List<Ast.Statement> parseBlock() throws ParseException {
        List<Ast.Statement> stmtList = new ArrayList<Ast.Statement>();
        int index = 0;

        while (tokens.has(0) && !peek("END") && !peek("ELSE") && !peek("CASE") && !peek("DEFAULT")) {
            stmtList.add(index++, parseStatement());
        }

        return stmtList;
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Statement parseStatement() throws ParseException {
        if (match("LET")) {
            return parseDeclarationStatement();
        }
        else if (match("IF")) {
            return parseIfStatement();
        }
        else if (match("SWITCH")) {
            return parseSwitchStatement();
        }
        else if (match("WHILE")) {
            return parseWhileStatement();
        }
        else if (match("RETURN")) {
            return parseReturnStatement();
        }
        else {
            Ast.Expression receiver = parseExpression();
            Ast.Expression value = null;

            if (match("=")) {
                if (tokens.has(0) && !peek(";")) {
                    value = parseExpression();
                }
                else {
                    if (tokens.has(0)) {
                        throw new ParseException("Missing Value at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                    }
                    else {
                        throw new ParseException("Missing Value at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    }
                }
            }

            if (match(";")) {
                if (value == null) {
                    return new Ast.Statement.Expression(receiver);
                }
                else {
                    return new Ast.Statement.Assignment(receiver, value);
                }
            }
            else {
                if (tokens.has(0)) {
                    throw new ParseException("Missing Semicolon at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                }
                else {
                    throw new ParseException("Missing Semicolon at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
            }
        }
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {
        if (match(Token.Type.IDENTIFIER)) {
            String name = tokens.get(-1).getLiteral();
            Boolean ident = false;
            String type = "";
            if (match (":", Token.Type.IDENTIFIER)) {
                type = tokens.get(-1).getLiteral();
                ident = true;
            }
            Ast.Expression expr = null;

            if (match("=")) {
                expr = parseExpression();
            }

            if (match(";")) {
                if (expr == null && ident) {
                    return new Ast.Statement.Declaration(name, Optional.of(type),Optional.empty());
                }
                else if (expr == null && !ident) {
                    return new Ast.Statement.Declaration(name, Optional.empty(),Optional.empty());
                }
                else if (ident)  {
                    return new Ast.Statement.Declaration(name, Optional.of(type),Optional.of(expr));
                }
                else {
                    return new Ast.Statement.Declaration(name, Optional.empty(), Optional.of(expr));
                }
            }
            else {
                if (tokens.has(0)) {
                    throw new ParseException("Missing Semicolon at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                }
                else {
                    throw new ParseException("Missing Semicolon at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
            }
        }
        else {
            if (tokens.has(0)) {
                throw new ParseException("Not Identifier at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
            }
            else {
                throw new ParseException("Not Identifier at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        }
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Statement.If parseIfStatement() throws ParseException {
        List<Ast.Statement> thenList = new ArrayList<Ast.Statement>();
        List<Ast.Statement> elseList = new ArrayList<Ast.Statement>();

        Ast.Expression expr = parseExpression();

        if (match("DO")) {
            thenList = parseBlock();
            if (match("ELSE")) {
                elseList = parseBlock();
            }
            if (match("END")) {
                return new Ast.Statement.If(expr, thenList, elseList);
            }
            else {
                if (tokens.has(0)) {
                    throw new ParseException("Missing END at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                }
                else {
                    throw new ParseException("Missing END at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
            }
        }
        else {
            if (tokens.has(0)) {
                throw new ParseException("Missing DO at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
            }
            else {
                throw new ParseException("Missing DO at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        }
    }

    /**
     * Parses a switch statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a switch statement, aka
     * {@code SWITCH}.
     */
    public Ast.Statement.Switch parseSwitchStatement() throws ParseException {
        List<Ast.Statement.Case> cases = new ArrayList<Ast.Statement.Case>();
        int index = 0;

        Ast.Expression condition = parseExpression();

        while (peek("CASE")) {
            cases.add(index++, parseCaseStatement());
        }

        if (peek("DEFAULT")) {
            cases.add(index++, parseCaseStatement());
            if (match("END")) {
                return new Ast.Statement.Switch(condition, cases);
            }
            else {
                if (tokens.has(0)) {
                    throw new ParseException("Missing END at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                }
                else {
                    throw new ParseException("Missing END at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
            }
        }
        else {
            if (tokens.has(0)) {
                throw new ParseException("Missing DEFAULT at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
            }
            else {
                throw new ParseException("Missing DEFAULT at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        }
    }

    /**
     * Parses a case or default statement block from the {@code switch} rule.
     * This method should only be called if the next tokens start the case or
     * default block of a switch statement, aka {@code CASE} or {@code DEFAULT}.
     */
    public Ast.Statement.Case parseCaseStatement() throws ParseException {
        List<Ast.Statement> statements = new ArrayList<Ast.Statement>();

        if (match("CASE")) {
            Ast.Expression expr = parseExpression();
            if (match(":")) {
                statements = parseBlock();
                return new Ast.Statement.Case(Optional.of(expr), statements);
            }
            else {
                if (tokens.has(0)) {
                    throw new ParseException("Missing : at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                }
                else {
                    throw new ParseException("Missing : at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
            }
        }

        if (match("DEFAULT")) {
            statements = parseBlock();
            return new Ast.Statement.Case(Optional.empty(), statements);
        }
        else {
            if (tokens.has(0)) {
                throw new ParseException("Missing DEFAULT at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
            }
            else {
                throw new ParseException("Missing DEFAULT at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        }
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Statement.While parseWhileStatement() throws ParseException {
        List<Ast.Statement> stmtList = new ArrayList<Ast.Statement>();
        int index = 0;

        Ast.Expression expr = parseExpression();

        if (match("DO")) {
            stmtList = parseBlock();
            if (match("END")) {
                return new Ast.Statement.While(expr, stmtList);
            }
            else {
                if (tokens.has(0)) {
                    throw new ParseException("Missing END at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                }
                else {
                    throw new ParseException("Missing END at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
            }
        }
        else {
            if (tokens.has(0)) {
                throw new ParseException("Missing DO at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
            }
            else {
                throw new ParseException("Missing DO at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        }
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Statement.Return parseReturnStatement() throws ParseException {
        Ast.Expression expr = parseExpression();

        if (match(";")) {
            return new Ast.Statement.Return(expr);
        }
        else {
            if (tokens.has(0)) {
                throw new ParseException("Missing Semicolon at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
            }
            else {
                throw new ParseException("Missing Semicolon at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        }
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expression parseExpression() throws ParseException {
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expression parseLogicalExpression() throws ParseException {
        Ast.Expression expr = parseComparisonExpression();

        while (match("&&") || match("||")) {
            Token operator = tokens.get(-1);
            Ast.Expression right = parseComparisonExpression();
            expr = new Ast.Expression.Binary(operator.getLiteral(), expr, right);
        }

        return expr;
    }

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expression parseComparisonExpression() throws ParseException {
        Ast.Expression expr = parseAdditiveExpression();

        while (match("<") || match(">") || match("==") || match("!=")) {
            Token operator = tokens.get(-1);
            Ast.Expression right = parseAdditiveExpression();
            expr = new Ast.Expression.Binary(operator.getLiteral(), expr, right);
        }

        return expr;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expression parseAdditiveExpression() throws ParseException {
        Ast.Expression expr = parseMultiplicativeExpression();

        while (match("+") || match("-")) {
            Token operator = tokens.get(-1);
            Ast.Expression right = parseMultiplicativeExpression();
            expr = new Ast.Expression.Binary(operator.getLiteral(), expr, right);
        }

        return expr;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expression parseMultiplicativeExpression() throws ParseException {
        Ast.Expression expr = parsePrimaryExpression();

        while (match("*") || match("/") || match("^")) {
            Token operator = tokens.get(-1);
            Ast.Expression right = parsePrimaryExpression();
            expr = new Ast.Expression.Binary(operator.getLiteral(), expr, right);
        }

        return expr;
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expression parsePrimaryExpression() throws ParseException {
        if (match("NIL")) {
            return new Ast.Expression.Literal(null);
        }
        if (match("TRUE")) {
            return new Ast.Expression.Literal(Boolean.TRUE);
        }
        if (match("FALSE")) {
            return new Ast.Expression.Literal(Boolean.FALSE);
        }
        if (match(Token.Type.INTEGER)) {
            return new Ast.Expression.Literal(new BigInteger(tokens.get(-1).getLiteral()));
        }
        if (match(Token.Type.DECIMAL)) {
            return new Ast.Expression.Literal(new BigDecimal(tokens.get(-1).getLiteral()));
        }
        if (match(Token.Type.CHARACTER)) {
            String newChar = tokens.get(-1).getLiteral();

            newChar = newChar.replace("\\b", "\b");
            newChar = newChar.replace("\\n", "\n");
            newChar = newChar.replace("\\r", "\r");
            newChar = newChar.replace("\\t", "\t");
            newChar = newChar.replace("\\\'", "\'");
            newChar = newChar.replace("\\\"", "\"");
            newChar = newChar.replace("\\\\", "\\");

            return new Ast.Expression.Literal(newChar.charAt(1));
        }
        if (match(Token.Type.STRING)) {
            String output = tokens.get(-1).getLiteral();
            output = output.substring(1, output.length() - 1);

            output = output.replace("\\b", "\b");
            output = output.replace("\\n", "\n");
            output = output.replace("\\r", "\r");
            output = output.replace("\\t", "\t");
            output = output.replace("\\\'", "\'");
            output = output.replace("\\\"", "\"");
            output = output.replace("\\\\", "\\");

            return new Ast.Expression.Literal(output);
        }
        if (match("(")) {
            Ast.Expression expr = parseExpression();
            if (match(")")) {
                return new Ast.Expression.Group(expr);
            }
            else {
                if (tokens.has(0)) {
                    throw new ParseException("Missing Closing Parenthesis at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                }
                else {
                    throw new ParseException("Missing Closing Parenthesis at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
            }
        }
        if (match(Token.Type.IDENTIFIER)) {
            String name = tokens.get(-1).getLiteral();
            if (match("(")) {
                List<Ast.Expression> exprList = new ArrayList<Ast.Expression>();
                int index = 0;
                while (!peek(")")) {
                    exprList.add(index++, parseExpression());
                    while (match(",")) {
                        if (tokens.has(0) && !peek(")")) {
                            exprList.add(index++, parseExpression());
                        }
                        else {
                            if (tokens.has(0)) {
                                throw new ParseException("Tailing Comma at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                            }
                            else {
                                throw new ParseException("Tailing Comma at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                            }
                        }
                    }
                }
                if (match(")")) {
                    return new Ast.Expression.Function(name, exprList);
                }
                else {
                    if (tokens.has(0)) {
                        throw new ParseException("Missing Closing Parenthesis at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                    }
                    else {
                        throw new ParseException("Missing Closing Parenthesis at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    }
                }
            }
            else if (match("[")) {
                Ast.Expression expr = parseExpression();
                if (match("]")) {
                    return new Ast.Expression.Access(Optional.of(expr), name);
                }
                else {
                    if (tokens.has(0)) {
                        throw new ParseException("Missing Closing Square Bracket at index " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
                    }
                    else {
                        throw new ParseException("Missing Closing Square Bracket at index " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    }
                }
            }
            else {
                return new Ast.Expression.Access(Optional.empty(), name);
            }
        }

        if (tokens.has(0)) {
            throw new ParseException("Missing Token " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
        }
        else {
            throw new ParseException("Missing Token " + (tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length()), tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
        }
    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!tokens.has(i)) {
                return false;
            }
            else if (patterns[i] instanceof Token.Type) {
                if (patterns[i] != tokens.get(i).getType()) {
                    return false;
                }
            }
            else if (patterns[i] instanceof String) {
                if (!patterns[i].equals(tokens.get(i).getLiteral())) {
                    return false;
                }
            }
            else {
                throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        boolean peek = peek(patterns);

        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                tokens.advance();
            }
        }
        return peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}