package com.github.xjln.compiler;

public sealed abstract class AST permits AST.Statement, AST.If{

    public static final class Statement extends AST{
        public final String code;
        public Statement(String code) {
            this.code = code;
        }
    }

    public static final class If extends AST{
        public final AST[] ast;
        public final String condition;
        public If(String condition, AST[] ast) {
            this.condition = condition;
            this.ast = ast;
        }
    }
}
