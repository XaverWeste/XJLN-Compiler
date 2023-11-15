package com.github.xjln.lang;

import com.github.xjln.bytecode.AccessFlag;
import com.github.xjln.utility.MatchedList;

public final class XJLNInterface extends Compilable{

    public final MatchedList<String, XJLNInterfaceMethod> methods;

    public XJLNInterface(AccessFlag accessFlag, MatchedList<String, XJLNInterfaceMethod> methods) {
        super(accessFlag);
        this.methods = methods;
    }

    public int getAccessFlag() {
        return super.getAccessFlag() + AccessFlag.ABSTRACT + AccessFlag.INTERFACE;
    }
}
