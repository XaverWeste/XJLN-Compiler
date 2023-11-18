package com.github.xjln.lang;

import com.github.xjln.bytecode.AccessFlag;
import com.github.xjln.utility.MatchedList;

public final class XJLNMethod extends Compilable{

    public final MatchedList<String, String> parameters;
    public final String returnType;
    public final boolean statik, abstrakt, synchronise;

    public XJLNMethod(AccessFlag accessFlag, String returnType, MatchedList<String, String> parameters, boolean statik, boolean abstrakt, boolean synchronise) {
        super(accessFlag);
        this.returnType = returnType;
        this.parameters = parameters;
        this.statik = statik;
        this.abstrakt = abstrakt;
        this.synchronise = synchronise;
    }

    @Override
    public int getAccessFlag() {
        int accessFlag = super.getAccessFlag();

        if(statik)
            accessFlag += AccessFlag.STATIC;

        if(abstrakt)
            accessFlag += AccessFlag.ABSTRACT;

        if(synchronise)
            accessFlag += AccessFlag.SYNCHRONIZED;

        return accessFlag;
    }
}
