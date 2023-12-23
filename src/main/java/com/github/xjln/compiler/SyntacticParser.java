package com.github.xjln.compiler;

import java.util.ArrayList;
import java.util.Set;

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
        token.assertNull();

        return ast.toArray(new AST[0]);
    }

    AST.Calc parseCalc(){
        AST.Calc calc = new AST.Calc();
        calc.value = parseValue();
        calc.type = calc.value.type; //TODO

        token.assertNull();

        while(token.hasNext()){
            if(token.next().t() != Token.Type.OPERATOR && !(token.current().t() == Token.Type.OPERATOR && token.current().equals("->"))){
                token.last();
                return calc;
            }

            String opp = token.current().s();
            calc.opp = opp;
            token.assertHasNext();


            //TODO brackets

            calc.setRight();
            calc.value = parseValue();
            calc.type = calc.value.type; //TODO

            if(calc.value.token.t() != calc.right.value.token.t())
                throw new RuntimeException("expected type " + calc.type);

            if(!Set.of("+", "-", "*", "/", "<", ">", "==", ">=", "<=", "&", "|", "^", "%").contains(opp))
                throw new RuntimeException("Operator is not defined");
        }

        return calc;
    }

    AST.Value parseValue() {
        AST.Value value = new AST.Value();

        switch (token.next().t()){
            case INTEGER, DOUBLE, FLOAT, LONG, SHORT -> {
                value.token = token.current();
                value.type = token.current().t().toString();
            }
            case IDENTIFIER -> {
                if(token.current().equals("true") || token.current().equals("false")){
                    value.token = token.current();
                    value.type = "boolean";
                }else throw new RuntimeException("illegal argument");
            }
            /*case OPERATOR -> {
                token.last();
                return null;
            }*/
            default -> throw new RuntimeException("illegal argument"); //TODO
        }

        return value;
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
