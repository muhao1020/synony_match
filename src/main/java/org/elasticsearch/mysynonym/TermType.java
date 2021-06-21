package org.elasticsearch.mysynonym;

import org.apache.lucene.util.BytesRef;

/**
 * @Classname TermType
 * @Description TODO
 * @Date 2021/6/18 18:06
 * @Created by muhao
 */
public class TermType {
    String field;
    String type;
    BytesRef bytes;
    public TermType(String fld, String type, BytesRef bytes) {
        field = fld;
        this.type = type;
        this.bytes = bytes == null ? null : BytesRef.deepCopyOf(bytes);
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BytesRef getBytes() {
        return bytes;
    }

    public void setBytes(BytesRef bytes) {
        this.bytes = bytes;
    }
}
