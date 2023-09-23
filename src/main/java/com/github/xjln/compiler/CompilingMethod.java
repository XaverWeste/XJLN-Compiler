package com.github.xjln.compiler;

import com.github.xjln.lang.XJLNMethod;

import java.util.HashMap;
import java.util.Stack;

public class CompilingMethod {

    public static class Scope{

        public static class Var{
            final String allowedTypes;

            public Var(String allowedTypes){
                this.allowedTypes = allowedTypes;
            }

            public boolean areTypesAllowed(String types){
                for(String type:types.split(","))
                    if(!isTypeAllowed(type))
                        return false;

                return true;
            }

            private boolean isTypeAllowed(String type){
                for(String allowedType:allowedTypes.split(","))
                    if(allowedType.equals(type))
                        return true;

                return false;
            }
        }

        final HashMap<String, Var> vars;

        public Scope(Scope scope){
            vars = new HashMap<>();
            vars.putAll(scope.vars);
        }

        public Scope(XJLNMethod method){
            vars = new HashMap<>();
            for(String name:method.parameterTypes.getKeyList())
                vars.put(name, new Var(method.parameterTypes.getValue(name).type()));
        }

        public boolean varExist(String name){
            return vars.containsKey(name);
        }

        public boolean areTypesAllowed(String varName, String types){
            return vars.get(varName).areTypesAllowed(types);
        }

        public void add(String name, Var var){
            vars.put(name, var);
        }

    }

    private final Stack<Scope> scopes;
    private final String[] code;
    private int currentLine;

    public CompilingMethod(XJLNMethod method){
        code = method.code;
        currentLine = -1;
        scopes = new Stack<>();
        scopes.push(new Scope(method));
    }

    public boolean hasNextLine(){
        return currentLine < code.length;
    }

    public String nextLine(){
        return code[currentLine++];
    }

    public String currentLine(){
        return code[currentLine == -1 ? 0 : currentLine];
    }

    public void newScope(){
        scopes.push(new Scope(scopes.peek()));
    }

    public void lastScope(){
        scopes.pop();
    }

    public Scope scope(){
        return scopes.peek();
    }
}
