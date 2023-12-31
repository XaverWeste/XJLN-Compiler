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

        if(result.opp != null && result.opp.equals("="))
            result.opp = "#";

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
            ast.add(parseNext());
        }

        return ast.toArray(new AST[0]);
    }

    private AST parseNext(){
        th.assertToken(Token.Type.IDENTIFIER);
        if(th.current().equals("return")){
            AST.Return statement = new AST.Return();
            statement.calc = parseCalc(false);
            statement.type = statement.calc.type;
            th.assertNull();

            if(statement.calc.opp != null && statement.calc.opp.equals("#"))
                statement.calc.opp = "=";

            return statement;
        }else if(th.current().equals("while")){
            AST.While ast = parseWhile();

            if(ast.condition.opp.equals("#"))
                ast.condition.opp = "=";

            return ast;
        }else if(th.current().equals("if")) {
            AST.If ast = parseIf();

            AST.If statement = ast;
            while(statement != null){
                if(statement.condition.opp.equals("#"))
                    statement.condition.opp = "=";
                statement = statement.elif;
            }

            return ast;
        }else{
            if (th.next().equals(Token.Type.IDENTIFIER)) {
                th.assertToken("=");
                String name = th.last().s();
                String type = th.last().s();

                AST.Calc calc = parseCalc(true);

                if (vars.containsKey(name))
                    throw new RuntimeException("Variable " + name + " already exists");

                if (!calc.type.equals(type))
                    throw new RuntimeException("Expected type " + type + " got " + calc.type);

                th.assertNull();

                vars.put(name, type);

                if(calc.opp != null && calc.opp.equals("="))
                    calc.opp = "#";

                return calc;
            } else if (th.current().equals("=")) {
                th.toFirst();
                AST.Calc calc = parseCalc(true);

                if(calc.opp != null && calc.opp.equals("="))
                    calc.opp = "#";

                return calc;
            } else throw new RuntimeException(th.toString());
        }
    }

    private AST.While parseWhile(){
        AST.While statement = new AST.While();
        statement.condition = parseCalc(false);

        if(!statement.condition.type.equals("boolean"))
            throw new RuntimeException("Expected boolean got " + statement.condition.type);

        ArrayList<AST> ast = new ArrayList<>();

        nextLine();
        while (!th.toStringNonMarked().equals("end ")){
            ast.add(parseNext());
            nextLine();
        }

        statement.ast = ast.toArray(new AST[0]);

        return statement;
    }

    private AST.If parseIf(){
        AST.If statement = new AST.If();

        statement.condition = parseCalc(false);

        if(!statement.condition.type.equals("boolean"))
            throw new RuntimeException("Expected boolean got " + statement.condition.type);

        ArrayList<AST> ast = new ArrayList<>();

        nextLine();
        while (!(th.toStringNonMarked().equals("end ") || th.toStringNonMarked().startsWith("else "))){
            ast.add(parseNext());
            nextLine();
        }

        statement.ast = ast.toArray(new AST[0]);

        if(th.toStringNonMarked().startsWith("else ")){
            th.assertToken("else");

            if(!th.hasNext()){
                ast = new ArrayList<>();
                nextLine();
                while (!(th.toStringNonMarked().equals("end "))){
                    ast.add(parseNext());
                    nextLine();
                }

                AST.If elseCase = new AST.If();
                elseCase.ast = ast.toArray(new AST[0]);

                statement.elif = elseCase;
            }else{
                th.assertToken("if");
                statement.elif = parseIf();
            }
        }

        return statement;
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

                if(assignment){
                    calc.opp = "#";
                    assignment = false;
                }
            }else if(th.next().equals("(")){
                calc.left = parseCalc(false);
                th.assertToken(")");

                String returnType = Compiler.getOperatorReturnType(calc.right.type, calc.left.type, calc.opp);

                if(returnType == null)
                    throw new RuntimeException("Operator " + calc.opp + " is not defined for " + calc.left.type + " and " + calc.value.type);

                calc.type = returnType;
            }else{
                th.last();
                calc.value = parseValue(true);

                String returnType = Compiler.getOperatorReturnType(calc.right.type, calc.value.type, calc.opp);

                if(returnType == null)
                    throw new RuntimeException("Operator " + calc.opp + " is not defined for " + calc.right.type + " and " + calc.value.type);

                calc.type = returnType;
            }
        }

        return calc;
    }

    private AST.Value parseValue(boolean checkVarExist) {
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
                    if(th.hasNext() && !th.next().equals(Token.Type.OPERATOR)){
                        th.last();
                        String type = th.current().s();

                        value = parseValue(true);

                        if(!Compiler.PRIMITIVES.contains(type) || type.equals("boolean"))
                            throw new RuntimeException("Unable to cast " + value.type + " to " + type);

                        value.cast = value.type;
                        value.type = type;
                    }else {
                        th.last();

                        if (checkVarExist && !vars.containsKey(th.current().s()))
                            throw new RuntimeException("Variable " + th.current().s() + " does not exist");

                        AST.Call call = new AST.Call();
                        call.call = th.current().s();
                        call.type = vars.get(th.current().s());

                        value.call = call;
                        value.type = call.type;
                    }
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
