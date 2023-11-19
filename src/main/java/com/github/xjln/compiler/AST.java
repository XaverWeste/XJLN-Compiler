package com.github.xjln.compiler;

import java.util.ArrayList;

sealed abstract class AST permits AST.Calc, AST.CalcArg, AST.Call, AST.Set {

    final class Set extends AST{
        Call call = null;
        Calc calc = null;
    }

    final class Call extends AST{
        String name = null;
        Call next = null;
        ArrayList<String> args = null;
    }

    final class Calc extends AST{
        String opp = null;
        CalcArg argLeft, argRight = null;
        Call callLeft, callRight = null;
    }

    final class CalcArg extends AST{
        public Call call = null;
        public Token token = null;
        public String opp = null;
    }

}
