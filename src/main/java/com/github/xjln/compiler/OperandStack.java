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

    int push(String name, int length){
        stack.add(name, size);
        size += length;
        return size - length;
    }

    void push(int length){
        push("&temp", length);
    }

    String pop(){
        String temp = stack.getKey(stack.size());
        int length = stack.getValue(stack.size() - 1);
        length -= stack.getValue(stack.size() - 2);
        stack.remove(stack.size() - 1);
        size -= length;
        return temp;
    }

    int clearTemp(){
        int i = 0;
        while (stack.getKey(stack.size()).equals("&temp")){
            pop();
            i++;
        }
        return i;
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
