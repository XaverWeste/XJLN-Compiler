package com.github.xjln.compiler;

sealed abstract class AST permits AST.Calc, AST.Cast, AST.Call, AST.Value, AST.Return, AST.VarInit {

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
            temp.type = type;

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
            temp.type = type;

            right = temp;
            opp = null;
            value = null;
        }
    }

    static final class Cast extends AST{
        String to = null;
        Value value = null;
    }

    static final class Value extends AST{
        Call call = null;
        Cast cast = null;
        Token token = null;
    }

    static final class Call extends AST{
        String call = null;
        Call next = null;
        Calc[] argTypes;
    }

    static final class Return extends AST{
        Calc calc = null;
    }

    static final class VarInit extends AST{
        Calc calc = null;
        String name = null;
    }

}
