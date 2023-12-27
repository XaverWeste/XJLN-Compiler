package com.github.xjln.compiler;

import com.github.xjln.lang.XJLNMethod;
import com.github.xjln.utility.MatchedList;

final class OperandStack {

    private final MatchedList<String, Integer> stack;
    private int size;

    OperandStack(){
        stack = new MatchedList<>();
        size = 0;

        push("this", 1);
    }

    void push(String name, int length){
        stack.add(name, size);
        size += length;
    }

    void push(int length){
        push("", length);
    }

    String pop(int length){
        String temp = stack.getKey(stack.size());
        stack.remove(stack.size());
        size -= length;
        return temp;
    }

    int get(String name){
        if(!stack.hasKey(name))
            return -1;

        return stack.getValue(name);
    }

    static OperandStack forMethod(XJLNMethod method){
        OperandStack os = new OperandStack();

        for(int i = 0;i < method.parameters.size();i++)
            os.push(method.parameters.getKey(i), (method.parameters.getValue(i).equals("double") || method.parameters.getValue(i).equals("long")) ? 2 : 1);

        return os;
    }

    boolean contains(String name){
        return stack.hasKey(name);
    }
}
