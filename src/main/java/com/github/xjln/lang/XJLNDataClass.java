package com.github.xjln.lang;

import com.github.xjln.bytecode.AccessFlag;
import com.github.xjln.utility.MatchedList;

public final class XJLNDataClass extends Compilable{

    public final MatchedList<String, XJLNField> fields;
    public final boolean finaly;

    public XJLNDataClass(AccessFlag accessFlag, MatchedList<String, XJLNField> fields, boolean finaly) {
        super(accessFlag);
        this.fields = fields;
        this.finaly = finaly;
    }

    @Override
    public int getAccessFlag() {
        return super.getAccessFlag() + (finaly ? AccessFlag.FINAL : 0);
    }
}
