package com.github.xjln.compiler;

sealed abstract class AST permits AST.Calc, AST.CalcArg, AST.Call, AST.Value {

    String type = null;

    static final class Calc extends AST{
        Value value = null;
        CalcArg next = null;
    }

    static final class CalcArg extends AST{
        String opp1 = null, opp2 = null;
        Value value = null;
        CalcArg next = null;
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
