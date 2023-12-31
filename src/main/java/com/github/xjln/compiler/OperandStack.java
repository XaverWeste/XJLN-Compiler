package com.github.xjln.compiler;

import com.github.xjln.lang.XJLNMethod;
import com.github.xjln.utility.MatchedList;
import javassist.bytecode.Bytecode;
import javassist.bytecode.Opcode;

import java.util.ArrayList;

final class OperandStack {

    private final MatchedList<String, Integer> stack;
    private final ArrayList<Integer> scopes = new ArrayList<>();
    private int size;

    OperandStack(){
        stack = new MatchedList<>();
        size = 0;

        push("this", 1);
    }

    int push(String name, int length){
        stack.add(name, size);
        System.out.println(name + " " + size);
        size += length;
        System.out.println(stack);
        return size - length;
    }

    void push(int length){
        push("&temp", length);
    }

    String pop(){
        String temp = stack.getKey(stack.size() - 1);
        int length = stack.getValue(stack.size() - 1);
        length -= stack.getValue(stack.size() - 2);
        stack.remove(stack.size() - 1);
        size -= length;
        System.out.println(stack);
        return temp;
    }

    void clearScope(Bytecode code){
        while (size > scopes.get(scopes.size() - 1)) {
            //code.addOpcode(Opcode.POP);
            pop();
        }
    }

    void newScope(){
        scopes.add(size);
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
