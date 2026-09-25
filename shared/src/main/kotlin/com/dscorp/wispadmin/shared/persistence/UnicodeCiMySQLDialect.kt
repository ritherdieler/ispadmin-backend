package com.dscorp.wispadmin.shared.persistence

import org.hibernate.dialect.MySQL57Dialect

class UnicodeCiMySQLDialect : MySQL57Dialect() {
    init {
        registerFunction("cast", UnicodeCiCastFunction())
    }
}
