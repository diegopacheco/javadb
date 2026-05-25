package com.javadb.engine;

import com.javadb.storage.RID;

public record Located(RID rid, Row row) {
}
