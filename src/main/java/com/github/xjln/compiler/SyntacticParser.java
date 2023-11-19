package com.github.xjln.compiler;

import java.util.ArrayList;

final class SyntacticParser {

    TokenHandler token;
    ArrayList<AST> ast;
    String[] code;
    int line;

    AST[] parseAst(String allCode){
        if(allCode == null || allCode.trim().equals(""))
            return new AST[0];

        code = allCode.split("\n");
        line = 0;
        ast = new ArrayList<>();

        nextLine();

        ast.add(parseCalc());

        return ast.toArray(new AST[0]);
    }

    AST.Calc parseCalc(){
        token.toFirst();
        AST.Calc calc = new AST.Calc();

        calc.value.token = token.assertToken(Token.Type.INTEGER);
        token.assertNull();
        calc.type = "int";

        return calc;
    }

    /*
    AST.CalcArg parseCalcArg(){
        AST.CalcArg arg = new AST.CalcArg();

        switch (token.assertToken(Token.Type.NUMBER).t()){ //TODO
            case NUMBER, CHAR, STRING -> arg.token = token.current();
            case IDENTIFIER -> {
                //TODO
            }
            case OPERATOR -> {
                arg.opp = token.current().s();
                //TODO
            }
            case SIMPLE -> {
                if(token.current().equals("(")){
                    //TODO
                }else throw new RuntimeException("illegal argument");
            }
        }

        return arg;
    }

     */

    void nextLine(){
        if(line >= code.length)
            throw new RuntimeException("internal compiler error");

        token = Lexer.lex(code[line]);
        line++;
    }
}
