package com.github.xjln.compiler;

sealed abstract class AST permits AST.Calc, AST.Call, AST.Value {

    String type = null;

    static final class Calc extends AST{
        String opp = null;
        Value value = null;
        Calc left = null, right = null;

        public void setLeft(){
            Calc temp = new Calc();
            temp.left = left;
            temp.right = right;
            temp.opp = opp;
            temp.value = value;

            left = temp;
            opp = null;
            value = null;
        }

        public void setRight(){
            Calc temp = new Calc();
            temp.right = right;
            temp.left = left;
            temp.opp = opp;
            temp.value = value;

            right = temp;
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
