package com.github.xjln.compiler;

import com.github.xjln.lang.XJLNMethod;

import java.util.HashMap;
import java.util.Stack;

public class CompilingMethod {

    public static class Scope{

        final HashMap<String, String> vars;

        public Scope(Scope scope){
            vars = new HashMap<>();
            vars.putAll(scope.vars);
        }

        public Scope(XJLNMethod method){
            vars = new HashMap<>();
            for(String name:method.parameterTypes.getKeyList())
                vars.put(name, method.parameterTypes.getValue(name).type());
        }

        public boolean varExist(String name){
            return vars.containsKey(name);
        }

        public String getType(String name){
            return vars.get(name);
        }

        public void add(String name, String var){
            vars.put(name, var);
        }
    }

    public final String[] genericTypes;
    private final Stack<Scope> scopes;
    private final String[] code;
    private int currentLine;

    public CompilingMethod(XJLNMethod method){
        code = method.code;
        currentLine = -1;
        scopes = new Stack<>();
        scopes.push(new Scope(method));
        genericTypes = method.genericTypes;
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
