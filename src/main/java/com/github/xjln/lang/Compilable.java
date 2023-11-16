package com.github.xjln.lang;

import com.github.xjln.bytecode.AccessFlag;

public abstract sealed class Compilable permits XJLNClass, XJLNDataClass, XJLNInterface, XJLNMethod, XJLNTypeClass {

    public final AccessFlag accessFlag;

    public Compilable(AccessFlag accessFlag){
        this.accessFlag = accessFlag;
    }

    public int getAccessFlag(){
        return switch (accessFlag){
            case ACC_PUBLIC -> AccessFlag.PUBLIC;
            case ACC_PRIVATE -> AccessFlag.PRIVATE;
            case ACC_PROTECTED -> AccessFlag.PROTECTED;
        };
    }
}
