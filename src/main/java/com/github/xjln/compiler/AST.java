package com.github.xjln.compiler;

sealed abstract class AST permits AST.Calc, AST.Call, AST.Value {

    String type = null;

    static final class Calc extends AST{
        String opp = null;
        Value value = null;
        Calc left = null, right = null;

        public void setLeft(){
            left = new Calc();
            left.opp = opp;
            left.value = value;

            opp = null;
            value = null;
        }

        public void setRight(){
            right = new Calc();
            right.opp = opp;
            right.value = value;

            opp = null;
            value = null;
        }
    }

    static final class Value extends AST{
        Call call = null;
        Token token = null;
    }

    static final class Call extends AST{
        String call = null;
        Call next = null;
        Calc[] argTypes;
    }

}
