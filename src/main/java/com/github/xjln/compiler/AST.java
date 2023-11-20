package com.github.xjln.compiler;

sealed abstract class AST permits AST.Calc, AST.Call, AST.Value {

    String type = null;

    static final class Calc extends AST{
        String opp = null;
        Value value = null;
        Calc left = null, right = null;
    }

    static final class Value extends AST{
        Call call = null;
        Token token = null;
    }

    static final class Call extends AST{
        String call = null;
        Call next = null;
        String[] argTypes;
    }

}
