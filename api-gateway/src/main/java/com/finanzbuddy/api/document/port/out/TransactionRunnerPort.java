package com.finanzbuddy.api.document.port.out;

import java.util.function.Supplier;

public interface TransactionRunnerPort {

    <T> T inTransaction(Supplier<T> work);
}
