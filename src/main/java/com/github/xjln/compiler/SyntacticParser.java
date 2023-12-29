package com.github.xjln.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

final class SyntacticParser {

    static final Set<String> BOOL_OPERATORS = Set.of("==", "!=", "|", "&", ">", "<", ">=", "<=");
    static final Set<String> NUMBER_OPERATORS = Set.of("+", "-", "*", "/");

    private HashMap<String, String> vars;
    private TokenHandler th;
    private String[] code;
    private int line;

    AST.Calc parseCalc(String calc){
        th = Lexer.lex(calc);
        AST.Calc result = parseCalc(false);
        th.assertNull();
        return result;
    }

    AST[] parseAst(String allCode){ //TODO better exception messages
        if(allCode == null || allCode.trim().equals(""))
            return new AST[0];

        vars = new HashMap<>();
        code = allCode.split("\n");
        line = 0;
        ArrayList<AST> ast = new ArrayList<>();

        while(line < code.length){
            nextLine();
            th.assertToken(Token.Type.IDENTIFIER);
            if(th.current().equals("return")){
                AST.Return statement = new AST.Return();
                statement.calc = parseCalc(false);
                statement.type = statement.calc.type;
                ast.add(statement);
            }else{
                if(th.next().equals(Token.Type.IDENTIFIER)){
                    th.assertToken("=");
                    String name = th.last().s();
                    String type = th.last().s();

                    AST.Calc calc = parseCalc(true);

                    if(vars.containsKey(name))
                        throw new RuntimeException("Variable " + name + " already exists");

                    if(!calc.type.equals(type))
                        throw new RuntimeException("Expected type " + type + " got " + calc.type);

                    vars.put(name, type);
                    ast.add(calc);
                }else if(th.current().equals("=")){
                    th.toFirst();
                    ast.add(parseCalc(true));
                }else throw new RuntimeException(th.toString());
            }
        }

        return ast.toArray(new AST[0]);
    }

    AST.Calc parseCalc(boolean assignment){
        AST.Calc calc;
        if(th.next().equals("(")){
            calc = parseCalc(!assignment);
            th.assertToken(")");
        }else{
            th.last();
            calc = new AST.Calc();
            calc.value = parseValue(!assignment);
            calc.type = calc.value.type;
        }

        while(th.hasNext()){
            if(th.next().t() != Token.Type.OPERATOR && !(th.current().t() == Token.Type.OPERATOR && th.current().equals("->"))){
                th.last();
                return calc;
            }

            calc.setRight();

            calc.opp = th.current().s();
            th.assertHasNext();

            if(calc.opp.equals("=")){
                calc.left = parseCalc(false);

                String returnType = Compiler.getOperatorReturnType(calc.right.type, calc.left.type, calc.opp);

                if(returnType == null)
                    throw new RuntimeException("Operator " + calc.opp + " is not defined for " + calc.left.type + " and " + calc.value.type);

                calc.type = returnType;
            }else if(th.next().equals("(")){
                calc.left = parseCalc(false);
                th.assertToken(")");

                String returnType = Compiler.getOperatorReturnType(calc.right.type, calc.left.type, calc.opp);

                if(returnType == null)
                    throw new RuntimeException("Operator " + calc.opp + " is not defined for " + calc.left.type + " and " + calc.value.type);

                calc.type = returnType;
            }else{
                th.last();
                calc.value = parseValue(false);

                String returnType = Compiler.getOperatorReturnType(calc.right.type, calc.value.type, calc.opp);

                if(returnType == null)
                    throw new RuntimeException("Operator " + calc.opp + " is not defined for " + calc.right.type + " and " + calc.value.type);

                calc.type = returnType;
            }
        }

        return calc;
    }

    AST.Value parseValue(boolean checkVarExist) {
        AST.Value value = new AST.Value();

        switch (th.next().t()){
            case INTEGER, DOUBLE, FLOAT, LONG, SHORT, CHAR -> {
                value.token = th.current();
                value.type = th.current().t().toString();
            }
            case IDENTIFIER -> {
                if(th.current().equals("true") || th.current().equals("false")){
                    value.token = th.current();
                    value.type = "boolean";
                }else{
                    if(checkVarExist && !vars.containsKey(th.current().s()))
                        throw new RuntimeException("Variable " + th.current().s() + " does not exist");

                    AST.Call call = new AST.Call();
                    call.call = th.current().s();
                    call.type = vars.get(th.current().s());

                    value.call = call;
                    value.type = call.type;
                }
            }
            default -> throw new RuntimeException("illegal argument"); //TODO
        }

        return value;
    }

    private void nextLine(){
        if(line >= code.length)
            throw new RuntimeException("internal compiler error");

        th = Lexer.lex(code[line]);
        line++;
    }
}
