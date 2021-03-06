package jlox;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import static jlox.TokenType.*;

/**************************************************************
* program       → declaration* EOF ;
* declaration   → | varDecl
*                 | classDecl
*                 | funDecl
*                 | statement ;
* varDecl       → "var" IDENTIFIER ( "=" expression )? ";" ;
* classDecl     → "class" IDENTIFIER ( "<" IDENTIFIER )? "{" methodDecl* "}" ;
* methodDecl    → IDENTIFIER funParams ;
* funDecl       → "fun" IDENTIFIER funParams ;
* funParams     → "(" parameters? ")" block ;
* parameters    → IDENTIFIER ( "," IDENTIFIER )* ;
* statement     → | exprStmt
*                 | ifStmt
*                 | printStmt
*                 | returnStmt
*                 | whileStmt
*                 | forStmt
*                 | block ;
* exprStmt      → expression ";" ;
* ifStmt        → "if" "(" expression ")" statement ( "else" statement )? ;
* printStmt     → "print" expression ";" ;
* returnStmt    → "return" expression? ";" ;
* whileStmt     → "while" "(" expression ")" statement ;
* forStmt       → "for" "(" ( varDecl | exprStmt | ";" ) expression? ";" expression? ")" statement ;
* block         → "{" declaration* "}" ;
* expression    → assignment ;
* assignment    → | ( call "." )? IDENTIFIER "=" assignment
*                 | ternary ;
* ternary       → logic_or ( "?" expression ":" expression )? ;
* logic_or      → logic_and ( "or" logic_and )* ;
* logic_and     → equality ( "and" equality )* ;
* equality      → comparison ( ( "!=" | "==" ) comparison )* ;
* comparison    → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
* term          → factor ( ( "-" | "+" ) factor )* ;
* factor        → unary ( ( "/" | "*" ) unary )* ;
* unary         → | ( "!" | "-" ) unary
*                 | call ;
* call          → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;
* arguments     → expression ( "," expression )* ;
* primary       → | NUMBER | STRING | "true" | "false" | "nil"
*                 | "(" expression ")" | IDENTIFIER | lambda 
*                 | "super" "." IDENTIFIER ;
* lambda        → "fun" funParams ;
***************************************************************/

class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();
            if (match(CLASS)) return classDeclaration();
            if (check(FUN) && check2(IDENTIFIER)) {
                advance();  // Eat the FUN, let function eat the ID.
                            // If we were to match FUN, lambdas wouldn't
                            // be found later.
                return function("function");
            }
            return statement();
        } catch (ParseError error) {
            syncronize();
            return null;
        }
    }

    private Stmt classDeclaration() {
        Token name = consume(IDENTIFIER, "Expected class name.");

        Expr.Variable superclass = null;
        if (match(LESS)) {
            consume(IDENTIFIER, "Expected superclass name.");
            superclass = new Expr.Variable(previous());
        }

        consume(LEFT_BRACE, "Expected '{' before class body.");

        List<Stmt.Function> methods = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            methods.add(function("method"));
        }

        consume(RIGHT_BRACE, "Expected '}' after class body.");
        return new Stmt.Class(name, superclass, methods);
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expected variable name.");
        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expected ';' at end of variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt statement() {
        if (match(PRINT     )) return printStatement();
        if (match(RETURN    )) return returnStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        if (match(IF        )) return ifStatement();
        if (match(WHILE     )) return whileStatement();
        if (match(FOR       )) return forStatement();
        return expressionStatement();
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expected ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }

        consume(SEMICOLON, "Expected ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expected ';' after expression.");
        return new Stmt.Expression(expr);
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expected '(' after if.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expected ')' after condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expected '(' after while.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expected ')' after condition.");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expected ')' after for.");

        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null; // initializer omitted
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression(); // condition *not* omitted
        }
        consume(SEMICOLON, "Expected ';' after condition.");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expected ')' after for clauses.");

        Stmt body = statement();

        if (increment != null) {
            // Add increment expression to the end of the block
            body = new Stmt.Block(
                Arrays.asList(
                    body,
                    new Stmt.Expression(increment)
                )
            );
        }

        // Slap the body and the condition statement together in a while statement
        if (condition == null) condition =  new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        // Add the initializer before the while body
        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    private Stmt.Function function(String kind) {
        Token name = consume(IDENTIFIER, "Expected " + kind + "name.");
        consume(LEFT_PAREN, "Expected '(' after " + kind + " name.");
        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    error(peek(), "Can't have more than 255 parameters");
                }
                parameters.add(consume(IDENTIFIER, "Expected parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expected ')' after parameters.");
        consume(LEFT_BRACE, "Expected '{' before " + kind + " body.");
        List<Stmt> body = block();
        return new Stmt.Function(name, parameters, body);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expected '}' after block.");
        return statements;
    }

	private Expr expression() {
        return assignment();
	}

    private Expr assignment() {
        Expr expr = ternary();
        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            } else if (expr instanceof Expr.Get) {
                Expr.Get get = (Expr.Get)expr;
                return new Expr.Set(get.object, get.name, value);
            }

            error(equals, "Invalid assignment target");
        }

        return expr;
    }

    private Expr ternary() {
        Expr expr = or();

        if (match(QUEST)) {
            Expr truePath = expression();
            consume(COLON, "Expected ':' after true path of ternary operator.");
            Expr falsePath = expression();
            expr = new Expr.Ternary(expr, truePath, falsePath);
        }

        return expr;
    }

    private Expr or() {
        Expr expr = and();
        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }
        return expr;
    }

    private Expr and() {
        Expr expr = equality();
        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }
        return expr;
        
    }

	private Expr equality() {
		Expr expr = comparison();
		
		while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
		}

        return expr;
	}

    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return call();
    }

    // Treat calls like infix operation.
    // Left side is primary, right side is arguments to func. call, object fields, etc.
    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(DOT)) {
                Token name = consume(IDENTIFIER, "Expected property name after '.'.");
                expr = new Expr.Get(expr, name);
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 255) {
                    error(peek(), "Can't have more than 255 arguments.");
                }
                arguments.add(expression());
            } while (match(COMMA));
        }

        // Save parenthesis token for when we want to mark errors originating in a function call
        Token paren = consume(RIGHT_PAREN, "Expected ')' after function call arguments.");
        return new Expr.Call(callee, paren, arguments);
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE )) return new Expr.Literal(true );
        if (match(NIL  )) return new Expr.Literal(null );
        if (match(THIS )) return new Expr.This(previous());

        if (match(STRING, NUMBER))
            return new Expr.Literal(previous().literal);
        if (match(IDENTIFIER))
            return new Expr.Variable(previous());

        if (match(SUPER)){
            Token keyword = previous();
            consume(DOT, "Expected '.' after 'super'.");
            Token method = consume(IDENTIFIER, "Expected method name.");
            return new Expr.Super(keyword, method);
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expected ')' after expression.");
            return new Expr.Grouping(expr);
        }

        if (match(FUN)) {
            consume(LEFT_PAREN, "Expected '(' after lambda 'fun' token.");
            List<Token> parameters = new ArrayList<>();
            if (!check(RIGHT_PAREN)) {
                do {
                    if (parameters.size() >= 255) {
                        error(peek(), "Can't have more than 255 parameters");
                    }
                    parameters.add(consume(IDENTIFIER, "Expected parameter name."));
                } while (match(COMMA));
            }
            consume(RIGHT_PAREN, "Expected ')' after parameters.");
            consume(LEFT_BRACE, "Expected '{' before lambda body.");
            List<Stmt> body = block();
            return new Expr.Lambda(parameters, body);
        }

        throw error(peek(), "Expected expression.");
    }

    // Return true if any of the passed types are found. If they are, consume them.
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    // Check type of latest token. Do not consume.
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private boolean check2(TokenType type) {
        if (isAtEnd()) return false;
        return peek2().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token peek2() {
        if (isAtEnd()) return peek(); // Will return EOF if we can't peek 2 steps ahead
        else return tokens.get(current + 1);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        App.error(token, message);
        return new ParseError();
    }

    private void syncronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;
            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }
}
