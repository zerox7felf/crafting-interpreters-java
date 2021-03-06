package jlox.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class GenerateAst {
    public static void main(String args[]) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: generate_ast <output directiry>");
            System.exit(64);
        }
        String outputDir = args[0];
        defineAst(outputDir, "Expr", Arrays.asList(
            "Ternary    : Expr condition, Expr truePath, Expr falsePath",
            "Binary     : Expr left, Token operator, Expr right",
            "Logical    : Expr left, Token operator, Expr right",
            "Grouping   : Expr expression",
            "Literal    : Object value",
            "Unary      : Token operator, Expr right",
            "Call       : Expr callee, Token paren, List<Expr> arguments",
            "Get        : Expr object, Token name",
            "Set        : Expr object, Token name, Expr value",
            "This       : Token keyword",
            "Variable   : Token name",
            "Assign     : Token name, Expr value",
            "Lambda     : List<Token> params, List<Stmt> body",
            "Super      : Token keyword, Token method"
        ));
        defineAst(outputDir, "Stmt", Arrays.asList(
            "Expression : Expr expression",
            "Print      : Expr expression",
            "Return     : Token keyword, Expr value",
            "Var        : Token name, Expr initializer",
            "Block      : List<Stmt> statements",
            "If         : Expr condition, Stmt thenBranch, Stmt elseBranch",
            "While      : Expr condition, Stmt body",
            "Function   : Token name, List<Token> params, List<Stmt> body",
            "Class      : Token name, Expr.Variable superclass, List<Stmt.Function> methods"
        ));
    }

    private static void defineAst(String outputDir, String baseName, List<String> types) throws IOException {
        String path = outputDir + "/" + baseName + ".java";
        PrintWriter writer = new PrintWriter(path, "UTF-8");

        writer.println(
            "/* File generated by jlox.tool.GenerateAst */\n"+
            "package jlox;\n"+
            "\n"+
            "import java.util.List;\n"+
            "\n"+
            "abstract class "+baseName+" {"
        );

        defineVisitor(writer, baseName, types);

        for (String type : types) {
            String className = type.split(":")[0].trim();
            String fields = type.split(":")[1].trim();
            defineType(writer, baseName, className, fields);
        }

        writer.println();
        writer.println("    abstract <R> R accept(Visitor<R> visitor);");

        writer.println("}");
        writer.close();
    }

    private static void defineVisitor(PrintWriter writer, String baseName, List<String> types) {
        writer.println("    interface Visitor<R> {");

        for (String type : types) {
            String typeName = type.split(":")[0].trim();
            writer.println("        R visit" + typeName + baseName + "(" + typeName + " " + baseName.toLowerCase() + ");");
        }

        writer.println("    }\n");
    }

    private static void defineType(PrintWriter writer, String baseName, String className, String fieldList) {
        writer.println("    static class " + className + " extends " + baseName + " {");
        writer.println("        " + className + "(" + fieldList + ") {"); // Constructor

        String[] fields = fieldList.split(", ");
        for (String field : fields) {
            String name = field.split(" ")[1];
            writer.println("            this." + name + " = " + name + ";");
        }
        writer.println(
            "        }\n"+
            "        \n"+
            "        @Override\n"+
            "        <R> R accept(Visitor<R> visitor) {\n"+
            "            return visitor.visit" + className + baseName + "(this);\n"+
            "        }\n"
        );

        for (String field : fields) {
            writer.println("        final " + field + ";");
        }
        writer.println("    }");
    }
}
