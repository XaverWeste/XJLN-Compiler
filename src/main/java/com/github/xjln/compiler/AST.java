package com.github.xjln.compiler;

public sealed abstract class AST permits AST.Statement, AST.If, AST.While, AST.Calculation{

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

    public static final class While extends AST{
        public final AST[] ast;
        public final String condition;
        public While(String condition, AST[] ast) {
            this.condition = condition;
            this.ast = ast;
        }
    }

    public static final class Calculation extends AST{
        private Calculation left = null, right = null;
        private Token content;

        public Token getContent(){
            return content;
        }

        public Calculation right(Calculation right){
            if(right != null) this.right = right;
            return this.right;
        }

        public Calculation left(Calculation left){
            if(left != null) this.left = left;
            return this.left;
        }

        public Token calculate(){
            return content;
        }
    }
}
