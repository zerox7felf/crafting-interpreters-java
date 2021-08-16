package jlox;

import java.util.List;

class LoxFunction implements LoxCallable {
    // Book uses this, but we want to support lambdas aswell.
    // Since these can come from expressions aswell, we store the fields separately instead.
    //private final Stmt.Function declaration;
    private final String name;
    private final List<Token> params;
    private final List<Stmt> body;
    private final Environment closure;

    LoxFunction(Expr.Lambda declaration, Environment closure) {
        this.name = "lambda";
        this.params = declaration.params;
        this.body = declaration.body;
        this.closure = closure;
    }

    LoxFunction(Stmt.Function declaration, Environment closure) {
        this.name = declaration.name.lexeme;
        this.params = declaration.params;
        this.body = declaration.body;
        this.closure = closure;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment environment = new Environment(closure);
        for (int i = 0; i < params.size(); i++) {
            // Set each parameters value according to the arguments received
            environment.define(params.get(i).lexeme, arguments.get(i));
        }

        try {
            interpreter.executeBlock(body, environment);
        } catch (Return returnValue) {
            return returnValue.value;
        }
        return null;
    }

    @Override
    public int arity() {
        return params.size();
    }

    @Override
    public String toString() {
        return "<fn " + name + ">";
    }
}
