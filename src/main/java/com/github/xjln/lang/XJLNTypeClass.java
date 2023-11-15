package com.github.xjln.lang;

import com.github.xjln.bytecode.AccessFlag;

public final class XJLNTypeClass extends Compilable{

    public final String[] values;

    public XJLNTypeClass(AccessFlag accessFlag, String[] values){
        super(accessFlag);
        this.values = values;
    }

    public boolean hasValue(String value){
        for(String s:values)
            if(s.equals(value))
                return true;

        return false;
    }

    @Override
    public int getAccessFlag() {
        return super.getAccessFlag() + AccessFlag.ENUM + AccessFlag.FINAL;
    }
}
